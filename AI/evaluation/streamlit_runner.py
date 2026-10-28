"""Streamlit-only harness; production method 6 plus existing comparison baselines."""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
import sys
from uuid import uuid4
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path
from time import perf_counter

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from src.api.schemas import ConfirmedGoal, ContentInput, Observation, VerifiedContext
from src.analysis.goal_analyzer import GoalGraphResolver
from src.analysis.goal_graph import ContentRole
from src.analysis.hybrid_scoring import decide_relevance
from src.analysis.passage_grouping import build_code_context_groups
from src.analysis.relevance_analyzer import DEFAULT_INSTRUCTION, RelevanceAnalyzer, concept_queries, next_content_source
from src.analysis.acquisition_policy import desktop_target_block_reason
from src.api.relevance_contract import contract_reason_code
from src.preprocessing.adapter import adapt_content, observation_is_current, preprocessing_context
from src.preprocessing.models import PassageKind, PreprocessingConfig
from src.preprocessing.text_cleaner import clean_text
from src.preprocessing.validator import contains_sensitive_data, validate_and_preprocess
from src.utils.cache import TTLCache
from AI.evaluation.similarity_methods import METHODS, Goal, PreparedCase, parse_goal, score_method
from AI.evaluation.graphless_hybrid import (GraphlessConfig, SCORING_VERSION, build_screen_contexts,
                                           score_passages, score_metadata, metadata_titles, metadata_is_informative,
                                           METADATA_CONFIG, METADATA_SCORING_VERSION, EVIDENCE_POLICY_VERSION,
                                           build_followup_contexts)

LABELS = ("RELATED", "UNRELATED", "UNCERTAIN", "EXCLUDED", "PRIVACY_BLOCKED")
DEFAULT_URL = "https://desktop-n7o3o02.tail1023df.ts.net"
DEFAULT_REVISION = "3c93b6415795f9cc3e16c403a492ce25b1c0e260f2f760acc90e2035de25f9bc"


def parse_inputs(goal_raw: str, content_raw: str, source: str, *, page_title: str = "",
                 template_id: str | None = None, ocr_confidence: float = .95,
                 capture_status: str = "SUCCESS"):
    """Fill only omitted local harness context; supplied contract fields stay intact."""
    if len(content_raw) > 2_000_000:
        raise ValueError("입력은 2MB 이하로 제한합니다.")
    value = json.loads(content_raw) if content_raw.lstrip().startswith(("{", "[")) else {"text": content_raw}
    items = value.get("cases", [value]) if isinstance(value, dict) else value
    if not isinstance(items, list) or not 1 <= len(items) <= 30:
        raise ValueError("단일 JSON 또는 cases 1~30개를 입력하세요.")
    raw_goal = json.loads(goal_raw) if goal_raw.lstrip().startswith("{") else {"text": goal_raw}
    if not isinstance(raw_goal, dict):
        raise ValueError("목표는 문장 또는 ConfirmedGoal JSON이어야 합니다.")
    raw_goal = raw_goal.get("goal", raw_goal)
    # When using plain goal text, inherit only its identity from the input fixture.
    first = items[0]
    if not isinstance(first, dict):
        raise ValueError("각 사례는 JSON 객체여야 합니다.")
    if "observations" in first:
        observations = first["observations"]
        if not isinstance(observations, list) or len(observations) != 1:
            raise ValueError("방식 비교에는 사례당 현재 화면 observation 1개를 넣으세요.")
        first = observations[0]
    elif "input" in first:
        first = first["input"]
    if not isinstance(first, dict):
        raise ValueError("입력은 JSON 객체여야 합니다.")
    goal_data = {"goalId": first.get("goalId", "lab-goal"),
                 "goalVersion": first.get("goalVersion", 1), "confirmed": True, **raw_goal}
    if template_id:
        goal_data["templateId"] = template_id
    goal = ConfirmedGoal.parse_safe(goal_data)
    # A labelled fixture's gold labels belong to its declared goal. Do not
    # silently score them against an unrelated goal left in the UI textbox.
    if isinstance(value, dict) and "cases" in value and "goal" in value:
        declared = value["goal"]
        if not isinstance(declared, dict) or not isinstance(declared.get("text"), str):
            raise ValueError("INVALID_DATASET_GOAL")
        def normalized_goal(text):
            return re.sub(r"\s+", "", unicodedata.normalize("NFKC", text)).casefold()
        if normalized_goal(declared["text"]) != normalized_goal(goal.text):
            raise ValueError("DATASET_GOAL_MISMATCH")
    if any(contains_sensitive_data(text) for text in [goal.text, *(
        [goal.profile.main_topic, *goal.profile.core_topics, *goal.profile.supporting_topics,
         *goal.profile.expected_activities] if goal.profile else [])]):
        raise ValueError("목표에 민감정보가 감지되어 분석하지 않았습니다.")
    if not goal.text.strip():
        raise ValueError("목표를 입력하세요.")
    cases = []
    batch_id = uuid4().hex
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            raise ValueError("각 사례는 JSON 객체여야 합니다.")
        expected = item.get("expectedLabel", item.get("expectedRelevanceLabel", item.get("expected")))
        if expected is not None and expected not in LABELS:
            raise ValueError("기대 라벨 값이 올바르지 않습니다.")
        if "observations" in item:
            observations = item["observations"]
            if not isinstance(observations, list) or len(observations) != 1:
                raise ValueError("방식 비교에는 사례당 현재 화면 observation 1개를 넣으세요.")
            payload = observations[0]
        else:
            payload = item.get("input", item.get("content", item))
        if not isinstance(payload, dict):
            raise ValueError("각 입력은 JSON 객체여야 합니다.")
        # Only documented evaluation metadata is removed; typo fields are rejected.
        meta = {"caseId", "expectedLabel", "expectedRelevanceLabel", "expected", "purpose"}
        supplied = {k: v for k, v in payload.items() if k not in meta}
        raw_text = supplied.get("text", "")
        if isinstance(supplied.get("contentPayload"), dict):
            raw_text = supplied["contentPayload"].get("text", "")
        if isinstance(raw_text, str) and len(raw_text) > 2_000:
            raise ValueError("CONTENT_TEXT_TOO_LONG")
        if any(key in supplied for key in ("metadata", "contentPayload", "goalSnapshot", "guardStatus")):
            from src.api.relevance_contract import ScreenAnalysisRequest
            public = ScreenAnalysisRequest.parse_safe(supplied)
            if public.goal_snapshot is not None and public.goal_snapshot != goal.text:
                raise ValueError("DATASET_GOAL_MISMATCH")
            supplied = {"sessionId": public.session_id, "clientRunId": public.client_run_id,
                        "observedAt": public.observed_at.isoformat(), "sequence": public.sequence,
                        "contentSource": public.content_source.value,
                        "text": public.content_payload.text if public.content_payload else "",
                        "isSensitive": public.guard_status == "PRIVACY_BLOCKED",
                        "isExcluded": public.guard_status == "EXCLUDED"}
            if public.metadata is not None:
                supplied.update(public.metadata.model_dump(by_alias=True))
            # The public contract does not yet carry OCR recognition quality.
            # Missing quality is deliberately not replaced with the manual UI default.
        content_data = {
            "sessionId": "lab-session", "runId": "lab-run", "goalId": goal.goal_id,
            "goalVersion": goal.goal_version, "clientRunId": f"lab-{batch_id}-{index}",
            "sequence": index, "navigationId": f"lab-nav-{index}",
            "observedAt": datetime.now(timezone.utc).isoformat(), "isActiveTarget": True,
            "sessionStatus": "ACTIVE", "exclusionMode": "NONE", "contentSource": source,
            "contentType": "DOCUMENT", "pageTitle": page_title, "text": "",
            "extractionStatus": "SUCCESS", "isSensitive": False, "isExcluded": False,
            **supplied,
        }
        if content_data["contentSource"] in {"CHROME_VIEWPORT_OCR", "DESKTOP_APP_OCR"} and "ocr" not in supplied:
            # A raw-text/manual entry uses the explicit UI assumption; JSON must carry OCR metadata.
            if not content_raw.lstrip().startswith(("{", "[")):
                content_data["ocr"] = {"confidence": ocr_confidence, "captureStatus": capture_status}
        content = ContentInput.parse_safe(content_data)
        observation = {field.alias: content_data[field.alias] for field in Observation.model_fields.values()}
        context = VerifiedContext.parse_safe({**observation, "userId": "lab-user", "resumeRequired": False,
            "policyVersion": "school-experimental-v2", "extractorVersion": "manual-lab-v1", "stateVersion": 1})
        cases.append((str(item.get("caseId", f"case-{index + 1}")), content, context, expected))
    return goal, cases


class RecordingClient:
    """Transient vector lookup by hash for diagnostics; no extra inference requests."""
    def __init__(self, client):
        self.client = client
        self.fingerprint, self.dimensions = client.fingerprint, client.dimensions
        self.settings = getattr(client, "settings", None)
        self.vectors = TTLCache(512, 300)
        self.embedding_ms = 0.0

    def embed(self, texts):
        started = perf_counter()
        try:
            vectors = self.client.embed(texts)
        finally:
            self.embedding_ms += (perf_counter() - started) * 1000
        for text, vector in zip(texts, vectors):
            self.vectors.put(hashlib.sha256(text.encode()).hexdigest(), tuple(vector))
        return vectors

    def lookup(self, texts):
        vectors = tuple(self.vectors.get(hashlib.sha256(text.encode()).hexdigest()) for text in texts)
        if any(vector is None for vector in vectors):
            raise LookupError("DIAGNOSTIC_VECTORS_EXPIRED")
        return vectors

    def encode(self, texts, **_):
        # Existing baselines supplied E5 prefixes; translate to the current Qwen format.
        return self.embed(tuple(
            f"Instruct: {DEFAULT_INSTRUCTION}\nQuery: {text[7:]}" if text.startswith("query: ")
            else text[9:] if text.startswith("passage: ") else text for text in texts))


def _trace(analyzer, recorded, graph, request, prepared):
    pairs = tuple((clean_text(a.text, a.kind, analyzer.preprocessing.ui_fragments).text,
                   clean_text(b.text, b.kind, analyzer.preprocessing.ui_fragments).text)
                  for a, b in zip(request.passages, request.passages[1:])
                  if a.kind in {PassageKind.BODY, PassageKind.OCR} and b.kind is PassageKind.CODE)
    groups = build_code_context_groups(prepared.passages, max_bytes=analyzer.code_context_max_bytes, source_pairs=pairs)
    decision = decide_relevance(prepared.passages, graph,
        recorded.lookup(tuple(p.text for p in prepared.passages)),
        recorded.lookup(concept_queries(graph, analyzer.instruction)), analyzer.hybrid,
        context_groups=groups, context_vectors=recorded.lookup(tuple(g.passage.text for g in groups)))
    return decision


class HybridSession:
    """Per-browser runtime; reuse HTTP connections and bounded analyzer caches."""
    def __init__(self, client, resolver):
        self.client = client
        self.recorded = RecordingClient(client)
        self.analyzer = RelevanceAnalyzer(self.recorded, goal_graph_resolver=resolver)

    def close(self):
        self.analyzer.clear_caches()
        self.recorded.vectors.clear()
        self.client.close()


class GraphlessSession:
    """Per-browser graph-free runtime with short-lived vector reuse only."""
    def __init__(self, client):
        self.client = client
        self.recorded = RecordingClient(client)

    def close(self):
        self.recorded.vectors.clear()
        self.client.close()


def compare_graphless(goal, cases, client, *, config: GraphlessConfig = GraphlessConfig(),
                      session: GraphlessSession | None = None):
    """Streamlit experiment only. Expectations never enter scoring or embedding."""
    started = perf_counter()
    calls_start = getattr(client, "calls", 0)
    recorded = session.recorded if session else RecordingClient(client)
    embedding_start = recorded.embedding_ms
    query_text = f"Instruct: {DEFAULT_INSTRUCTION}\nQuery: {goal.text}"
    # This experiment judges short text by evidence, not a character minimum.
    preprocessing = PreprocessingConfig(min_text_chars=0, penalize_removed_fragments=False,
                                        preserve_extracted_markup=True,
                                        max_passage_chars=2_000)
    rows = []
    for case_id, content, context, expected in cases:
        tick = perf_counter()
        embedding_before = recorded.embedding_ms
        calls_before = getattr(client, "calls", 0)
        score_result = {
            "relevanceLabel": "UNCERTAIN", "reasonCode": "INSUFFICIENT_EVIDENCE",
            "relatedCoverage": 0.0, "unrelatedCoverage": 0.0,
            "totalBodyChars": 0, "relatedBodyChars": 0, "unrelatedBodyChars": 0,
            "matchedKeywords": [], "matches": [], "evidenceIds": [], "keywordTerms": [],
            "generalTerms": [], "goalPhrases": [], "keywordWeights": {},
        }
        prepared = None
        is_metadata = content.content_source.value in {"TAB_META", "APP_META"}
        titles = ()
        status = "SKIPPED"
        reason = None
        if content.is_excluded or content.extraction_status.value == "EXCLUDED" or context.exclusion_mode.value != "NONE":
            reason = "EXCLUDED"
        elif content.is_sensitive or any(contains_sensitive_data(value) for value in (
                goal.text, content.app_name, content.window_title, content.page_title, content.url_host)):
            reason = "PRIVACY_BLOCKED"
        elif not context.is_active_target:
            reason = "INACTIVE_TARGET"
        elif not observation_is_current(content, context):
            reason = "STALE_EVENT"
        elif (goal.goal_id, goal.goal_version) != (context.goal_id, context.goal_version):
            reason = "GOAL_VERSION_MISMATCH"
        elif content.extraction_status.value in {"FAILED", "UNSUPPORTED"}:
            reason = "EXTRACTION_FAILED" if content.extraction_status.value == "FAILED" else "UNSUPPORTED"
            status = "FAILED"
        elif desktop_target_block_reason(content.content_source, content.app_name):
            reason = desktop_target_block_reason(content.content_source, content.app_name)
        elif content.content_source.value == "CHROME_VIEWPORT_OCR":
            reason = "CHROME_OCR_DEFERRED_P1"
        elif content.content_source.value == "DESKTOP_APP_OCR":
            if content.ocr is None:
                reason = "OCR_METADATA_REQUIRED"
            elif content.ocr.capture_status != "SUCCESS":
                reason = content.ocr.capture_status
        try:
            if reason is None and is_metadata:
                titles = metadata_titles(
                    clean_text(content.page_title, PassageKind.TITLE, preprocessing.ui_fragments, strip_html=False).text,
                    clean_text(content.window_title, PassageKind.TITLE, preprocessing.ui_fragments, strip_html=False).text)
                if sum(len(title.encode("utf-8")) for title in titles) > 8000:
                    reason = "INCOMPLETE_REPRESENTATION"
                elif not any(metadata_is_informative(title, app_name=content.app_name) for title in titles):
                    # Missing/generic metadata is a completed abstention, not model failure.
                    score_result = score_metadata(goal.text, (), (), ())
                    status = "COMPLETED"
                else:
                    inputs = tuple(dict.fromkeys((query_text, *titles)))
                    vector_by_text, missing = {}, []
                    for text in inputs:
                        cached = recorded.vectors.get(hashlib.sha256(text.encode()).hexdigest())
                        if cached is None:
                            missing.append(text)
                        else:
                            vector_by_text[text] = cached
                    if missing:
                        vectors = recorded.embed(tuple(missing))
                        if len(vectors) != len(missing):
                            raise ValueError("VECTOR_COUNT_INVALID")
                        vector_by_text.update(zip(missing, vectors))
                    score_result = score_metadata(goal.text, titles, vector_by_text[query_text],
                                                  tuple(vector_by_text[text] for text in titles),
                                                  allow_unrelated=content.extraction_status.value == "SUCCESS",
                                                  app_name=content.app_name)
                    status = "COMPLETED"
            elif reason is None:
                request = adapt_content(content, context)
                prepared = validate_and_preprocess(request, preprocessing_context(context), preprocessing)
                body = tuple(p for p in prepared.passages if p.kind not in {PassageKind.TITLE, PassageKind.HEADING})
                if not prepared.should_analyze:
                    reason = prepared.reasons[0].value
                    status = "FAILED" if prepared.status.value == "FAILED" else "SKIPPED"
                elif prepared.quality.truncated:
                    reason = "INCOMPLETE_REPRESENTATION"
                elif not body or not any(char.isalnum() for p in body for char in p.text):
                    reason = "INSUFFICIENT_BODY"
                else:
                    pairs = tuple((clean_text(a.text, a.kind, preprocessing.ui_fragments,
                                              strip_html=False).text,
                                   clean_text(b.text, b.kind, preprocessing.ui_fragments,
                                              strip_html=False).text)
                                  for a, b in zip(request.passages, request.passages[1:])
                                  if a.kind in {PassageKind.BODY, PassageKind.OCR} and b.kind is PassageKind.CODE)
                    groups = build_code_context_groups(prepared.passages, source_pairs=pairs)
                    intro_pairs = tuple((clean_text(a.text, a.kind, preprocessing.ui_fragments, strip_html=False).text,
                                         clean_text(b.text, b.kind, preprocessing.ui_fragments, strip_html=False).text)
                                        for a, b in zip(request.passages, request.passages[1:])
                                        if a.kind in {PassageKind.BODY, PassageKind.OCR} and b.kind == a.kind)
                    window_title = clean_text(content.window_title, PassageKind.TITLE,
                                              preprocessing.ui_fragments, strip_html=False).text
                    screen_contexts = build_screen_contexts(prepared.passages, window_title=window_title)
                    followups = build_followup_contexts(prepared.passages, source_pairs=intro_pairs)
                    texts = (tuple(p.text for p in prepared.passages) + tuple(g.passage.text for g in groups)
                             + tuple(g.passage.text for g in screen_contexts)
                             + tuple(g.passage.text for g in followups))
                    inputs = tuple(dict.fromkeys((query_text, *texts)))
                    vector_by_text, missing = {}, []
                    for text in inputs:
                        cached = recorded.vectors.get(hashlib.sha256(text.encode()).hexdigest())
                        if cached is None:
                            missing.append(text)
                        else:
                            vector_by_text[text] = cached
                    if missing:
                        vectors = recorded.embed(tuple(missing))
                        if len(vectors) != len(missing):
                            raise ValueError("VECTOR_COUNT_INVALID")
                        vector_by_text.update(zip(missing, vectors))
                    score_result = score_passages(
                        goal.text, prepared.passages, vector_by_text[query_text],
                        tuple(vector_by_text[p.text] for p in prepared.passages), config,
                        context_groups=groups,
                        context_vectors=tuple(vector_by_text[g.passage.text] for g in groups),
                        screen_contexts=screen_contexts,
                        screen_vectors=tuple(vector_by_text[g.passage.text] for g in screen_contexts),
                        intro_pairs=intro_pairs,
                        followup_contexts=followups,
                        followup_vectors=tuple(vector_by_text[g.passage.text] for g in followups),
                        allow_unrelated=content.extraction_status.value == "SUCCESS",
                    )
                    status = "COMPLETED"
            if reason:
                score_result["reasonCode"] = reason
                if reason in {"EXCLUDED", "PRIVACY_BLOCKED"}:
                    score_result["relevanceLabel"] = reason
                    recorded.vectors.clear()
        except Exception:
            # Do not expose provider errors, raw content or secrets through UI logs.
            status = "FAILED"
            score_result["reasonCode"] = "MODEL_OR_SCORING_FAILED"
            recorded.vectors.clear()
        evidence_ids = set(score_result["evidenceIds"])
        body_scores = [item["hybridScore"] for item in score_result["matches"]
                       if item["kind"] not in {"TITLE", "HEADING"}
                       and item["unitType"] not in {"SCREEN_CONTEXT", "FOLLOWUP_CONTEXT"}]
        context_scores = [item["hybridScore"] for item in score_result["matches"]
                          if item["unitType"] in {"SCREEN_CONTEXT", "FOLLOWUP_CONTEXT"}]
        metadata_scores = [item["hybridScore"] for item in score_result["matches"] if item["kind"] in {"TITLE", "HEADING"}]
        evidence_scores = [item["hybridScore"] for item in score_result["matches"] if item["relatedEvidence"]]
        fallback = next_content_source(content.content_source, score_result["relevanceLabel"],
                                       score_result["reasonCode"])
        elapsed_ms = (perf_counter() - tick) * 1000
        embedding_ms = recorded.embedding_ms - embedding_before
        rows.append({
            "caseId": case_id, "method": "graphless", "methodName": "키워드 + 벡터 Hybrid (Graph 없음)",
            "status": status, "expectedLabel": expected,
            **{k: score_result[k] for k in ("relevanceLabel", "reasonCode", "matchedKeywords", "keywordTerms",
                "generalTerms", "goalPhrases", "keywordWeights",
                "relatedCoverage", "unrelatedCoverage", "totalBodyChars", "relatedBodyChars", "unrelatedBodyChars", "matches")},
            "scoringVersion": METADATA_SCORING_VERSION if is_metadata else SCORING_VERSION,
            "evidencePolicyVersion": METADATA_SCORING_VERSION if is_metadata else EVIDENCE_POLICY_VERSION,
            "analysisPath": "METADATA" if is_metadata else "BODY",
            "coverageApplicable": not is_metadata,
            "score": max(metadata_scores if is_metadata else body_scores, default=None),
            "bodyScore": max(body_scores, default=None), "contextScore": max(context_scores, default=None),
            "metadataScore": max(metadata_scores, default=None), "decisionScore": max(evidence_scores, default=None),
            "allowUnrelated": content.extraction_status.value == "SUCCESS",
            "scoreType": "벡터 기본점수 + 키워드 보강점수",
            "evidence": [{"id": p.id, "kind": p.kind.value, "text": p.text}
                         for p in (prepared.passages if prepared else ()) if p.id in evidence_ids]
                         + ([{"id": item["passageId"], "kind": "TITLE", "text": item["text"]}
                             for item in score_result["matches"] if item["passageId"] in evidence_ids] if is_metadata else []),
            "nextContentSource": fallback.value if fallback else None,
            "publicReasonCode": contract_reason_code(score_result["relevanceLabel"], score_result["reasonCode"]),
            "cacheHit": status == "COMPLETED" and getattr(client, "calls", 0) == calls_before,
            "embeddingMs": round(embedding_ms, 2), "localMs": round(max(0, elapsed_ms - embedding_ms), 2),
            "openaiCalls": 0, "openaiMs": 0.0,
            "embeddingCalls": getattr(client, "calls", 0) - calls_before, "elapsedMs": round(elapsed_ms, 2),
            "matchesExpected": (status == "COMPLETED" and score_result["relevanceLabel"] == expected)
                               if expected else None,
        })
    labelled = [row for row in rows if row["expectedLabel"] in {"RELATED", "UNRELATED", "UNCERTAIN"}]
    summary = [{
        "방식": "키워드 + 벡터 Hybrid (Graph 없음)", "사례 수": len(rows),
        "실패·분석 제외": sum(row["status"] != "COMPLETED" for row in rows),
        "기대 일치": f"{sum(row['matchesExpected'] is True for row in labelled)}/{len(labelled)}" if labelled else "기대 라벨 없음",
        "기대 일치율 %": round(100 * sum(row["matchesExpected"] is True for row in labelled) / len(labelled), 1) if labelled else None,
        "평균 시간 ms": round(sum(row["elapsedMs"] for row in rows) / max(1, len(rows)), 2),
    }]
    total_ms = (perf_counter() - started) * 1000
    embedding_ms = recorded.embedding_ms - embedding_start
    return {"rows": rows, "summary": summary, "graph": None, "graphMs": 0.0,
            "scoringVersion": SCORING_VERSION,
            "evidencePolicyVersion": EVIDENCE_POLICY_VERSION,
            # This path has no OpenAI client; zero means not invoked, not a fast response.
            "openaiCalls": 0, "openaiMs": 0.0,
            "metadataScoringVersion": METADATA_SCORING_VERSION, "metadataConfig": {key: getattr(METADATA_CONFIG, key) for key in
                ("keyword_boost", "min_related_cosine", "related_passage_threshold", "unrelated_cosine_threshold")},
            "config": asdict(config), "totalMs": round(total_ms, 2), "embeddingMs": round(embedding_ms, 2),
            "localMs": round(max(0, total_ms - embedding_ms), 2),
            "embeddingCalls": getattr(client, "calls", 0) - calls_start}


def compare(goal, cases, selected, client, resolver: GoalGraphResolver, *, reranker=None,
            session: HybridSession | None = None):
    """One comparison run; returns session-local results, never saves screen text."""
    if not selected or any(method not in METHODS for method in selected):
        raise ValueError("비교할 방식을 선택하세요.")
    started = perf_counter()
    calls_start = getattr(client, "calls", 0)
    # Reuse exactly this graph in the production analyzer and comparison baselines.
    graph_start = perf_counter()
    graph = resolver.resolve(goal) if set(selected) - {"1", "3"} else None
    graph_ms = (perf_counter() - graph_start) * 1000
    recorded = session.recorded if session else RecordingClient(client)
    embedding_start = recorded.embedding_ms
    class FixedResolver:
        def resolve(self, _):
            return graph
    analyzer = session.analyzer if session else RelevanceAnalyzer(recorded, goal_graph_resolver=FixedResolver())
    baseline = parse_goal(goal.text)
    semantic_goal = baseline
    if graph:
        direct = tuple(dict.fromkeys(a for c in graph.concepts if c.role is ContentRole.DIRECT for a in c.aliases))
        support = tuple(dict.fromkeys(a for c in graph.concepts if c.role is not ContentRole.DIRECT for a in c.aliases))
        baseline = Goal(goal.text, baseline.base_terms, direct, support, True)
        # Method 4 embeds concepts, rather than making a separate query for every alias.
        semantic_goal = Goal(goal.text, baseline.base_terms,
            tuple(c.description for c in graph.concepts if c.role is ContentRole.DIRECT),
            tuple(c.description for c in graph.concepts if c.role is not ContentRole.DIRECT), True)
    rows = []
    for case_id, content, context, expected in cases:
        request = adapt_content(content, context)
        prepared = validate_and_preprocess(request, preprocessing_context(context), analyzer.preprocessing)
        clean = PreparedCase(case_id, tuple((p.id, p.kind.value, p.text) for p in prepared.passages),
                             prepared.status, prepared.should_analyze, ", ".join(r.value for r in prepared.reasons))
        for method in selected:
            tick = perf_counter()
            embedding_before = recorded.embedding_ms
            calls_before = getattr(client, "calls", 0)
            row = {"caseId": case_id, "method": method, "methodName": METHODS[method], "expectedLabel": expected,
                   "score": None, "scoreType": "—", "matches": [], "evidence": [], "nextContentSource": None}
            # Run the real analyzer's policy/quality gate before any baseline model work.
            # Failed, private, inactive or stale inputs must not bypass current protections.
            try:
                if method == "6":
                    result = analyzer.analyze(content, goal, context, current_context=lambda: context)
                    row.update(status=result.analysis_status, relevanceLabel=result.relevance_label,
                               reasonCode=result.reason_code, contentRoles=[r.value for r in result.content_roles],
                               nextContentSource=result.next_content_source, evidenceIds=list(result.evidence_ids),
                               cacheHit=result.cache_hit)
                    if result.relevance_label in {"EXCLUDED", "PRIVACY_BLOCKED"}:
                        recorded.vectors.clear()
                    if result.analysis_status == "SUCCEEDED" and content.content_source.value not in {"TAB_META", "APP_META"}:
                        try:
                            decision = _trace(analyzer, recorded, graph, request, prepared)
                        except LookupError:
                            decision = None  # Do not repeat inference merely to draw diagnostic scores.
                            row["detail"] = "판정 캐시를 재사용했습니다. 상세 벡터가 만료되어 개념별 점수는 표시하지 않습니다."
                    else:
                        decision = None
                    if decision is not None:
                        if (decision.label, decision.reason_code) != (result.relevance_label, result.reason_code):
                            raise ValueError("DIAGNOSTIC_MISMATCH")
                        row["matches"] = [asdict(match) for match in decision.matches]
                        row["score"] = max((m.hybrid_score for m in decision.matches), default=None)
                        row["scoreType"] = "최대 개념 결합점수 (최종 판정은 근거·커버리지 포함)"
                else:
                    # A no-vector client lets the same production code validate policy gates.
                    # An otherwise analyzable content reaches embeddings and stops there.
                    class GateClient:
                        fingerprint, dimensions = "gate-only", client.dimensions
                        def embed(self, _):
                            from src.models.embedding_client import EmbeddingError
                            raise EmbeddingError("EMBEDDING_UNAVAILABLE")
                    gate = RelevanceAnalyzer(GateClient(), goal_graph_resolver=FixedResolver() if graph else GoalGraphResolver())
                    guard = gate.analyze(content, goal, context, current_context=lambda: context)
                    if guard.reason_code != "EMBEDDING_UNAVAILABLE":
                        row.update(status="SKIPPED", relevanceLabel=guard.relevance_label,
                                   reasonCode=guard.reason_code, evidenceIds=list(guard.evidence_ids))
                        if content.content_source.value in {"TAB_META", "APP_META"}:
                            row.update(status="NOT_RUN", relevanceLabel="UNCERTAIN", evidenceIds=[])
                            row["detail"] = "이 비교 방식은 DOM/OCR 본문 입력을 사용합니다. 메타데이터는 6번에서 확인하세요."
                    else:
                        result = score_method(method, semantic_goal if method == "4" else baseline,
                                              clean, recorded, reranker)
                        row.update(status=result.status, relevanceLabel=result.relevance_label,
                                   reasonCode=result.reason_code, evidenceIds=list(result.evidence_ids),
                                   score=result.score, scoreType=result.score_name, detail=result.detail or result.reason)
                ids = row.get("evidenceIds", [])
                row["evidence"] = [{"id": p.id, "kind": p.kind.value, "text": p.text}
                                   for p in prepared.passages if p.id in ids]
                if row.get("reasonCode", "").startswith("METADATA_"):
                    row["evidence"] = [{"id": name, "text": content.model_dump(by_alias=True).get(name, "")} for name in ids]
            except Exception:
                # Never serialize provider error messages, input values, URLs or credentials.
                row.update(status="ERROR", relevanceLabel="UNCERTAIN", reasonCode="ANALYSIS_UNAVAILABLE",
                           detail="모델 연결 또는 계산 실패. 설정과 모델 서버를 확인하세요.")
            row["elapsedMs"] = round((perf_counter() - tick) * 1000, 2)
            row["embeddingMs"] = round(recorded.embedding_ms - embedding_before, 2)
            row["localMs"] = round(max(0, row["elapsedMs"] - row["embeddingMs"]), 2)
            row["embeddingCalls"] = getattr(client, "calls", 0) - calls_before
            valid = row["status"] not in {"ERROR", "FAILED", "NOT_RUN"}
            row["matchesExpected"] = valid and row["relevanceLabel"] == expected if expected else None
            rows.append(row)
    summary = []
    for method in selected:
        subset = [r for r in rows if r["method"] == method]
        labelled = [r for r in subset if r["expectedLabel"] is not None]
        summary.append({"방식": f"{method}. {METHODS[method]}", "사례 수": len(subset),
                        "실행 오류/미실행": sum(r["status"] in {"ERROR", "FAILED", "NOT_RUN"} for r in subset),
                        "기대 일치": f"{sum(r['matchesExpected'] is True for r in labelled)}/{len(labelled)}" if labelled else "기대 라벨 없음",
                        "기대 일치율 %": round(100 * sum(r["matchesExpected"] is True for r in labelled) / len(labelled), 1) if labelled else None,
                        "평균 시간 ms": round(sum(r["elapsedMs"] for r in subset) / len(subset), 2)})
    total_ms = (perf_counter() - started) * 1000
    embedding_ms = recorded.embedding_ms - embedding_start
    return {"rows": rows, "summary": summary, "graph": asdict(graph) if graph else None,
            "graphMs": round(graph_ms, 2), "totalMs": round(total_ms, 2),
            "embeddingMs": round(embedding_ms, 2), "localMs": round(max(0, total_ms - graph_ms - embedding_ms), 2),
            "embeddingCalls": getattr(client, "calls", 0) - calls_start}

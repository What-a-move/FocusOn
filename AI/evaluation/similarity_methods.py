"""Local, one-off comparison of the seven methods in the Notion similarity plan.

Scores are retrieval signals. This module never treats them as probabilities or
uses an uncalibrated low score to declare a user off task.
"""

from __future__ import annotations

import json
import math
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from time import perf_counter
from typing import Callable, Protocol

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from src.preprocessing.models import (  # noqa: E402
    ContentSource,
    CurrentAnalysisContext,
    ExtractionStatus,
    PreprocessingRequest,
    PreprocessingStatus,
)
from src.preprocessing.validator import (  # noqa: E402
    contains_sensitive_data,
    validate_and_preprocess,
)


METHODS = {
    "1": "단순 키워드",
    "2": "확장 키워드",
    "3": "단일 목표 임베딩",
    "4": "다중 개념 임베딩",
    "5": "목표별 Graph + 키워드",
    "6": "Graph + 키워드 + 임베딩",
    "7": "Hybrid + 재정렬",
}
VECTOR_METHODS = frozenset({"3", "4", "6", "7"})
_GOAL_STOPWORDS = frozenset({"학습", "공부", "하기", "배우기", "공부하기", "알아보기", "구현하기"})


class Embedder(Protocol):
    def encode(self, texts: list[str], **kwargs: object) -> object: ...


class Reranker(Protocol):
    def predict(self, pairs: list[tuple[str, str]], **kwargs: object) -> object: ...


@dataclass(frozen=True)
class Goal:
    text: str
    base_terms: tuple[str, ...]
    direct_terms: tuple[str, ...]
    support_terms: tuple[str, ...]
    structured: bool


@dataclass(frozen=True)
class Case:
    case_id: str
    page_title: str
    text: str
    source: ContentSource
    extraction_status: ExtractionStatus
    quality_score: float
    is_sensitive: bool = False
    is_excluded: bool = False
    expected_label: str | None = None  # Evaluation metadata; never passed to score_method.


@dataclass(frozen=True)
class PreparedCase:
    case_id: str
    passages: tuple[tuple[str, str, str], ...]  # (id, kind, cleaned text)
    status: PreprocessingStatus
    can_score: bool
    status_reason: str


@dataclass(frozen=True)
class MethodResult:
    method_id: str
    method_name: str
    status: str
    candidate: str
    relevance_label: str
    score_name: str
    score: float | int | None
    evidence_ids: tuple[str, ...]
    reason_code: str
    reason: str
    elapsed_ms: float
    detail: str = ""


def _unique_strings(value: object) -> tuple[str, ...]:
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise ValueError("목표의 개념 목록은 문자열 배열이어야 합니다.")
    return tuple(dict.fromkeys(item.strip() for item in value if item.strip()))


def _goal_terms(text: str) -> tuple[str, ...]:
    words = [
        word for word in re.findall(r"[\w+#.-]+", text, re.UNICODE)
        if len(word) >= 2 and word.casefold() not in _GOAL_STOPWORDS
    ]
    return tuple(dict.fromkeys((text, *words)))


def parse_goal(raw: str) -> Goal:
    raw = raw.strip()
    if not raw:
        raise ValueError("학습 목표를 입력하세요.")
    if raw.startswith("{"):
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError("목표 JSON 형식이 올바르지 않습니다.") from exc
        if not isinstance(data, dict):
            raise ValueError("목표 JSON은 객체여야 합니다.")
        data = data.get("goalProfile", data.get("goal", data))
        if not isinstance(data, dict):
            raise ValueError("goal 또는 goalProfile은 객체여야 합니다.")
        text = data.get("mainTopic", data.get("text", ""))
        if not isinstance(text, str) or not text.strip():
            raise ValueError("목표 JSON에 mainTopic 또는 text가 필요합니다.")
        direct = _unique_strings(data.get("coreTopics", data.get("directTerms", [])))
        support = _unique_strings(data.get("supportingTopics", data.get("supportTerms", [])))
        text = text.strip()
        base = _goal_terms(text)
        terms = tuple(dict.fromkeys((*direct, *base)))
        support = tuple(term for term in support if term not in terms)
        structured = bool(direct or support)
    else:
        text = raw
        base = _goal_terms(text)
        terms = base
        support = ()
        structured = False
    if any(contains_sensitive_data(value) for value in (text, *terms, *support)):
        raise ValueError("목표에 민감정보가 감지되어 분석하지 않았습니다.")
    return Goal(text=text, base_terms=base, direct_terms=terms,
                support_terms=support, structured=structured)


def parse_cases(raw: str, source: str) -> tuple[Case, ...]:
    raw = raw.strip()
    if not raw:
        raise ValueError("DOM/OCR 추출 텍스트를 입력하세요.")
    if raw.startswith(("{", "[")):
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError("콘텐츠 JSON 형식이 올바르지 않습니다.") from exc
        if isinstance(data, dict) and "cases" in data:
            items = data["cases"]
        elif isinstance(data, list):
            items = data
        else:
            items = [data]
        if not isinstance(items, list) or not items or len(items) > 20:
            raise ValueError("콘텐츠 JSON에는 1~20개 사례만 넣을 수 있습니다.")
    else:
        items = [{"text": raw, "contentSource": source}]

    cases: list[Case] = []
    for index, item in enumerate(items, 1):
        if not isinstance(item, dict):
            raise ValueError("각 콘텐츠 사례는 JSON 객체여야 합니다.")
        if isinstance(item.get("content"), dict):
            item = {**item, **item["content"]}
        title = item.get("pageTitle", item.get("title", ""))
        text = item.get("text", "")
        if "passages" in item:
            passages = item["passages"]
            if not isinstance(passages, list) or any(
                not isinstance(p, dict) or not isinstance(p.get("text"), str)
                for p in passages
            ):
                raise ValueError("passages는 text가 있는 객체 배열이어야 합니다.")
            text = "\n\n".join(p["text"] for p in passages)
        if not isinstance(title, str) or not isinstance(text, str):
            raise ValueError("pageTitle/title과 text는 문자열이어야 합니다.")
        raw_source = item.get("contentSource", source)
        try:
            content_source = ContentSource(raw_source)
            extraction_status = ExtractionStatus(item.get("extractionStatus", "SUCCESS"))
        except (ValueError, TypeError) as exc:
            raise ValueError("contentSource 또는 extractionStatus 값이 지원되지 않습니다.") from exc
        quality = item.get("qualityScore", 1.0)
        if isinstance(quality, bool) or not isinstance(quality, (int, float)) or not 0 <= quality <= 1:
            raise ValueError("qualityScore는 0~1의 숫자여야 합니다.")
        sensitive = item.get("isSensitive", False)
        excluded = item.get("isExcluded", False)
        if not isinstance(sensitive, bool) or not isinstance(excluded, bool):
            raise ValueError("isSensitive/isExcluded는 boolean이어야 합니다.")
        case_id = item.get("id", item.get("caseId", f"case-{index:02d}"))
        if not isinstance(case_id, str) or not case_id:
            case_id = f"case-{index:02d}"
        cases.append(Case(
            case_id=case_id[:80], page_title=title, text=text,
            source=content_source, extraction_status=extraction_status,
            quality_score=float(quality), is_sensitive=sensitive,
            is_excluded=excluded,
            expected_label=item.get("expectedRelevanceLabel"),
        ))
    return tuple(cases)


def prepare_case(case: Case) -> PreparedCase:
    request = PreprocessingRequest(
        session_id="lab-session", run_id="lab-run", goal_id="lab-goal",
        goal_version=1, event_id="lab-event", navigation_id="lab-navigation",
        content_source=case.source, extraction_status=case.extraction_status,
        quality_score=case.quality_score, text=case.text,
        page_title=case.page_title, is_sensitive=case.is_sensitive,
        is_excluded=case.is_excluded,
    )
    context = CurrentAnalysisContext(
        run_id="lab-run", goal_version=1, navigation_id="lab-navigation"
    )
    result = validate_and_preprocess(request, context)
    return PreparedCase(
        case_id=case.case_id,
        passages=tuple((p.id, p.kind.value, p.text) for p in result.passages)
        if result.should_analyze else (),
        status=result.status,
        can_score=result.should_analyze,
        status_reason=", ".join(reason.value for reason in result.reasons)
        if result.reasons else result.status.value,
    )


def _normalized(text: str) -> str:
    return unicodedata.normalize("NFKC", text).casefold()


def _has_term(text: str, term: str) -> bool:
    content = _normalized(text)
    needle = _normalized(term)
    if not needle:
        return False
    if needle.isascii() and needle[0].isalnum() and needle[-1].isalnum():
        return re.search(r"(?<![a-z0-9])" + re.escape(needle) + r"(?![a-z0-9])", content) is not None
    return needle in content


def _hits(terms: tuple[str, ...], case: PreparedCase) -> tuple[tuple[str, str], ...]:
    return tuple(
        (term, passage_id)
        for term in terms
        for passage_id, _, passage_text in case.passages
        if _has_term(passage_text, term)
    )


def _lexical_result(method_id: str, goal: Goal, case: PreparedCase) -> MethodResult:
    direct = _hits(goal.base_terms if method_id == "1" else goal.direct_terms, case)
    support = _hits(goal.support_terms, case) if method_id != "1" else ()
    direct_ids = tuple(dict.fromkeys(pid for _, pid in direct))
    support_ids = tuple(dict.fromkeys(pid for _, pid in support))
    detail = "DIRECT: " + (", ".join(dict.fromkeys(term for term, _ in direct)) or "없음")
    if method_id == "2":
        detail += " | SUPPORTING: " + (", ".join(dict.fromkeys(term for term, _ in support)) or "없음")
    # A single mention is not enough. Title + body or two independent body
    # passages are the deliberately conservative, uncalibrated lab rule.
    if len(direct_ids) >= 2:
        return MethodResult(method_id, METHODS[method_id], "COMPLETED", "RELATED", "RELATED",
                            "distinct keyword hits", len({term for term, _ in direct}),
                            direct_ids, "GOAL_RELATED", "직접 개념이 서로 다른 두 근거에서 확인됐습니다.", 0.0,
                            detail)
    if method_id != "1" and len(support_ids) >= 2:
        return MethodResult(method_id, METHODS[method_id], "COMPLETED", "SUPPORTING", "RELATED",
                            "distinct keyword hits", len({term for term, _ in support}),
                            support_ids, "GOAL_SUPPORTING", "보조 개념이 서로 다른 두 근거에서 확인됐습니다.", 0.0,
                            detail)
    hits = direct + support
    return MethodResult(method_id, METHODS[method_id], "COMPLETED", "UNKNOWN", "UNCERTAIN",
                        "distinct keyword hits", len({term for term, _ in hits}),
                        tuple(dict.fromkeys(pid for _, pid in hits)), "AMBIGUOUS_CONTEXT",
                        "키워드 근거가 부족합니다. 미적중은 무관 판정 근거가 아닙니다.", 0.0,
                        detail)


def _graph_result(goal: Goal, case: PreparedCase) -> MethodResult:
    # The current GoalProfile supplies only DIRECT and SUPPORTING relations.
    # PREREQUISITE cannot be inferred safely from a topic name.
    paths = tuple(("DIRECT", term) for term in goal.direct_terms) + tuple(
        ("SUPPORTING", term) for term in goal.support_terms
    )
    matches = tuple(
        (relation, term, passage_id)
        for relation, term in paths
        for passage_id, _, passage_text in case.passages
        if _has_term(passage_text, term)
    )
    direct_ids = tuple(dict.fromkeys(pid for relation, _, pid in matches if relation == "DIRECT"))
    support_ids = tuple(dict.fromkeys(pid for relation, _, pid in matches if relation == "SUPPORTING"))
    graph_paths = " | ".join(
        f"{goal.text} → {relation} → {term} ({', '.join(dict.fromkeys(pid for role, node, pid in matches if role == relation and node == term))})"
        for relation, term in dict.fromkeys((relation, term) for relation, term, _ in matches)
    ) or "매칭된 Graph 경로 없음"
    if len(direct_ids) >= 2:
        candidate, label, evidence, code = "RELATED", "RELATED", direct_ids, "GOAL_RELATED"
    elif len(support_ids) >= 2:
        candidate, label, evidence, code = "SUPPORTING", "RELATED", support_ids, "GOAL_SUPPORTING"
    else:
        candidate, label = "UNKNOWN", "UNCERTAIN"
        evidence = tuple(dict.fromkeys(pid for _, _, pid in matches))
        code = "AMBIGUOUS_CONTEXT"
    reason = (
        "GoalProfile의 관계 경로와 두 근거 문단이 일치합니다."
        if label == "RELATED" else
        "Graph 경로 근거가 부족합니다. 미적중만으로 무관 판정하지 않습니다."
    )
    return MethodResult("5", METHODS["5"], "COMPLETED", candidate, label,
                        "matched graph nodes", len({(relation, term) for relation, term, _ in matches}),
                        evidence, code, reason, 0.0, graph_paths)


def _vector_scores(
    queries: tuple[str, ...], case: PreparedCase, embedder: Embedder
) -> dict[str, tuple[float, ...]]:
    passages = [text for _, _, text in case.passages]
    inputs = [*("query: " + query for query in queries),
              *("passage: " + passage for passage in passages)]
    vectors = embedder.encode(inputs, normalize_embeddings=True,
                              show_progress_bar=False)
    if len(vectors) != len(inputs):
        raise RuntimeError("임베딩 벡터 수가 입력 수와 다릅니다.")
    widths = {len(vector) for vector in vectors}
    if len(widths) != 1 or 0 in widths:
        raise RuntimeError("임베딩 벡터 차원이 유효하지 않습니다.")
    norms = [math.sqrt(sum(float(value) ** 2 for value in vector)) for vector in vectors]
    if any(norm == 0 or not math.isfinite(norm) for norm in norms):
        raise RuntimeError("임베딩 벡터가 유효하지 않습니다.")
    return {
        query: tuple(
            sum(float(a) * float(b) for a, b in zip(vectors[index], vectors[passage_index]))
            / (norms[index] * norms[passage_index])
            for passage_index in range(len(queries), len(inputs))
        )
        for index, query in enumerate(queries)
    }


def _semantic_result(method_id: str, goal: Goal, case: PreparedCase,
                     embedder: Embedder, reranker: Reranker | None) -> MethodResult:
    if method_id == "3":
        queries = (goal.text,)
    else:
        queries = tuple(dict.fromkeys((*goal.direct_terms, *goal.support_terms)))
    scores = _vector_scores(queries, case, embedder)
    best = max(((score, query, case.passages[index][0])
                for query, values in scores.items()
                for index, score in enumerate(values)), default=None)
    if best is None:
        raise RuntimeError("점수를 계산할 문단이 없습니다.")
    best_score, best_query, evidence_id = best
    if method_id == "3":
        return MethodResult(method_id, METHODS[method_id], "COMPLETED", "UNKNOWN", "UNCERTAIN",
                            "max cosine", best_score, (evidence_id,), "AMBIGUOUS_CONTEXT",
                            "실제 임베딩 점수입니다. 단일 점수 임계값이 검증되지 않아 판정을 보류합니다.", 0.0)

    role = "SUPPORTING" if best_query in goal.support_terms else "RELATED"
    reason = "가장 가까운 목표 개념: " + best_query + ". 임계값 검증 전 후보입니다."
    if method_id == "4":
        detail = " | ".join(
            f"{'SUPPORTING' if query in goal.support_terms else 'DIRECT'}: {query}={max(values):.4f}"
            f" ({case.passages[values.index(max(values))][0]})"
            for query, values in scores.items()
        )
        return MethodResult(method_id, METHODS[method_id], "COMPLETED", role, "UNCERTAIN",
                            "max concept cosine", best_score, (evidence_id,), "AMBIGUOUS_CONTEXT",
                            reason, 0.0, detail)

    direct_hits = _hits(goal.direct_terms, case)
    support_hits = _hits(goal.support_terms, case)
    graph_hits = direct_hits or support_hits
    graph_role = "RELATED" if direct_hits else "SUPPORTING" if support_hits else "UNKNOWN"
    candidate = graph_role if graph_role != "UNKNOWN" else "UNKNOWN"
    evidence = tuple(dict.fromkeys([evidence_id, *(pid for _, pid in graph_hits)]))
    if graph_role == "UNKNOWN":
        reason = "벡터만으로 가까운 Graph 밖 콘텐츠일 수 있어 판정을 보류합니다."
    else:
        reason = "Graph 키워드와 임베딩 후보가 확인됐으나 결합 임계값은 미검증입니다."

    if method_id == "6":
        return MethodResult(method_id, METHODS[method_id], "COMPLETED", candidate, "UNCERTAIN",
                            "max graph concept cosine", best_score, evidence,
                            "AMBIGUOUS_CONTEXT", reason, 0.0)

    assert reranker is not None
    pairs = [(goal.text, text) for _, _, text in case.passages]
    rank_scores = reranker.predict(pairs, show_progress_bar=False)
    ranked = [(float(value), case.passages[index][0])
              for index, value in enumerate(rank_scores)]
    if len(ranked) != len(case.passages) or any(not math.isfinite(score) for score, _ in ranked):
        raise RuntimeError("재정렬 점수가 유효하지 않습니다.")
    top_score, top_id = max(ranked)
    return MethodResult(method_id, METHODS[method_id], "COMPLETED", candidate, "UNCERTAIN",
                        "max reranker score", top_score, (top_id,), "AMBIGUOUS_CONTEXT",
                        "Hybrid 후보를 재정렬했습니다. 점수 임계값이 검증되지 않아 판정을 보류합니다.", 0.0)


def score_method(method_id: str, goal: Goal, case: PreparedCase,
                 embedder: Embedder | None = None,
                 reranker: Reranker | None = None,
                 synchronize: Callable[[], None] | None = None) -> MethodResult:
    if method_id not in METHODS:
        raise ValueError("지원하지 않는 유사도 방식입니다.")
    if not case.can_score:
        return MethodResult(method_id, METHODS[method_id], "SKIPPED", "UNKNOWN", "UNCERTAIN",
                            "none", None, (), "ANALYSIS_UNAVAILABLE",
                            "입력 검증·추출 상태로 분석을 중단했습니다: " + case.status_reason, 0.0)
    if method_id in VECTOR_METHODS and embedder is None:
        return MethodResult(method_id, METHODS[method_id], "NOT_RUN", "UNKNOWN", "UNCERTAIN",
                            "none", None, (), "ANALYSIS_UNAVAILABLE",
                            "실제 임베딩 모델이 없어 이 방식은 실행하지 않았습니다.", 0.0)
    if method_id == "7" and reranker is None:
        return MethodResult(method_id, METHODS[method_id], "NOT_RUN", "UNKNOWN", "UNCERTAIN",
                            "none", None, (), "ANALYSIS_UNAVAILABLE",
                            "실제 재정렬 모델이 없어 7번을 실행하지 않았습니다.", 0.0)
    if synchronize:
        synchronize()
    start = perf_counter()
    if method_id in {"1", "2"}:
        result = _lexical_result(method_id, goal, case)
    elif method_id == "5":
        result = _graph_result(goal, case)
    else:
        assert embedder is not None
        result = _semantic_result(method_id, goal, case, embedder, reranker)
    if synchronize:
        synchronize()
    elapsed = (perf_counter() - start) * 1000
    return MethodResult(
        method_id=result.method_id, method_name=result.method_name,
        status=result.status, candidate=result.candidate,
        relevance_label=result.relevance_label, score_name=result.score_name,
        score=result.score, evidence_ids=result.evidence_ids,
        reason_code=result.reason_code, reason=result.reason,
        elapsed_ms=elapsed, detail=result.detail,
    )

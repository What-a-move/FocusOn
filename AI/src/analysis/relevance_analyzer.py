"""Internal #11 pipeline. Server authentication/public serialization live elsewhere."""
from __future__ import annotations

import hashlib
import json
import math
from dataclasses import asdict, dataclass, replace
from threading import RLock
from typing import Callable

from ..api.schemas import ConfirmedGoal, ContentInput, ExclusionMode, VerifiedContext
from ..models.embedding_client import EmbeddingClient, EmbeddingError
from ..preprocessing.adapter import (PREPROCESSING_VERSION, adapt_content,
                                     observation_is_current, preprocessing_context)
from ..preprocessing.models import ContentSource, PassageKind, PreprocessingConfig, PreprocessingStatus, RejectionReason
from ..preprocessing.validator import contains_sensitive_data, validate_and_preprocess
from ..preprocessing.text_cleaner import clean_text
from ..utils.cache import TTLCache
from .goal_analyzer import GoalGraphResolver
from .goal_graph import Concept, ContentRole, GoalGraph, keyword_match
from .hybrid_scoring import Decision, HybridConfig, VectorError, decide_relevance, normalize_vector
from .passage_grouping import build_code_context_groups
from .acquisition_policy import (METADATA_SOURCES as _METADATA_SOURCES,
                                 desktop_target_block_reason, next_content_source)

ANALYZER_VERSION = "school-hybrid-v7-mvp"
DEFAULT_INSTRUCTION = "Retrieve passages useful for the given learning goal and concept."


def concept_queries(graph: GoalGraph, instruction: str) -> tuple[str, ...]:
    return tuple(f"Instruct: {instruction}\nQuery: {graph.goal_text}\nConcept: {concept.description}"
                 if concept.role is not None else f"Instruct: {instruction}\nQuery: {concept.description}"
                 for concept in graph.concepts + graph.unrelated_topics)


def _digest(value: object) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()


@dataclass(frozen=True, slots=True)
class RelevanceResult:
    # Internal result only; no invented public confidence or response fields.
    session_id: str
    run_id: str
    goal_id: str
    goal_version: int
    client_run_id: str
    sequence: int
    content_source: str
    navigation_id: str
    observed_at: str
    extraction_status: str
    analysis_status: str
    relevance_label: str
    reason_code: str
    evidence_ids: tuple[str, ...] = ()
    content_roles: tuple[ContentRole, ...] = ()
    quality_score: float | None = None
    content_hash: str | None = None
    cache_hit: bool = False
    analyzer_version: str = ANALYZER_VERSION
    model_version: str = ""
    policy_version: str = ""
    next_content_source: str | None = None


def decide_metadata(content: ContentInput, graph: GoalGraph) -> Decision:
    """Return only high-precision exact metadata matches; all ambiguity falls back."""
    fields = (
        ("appName", content.app_name),
        ("windowTitle", content.window_title),
        ("pageTitle", content.page_title),
        ("urlHost", content.url_host),
    )
    accepted: list[tuple[Concept, tuple[str, ...]]] = []
    for concept in graph.concepts + graph.unrelated_topics:
        aliases = {alias.casefold() for alias in concept.aliases
                   if any(keyword_match(value, alias) for _, value in fields if value)}
        if len(aliases) < 2:
            continue
        evidence = tuple(name for name, value in fields
                         if value and any(keyword_match(value, alias) for alias in concept.aliases))
        accepted.append((concept, evidence))

    related = tuple(item for item in accepted if item[0].role is not None)
    unrelated = tuple(item for item in accepted if item[0].role is None)
    if related and unrelated:
        return Decision("UNCERTAIN", "CONFLICTING_METADATA")
    if related:
        evidence = tuple(dict.fromkeys(name for _, names in related for name in names))
        roles = tuple(role for role in ContentRole if any(concept.role is role for concept, _ in related))
        return Decision("RELATED", "METADATA_GOAL_CONNECTED", evidence, roles)
    if unrelated:
        return Decision("UNCERTAIN", "METADATA_UNRELATED_REQUIRES_BODY")
    return Decision("UNCERTAIN", "METADATA_INSUFFICIENT")


class RelevanceAnalyzer:
    def __init__(self, client: EmbeddingClient, *, hybrid: HybridConfig | None = None,
                 preprocessing: PreprocessingConfig | None = None, instruction: str = DEFAULT_INSTRUCTION,
                 cache_capacity: int = 128, cache_ttl_seconds: float = 300,
                 queue_timeout_seconds: float = 30,
                 goal_graph_resolver: GoalGraphResolver | None = None):
        if not instruction or len(instruction) > 1_000 or contains_sensitive_data(instruction):
            raise ValueError("INVALID_INSTRUCTION")
        self.client, self.hybrid = client, hybrid or HybridConfig()
        self.code_context_max_bytes = min(8_000, getattr(getattr(client, "settings", None), "max_input_bytes", 8_000))
        self.preprocessing = preprocessing or PreprocessingConfig()
        self.instruction = instruction
        if goal_graph_resolver is None:
            from .goal_graph_runtime import build_goal_graph_resolver

            goal_graph_resolver = build_goal_graph_resolver()
        self.goal_graph_resolver = goal_graph_resolver
        if not math.isfinite(queue_timeout_seconds) or queue_timeout_seconds <= 0:
            raise ValueError("INVALID_QUEUE_TIMEOUT")
        self.queue_timeout_seconds = queue_timeout_seconds
        self._vectors: TTLCache[tuple[tuple[float, ...], ...]] = TTLCache(cache_capacity, cache_ttl_seconds)
        self._results: TTLCache[Decision] = TTLCache(cache_capacity, cache_ttl_seconds)
        self._events: TTLCache[tuple[str, RelevanceResult]] = TTLCache(cache_capacity, cache_ttl_seconds)
        self._lock = RLock()  # Single-flight duplicate events in this process.

    def clear_caches(self) -> None:
        with self._lock:
            self._vectors.clear()
            self._results.clear()
            self._events.clear()

    def clear_goal_graphs(self) -> None:
        """Call when a confirmed goal is deleted or its stored profile is invalidated."""
        self.goal_graph_resolver.clear()
        self.clear_caches()

    def analyze(self, content: ContentInput, goal: ConfirmedGoal, context: VerifiedContext,
                *, current_context: Callable[[], VerifiedContext]) -> RelevanceResult:
        # The callback is mandatory; never pretend the request snapshot is live state.
        if not self._lock.acquire(timeout=self.queue_timeout_seconds):
            return RelevanceResult(
                content.session_id, content.run_id, content.goal_id,
                content.goal_version, content.client_run_id, content.sequence,
                content.content_source.value, content.navigation_id,
                content.observed_at.isoformat(), content.extraction_status.value,
                "FAILED", "UNCERTAIN", "ANALYSIS_QUEUE_TIMEOUT",
            )
        try:
            return self._analyze(content, goal, context, current_context)
        finally:
            self._lock.release()

    def analyze_staged(
        self, metadata: ContentInput, goal: ConfirmedGoal, context: VerifiedContext,
        *, current_context: Callable[[], VerifiedContext],
        acquire_next: Callable[[ContentSource], tuple[ContentInput, VerifiedContext] | None],
    ) -> RelevanceResult:
        """Analyze the selected target, acquiring later stages only on demand.

        The caller owns foreground selection and local DOM/OCR extraction. Each
        acquired observation must have its own event ID and verified context.
        """
        if metadata.content_source not in _METADATA_SOURCES:
            raise ValueError("METADATA_STAGE_REQUIRED")

        result = self.analyze(metadata, goal, context, current_context=current_context)
        stage_content = metadata
        seen_event_ids = {metadata.client_run_id}
        while result.next_content_source is not None:
            expected = ContentSource(result.next_content_source)
            try:
                live = current_context()
            except Exception:
                live = None
            if not isinstance(live, VerifiedContext) or not (
                live.user_id == context.user_id
                and live.state_version == context.state_version
                and live.policy_version == context.policy_version
                and live.extractor_version == context.extractor_version
                and live.is_active_target
                and live.exclusion_mode is ExclusionMode.NONE
                and observation_is_current(stage_content, live)
            ):
                return replace(result, analysis_status="DISCARDED", relevance_label="UNCERTAIN",
                               reason_code="STALE_EVENT", evidence_ids=(), content_roles=(),
                               quality_score=None, content_hash=None, next_content_source=None)
            try:
                acquired = acquire_next(expected)
            except Exception:
                acquired = None
            if acquired is None:
                return result  # Keep UNCERTAIN and the requested stage for retry.
            content, next_context = acquired
            if not isinstance(content, ContentInput) or not isinstance(next_context, VerifiedContext) or not (
                content.content_source is expected
                and content.session_id == metadata.session_id
                and content.run_id == metadata.run_id
                and content.goal_id == metadata.goal_id
                and content.goal_version == metadata.goal_version
                and content.navigation_id == metadata.navigation_id
                and content.client_run_id not in seen_event_ids
                and content.observed_at >= stage_content.observed_at
                and next_context.user_id == context.user_id
                and next_context.state_version == context.state_version
                and next_context.policy_version == context.policy_version
                and next_context.extractor_version == context.extractor_version
            ):
                return replace(result, analysis_status="DISCARDED", relevance_label="UNCERTAIN",
                               reason_code="STAGE_MISMATCH", evidence_ids=(), content_roles=(),
                               quality_score=None, content_hash=None, next_content_source=None)
            seen_event_ids.add(content.client_run_id)
            result = self.analyze(content, goal, next_context, current_context=current_context)
            stage_content = content
        return result

    def _analyze(self, content: ContentInput, goal: ConfirmedGoal, context: VerifiedContext,
                 current_context: Callable[[], VerifiedContext]) -> RelevanceResult:
        def result(status: str, label: str, code: str, **values) -> RelevanceResult:
            fallback = next_content_source(content.content_source, label, code)
            return RelevanceResult(
                content.session_id, content.run_id, content.goal_id,
                content.goal_version, content.client_run_id, content.sequence,
                content.content_source.value, content.navigation_id,
                content.observed_at.isoformat(), content.extraction_status.value,
                status, label, code, model_version=self.client.fingerprint,
                policy_version=context.policy_version,
                next_content_source=fallback.value if fallback else None,
                **values,
            )

        def gate(live: VerifiedContext) -> RelevanceResult | None:
            if not isinstance(live, VerifiedContext):
                return result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
            if live.user_id != context.user_id:
                return result("DISCARDED", "UNCERTAIN", "OWNER_CHANGED")
            if content.is_excluded or content.extraction_status.value == "EXCLUDED" or live.exclusion_mode is not ExclusionMode.NONE:
                return result("SKIPPED", "EXCLUDED", "EXCLUDED")
            if content.is_sensitive:
                return result("SKIPPED", "PRIVACY_BLOCKED", "PRIVACY_BLOCKED")
            if not live.is_active_target:
                return result("SKIPPED", "UNCERTAIN", "INACTIVE_TARGET")
            if not observation_is_current(content, live) or live.state_version != context.state_version or live.policy_version != context.policy_version or live.extractor_version != context.extractor_version:
                return result("DISCARDED", "UNCERTAIN", "STALE_EVENT")
            return None

        def reject(rejected: RelevanceResult) -> RelevanceResult:
            if rejected.relevance_label in {"EXCLUDED", "PRIVACY_BLOCKED"}:
                self.clear_caches()
            return rejected

        try:
            live = current_context()
        except Exception:
            return result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
        rejected = gate(context) or gate(live)
        if rejected:
            return reject(rejected)
        if any(contains_sensitive_data(value) for value in (
            goal.text, content.app_name, content.window_title,
            content.page_title, content.url_host,
        )):
            self.clear_caches()
            return result("SKIPPED", "PRIVACY_BLOCKED", "PRIVACY_BLOCKED")
        if content.content_source is ContentSource.CHROME_VIEWPORT_OCR:
            return result("SKIPPED", "UNCERTAIN", "CHROME_OCR_DEFERRED_P1")
        target_block = desktop_target_block_reason(content.content_source, content.app_name)
        if target_block:
            return result("SKIPPED", "UNCERTAIN", target_block)
        if (goal.goal_id, goal.goal_version) != (context.goal_id, context.goal_version):
            return result("DISCARDED", "UNCERTAIN", "GOAL_VERSION_MISMATCH")
        if content.extraction_status.value == "FAILED":
            return result("FAILED", "UNCERTAIN", "EXTRACTION_FAILED")
        if content.extraction_status.value == "UNSUPPORTED":
            return result("FAILED", "UNCERTAIN", "UNSUPPORTED")
        if content.content_source in {ContentSource.CHROME_VIEWPORT_OCR, ContentSource.DESKTOP_APP_OCR}:
            if content.ocr is None:
                return result("SKIPPED", "UNCERTAIN", "OCR_METADATA_REQUIRED")
            if content.ocr.capture_status != "SUCCESS":
                return result("SKIPPED", "UNCERTAIN", content.ocr.capture_status)
        graph = self.goal_graph_resolver.resolve(goal)
        if graph is None:
            return result("SKIPPED", "UNCERTAIN", "GOAL_GRAPH_UNAVAILABLE")

        scope = [context.user_id, context.session_id, context.run_id, context.goal_id,
                 context.goal_version, graph.fingerprint, self.client.fingerprint,
                 context.policy_version, context.extractor_version, PREPROCESSING_VERSION, ANALYZER_VERSION,
                 asdict(self.preprocessing), asdict(self.hybrid), self.instruction, self.code_context_max_bytes]
        event_key = _digest([context.user_id, context.session_id, content.client_run_id])
        event_signature = _digest([content.model_dump(mode="json", by_alias=True), scope, context.state_version])
        event = self._events.get(event_key)
        if event:
            if event[0] != event_signature:
                return result("DISCARDED", "UNCERTAIN", "IDEMPOTENCY_CONFLICT")
            try:
                rejected = gate(current_context())
            except Exception:
                rejected = result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
            if rejected:
                return reject(rejected)
            return replace(event[1], cache_hit=True)

        if content.content_source in _METADATA_SOURCES:
            decision = decide_metadata(content, graph)
            decision_result = result(
                "SUCCEEDED", decision.label, decision.reason_code,
                evidence_ids=decision.evidence_ids,
                content_roles=decision.roles,
            )
            try:
                rejected = gate(current_context())
            except Exception:
                rejected = result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
            if rejected:
                return reject(rejected)
            self._events.put(event_key, (event_signature, decision_result))
            return decision_result

        request = adapt_content(content, context)
        prepared = validate_and_preprocess(request, preprocessing_context(context), self.preprocessing)
        if not prepared.should_analyze:
            reason = prepared.reasons[0]
            label = {RejectionReason.EXCLUDED: "EXCLUDED", RejectionReason.PRIVACY_BLOCKED: "PRIVACY_BLOCKED"}.get(reason, "UNCERTAIN")
            if label in {"EXCLUDED", "PRIVACY_BLOCKED"}:
                self.clear_caches()
            return result("FAILED" if prepared.status is PreprocessingStatus.FAILED else "SKIPPED", label, reason.value)
        if not any(p.kind not in {PassageKind.TITLE, PassageKind.HEADING} for p in prepared.passages):
            return result("SKIPPED", "UNCERTAIN", "INSUFFICIENT_BODY")
        if prepared.quality.truncated:
            # Without upstream representative selection, clipped text cannot
            # establish what the major body of an entire page is about.
            return result("SKIPPED", "UNCERTAIN", "INCOMPLETE_REPRESENTATION")
        # Keep the source context separate even when two apps/domains show the
        # same text. Goal vectors remain reusable across these observations.
        cache_key = _digest(scope + [prepared.content_hash, content.app_name, content.window_title, content.url_host])
        decision = self._results.get(cache_key)
        cache_hit = decision is not None
        pending_vectors = None
        try:
            if decision is None:
                vector_key = _digest(scope)
                vectors = self._vectors.get(vector_key)
                queries = concept_queries(graph, self.instruction)
                if vectors is None:
                    vectors = self._validated_vectors(self.client.embed(queries), len(queries))
                    pending_vectors = (vector_key, vectors)
                # State can change while goal vectors are being computed.
                rejected = gate(current_context())
                if rejected:
                    return reject(rejected)
                source_pairs = tuple(
                    (clean_text(body.text, body.kind, self.preprocessing.ui_fragments).text,
                     clean_text(code.text, code.kind, self.preprocessing.ui_fragments).text)
                    for body, code in zip(request.passages, request.passages[1:])
                    if body.kind in {PassageKind.BODY, PassageKind.OCR} and code.kind is PassageKind.CODE
                )
                context_groups = build_code_context_groups(
                    prepared.passages, max_bytes=self.code_context_max_bytes, source_pairs=source_pairs,
                )
                texts = tuple(p.text for p in prepared.passages) + tuple(g.passage.text for g in context_groups)
                embedded = self._validated_vectors(self.client.embed(texts), len(texts))
                decision = decide_relevance(
                    prepared.passages, graph, embedded[:len(prepared.passages)], vectors, self.hybrid,
                    context_groups=context_groups, context_vectors=embedded[len(prepared.passages):],
                )
        except (EmbeddingError, VectorError) as error:
            failure_code = str(error)
            # Do not copy arbitrary injected client exceptions into output.
            safe_codes = {"VECTOR_DIMENSION_INVALID", "VECTOR_NONFINITE", "VECTOR_ZERO", "VECTOR_COUNT_INVALID", "EMBEDDING_TIMEOUT", "EMBEDDING_UNAVAILABLE", "EMBEDDING_HTTP_ERROR", "EMBEDDING_RESPONSE_INVALID", "EMBEDDING_RESPONSE_TOO_LARGE", "EMBEDDING_INPUT_LIMIT", "EMBEDDING_QUEUE_TIMEOUT", "MODEL_REVISION_MISMATCH", "MODEL_PRECISION_MISMATCH", "MODEL_NOT_EMBEDDING", "MODEL_DIMENSION_MISMATCH", "MODEL_RESPONSE_MISMATCH", "MODEL_METADATA_INVALID", "SERVICE_VERSION_INVALID"}
            failure_code = failure_code if failure_code in safe_codes else "EMBEDDING_FAILED"
            decision = None
        except Exception:
            failure_code, decision = "ANALYSIS_FAILED", None
        if decision is None:
            try:
                rejected = gate(current_context())
            except Exception:
                rejected = result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
            self.clear_caches()
            return rejected or result("FAILED", "UNCERTAIN", failure_code)
        decision_result = result("SUCCEEDED", decision.label, decision.reason_code,
                                 evidence_ids=decision.evidence_ids, content_roles=decision.roles,
                                 quality_score=prepared.quality.final_quality_score,
                                 content_hash=prepared.content_hash, cache_hit=cache_hit)
        try:
            rejected = gate(current_context())
        except Exception:
            rejected = result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
        if rejected:
            return reject(rejected)
        if pending_vectors:
            self._vectors.put(*pending_vectors)
        # Matches contain only IDs/scores; no text is retained.
        self._results.put(cache_key, decision)
        self._events.put(event_key, (event_signature, decision_result))
        return decision_result

    def _validated_vectors(self, vectors, count: int) -> tuple[tuple[float, ...], ...]:
        if not isinstance(vectors, (tuple, list)) or len(vectors) != count:
            raise VectorError("VECTOR_COUNT_INVALID")
        return tuple(normalize_vector(vector, self.client.dimensions) for vector in vectors)

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
from ..utils.cache import TTLCache
from .goal_graph import ContentRole, GoalGraph, build_goal_graph
from .hybrid_scoring import Decision, HybridConfig, VectorError, decide_relevance, normalize_vector

ANALYZER_VERSION = "school-hybrid-v2"
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
    event_id: str
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


class RelevanceAnalyzer:
    def __init__(self, client: EmbeddingClient, *, hybrid: HybridConfig | None = None,
                 preprocessing: PreprocessingConfig | None = None, instruction: str = DEFAULT_INSTRUCTION,
                 cache_capacity: int = 128, cache_ttl_seconds: float = 300,
                 queue_timeout_seconds: float = 30):
        if not instruction or len(instruction) > 1_000 or contains_sensitive_data(instruction):
            raise ValueError("INVALID_INSTRUCTION")
        self.client, self.hybrid = client, hybrid or HybridConfig()
        self.preprocessing = preprocessing or PreprocessingConfig()
        self.instruction = instruction
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

    def analyze(self, content: ContentInput, goal: ConfirmedGoal, context: VerifiedContext,
                *, current_context: Callable[[], VerifiedContext]) -> RelevanceResult:
        # The callback is mandatory; never pretend the request snapshot is live state.
        if not self._lock.acquire(timeout=self.queue_timeout_seconds):
            return RelevanceResult(content.session_id, content.run_id, content.goal_id,
                                   content.goal_version, content.event_id, content.navigation_id,
                                   content.observed_at.isoformat(), content.extraction_status.value,
                                   "FAILED", "UNCERTAIN", "ANALYSIS_QUEUE_TIMEOUT")
        try:
            return self._analyze(content, goal, context, current_context)
        finally:
            self._lock.release()

    def _analyze(self, content: ContentInput, goal: ConfirmedGoal, context: VerifiedContext,
                 current_context: Callable[[], VerifiedContext]) -> RelevanceResult:
        def result(status: str, label: str, code: str, **values) -> RelevanceResult:
            return RelevanceResult(content.session_id, content.run_id, content.goal_id,
                                   content.goal_version, content.event_id, content.navigation_id,
                                   content.observed_at.isoformat(), content.extraction_status.value,
                                   status, label, code, model_version=self.client.fingerprint,
                                   policy_version=context.policy_version, **values)

        def gate(live: VerifiedContext) -> RelevanceResult | None:
            if not isinstance(live, VerifiedContext):
                return result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
            if live.user_id != context.user_id:
                return result("DISCARDED", "UNCERTAIN", "OWNER_CHANGED")
            if content.is_excluded or content.extraction_status.value == "EXCLUDED" or live.exclusion_mode is not ExclusionMode.NONE:
                return result("SKIPPED", "EXCLUDED", "EXCLUDED")
            if content.is_sensitive:
                return result("SKIPPED", "PRIVACY_BLOCKED", "PRIVACY_BLOCKED")
            if not observation_is_current(content, live) or live.state_version != context.state_version or live.policy_version != context.policy_version or live.extractor_version != context.extractor_version:
                return result("DISCARDED", "UNCERTAIN", "STALE_EVENT")
            return None

        try:
            live = current_context()
        except Exception:
            return result("DISCARDED", "UNCERTAIN", "CONTEXT_UNAVAILABLE")
        rejected = gate(context) or gate(live)
        if rejected:
            self.clear_caches()
            return rejected
        if contains_sensitive_data(goal.text) or contains_sensitive_data(content.app_name) or contains_sensitive_data(content.url_host):
            self.clear_caches()
            return result("SKIPPED", "PRIVACY_BLOCKED", "PRIVACY_BLOCKED")
        if (goal.goal_id, goal.goal_version) != (context.goal_id, context.goal_version):
            return result("DISCARDED", "UNCERTAIN", "GOAL_VERSION_MISMATCH")
        if content.content_source in {ContentSource.CHROME_VIEWPORT_OCR, ContentSource.DESKTOP_APP_OCR}:
            if content.ocr is None:
                return result("SKIPPED", "UNCERTAIN", "OCR_METADATA_REQUIRED")
            if content.ocr.capture_status != "SUCCESS":
                return result("SKIPPED", "UNCERTAIN", content.ocr.capture_status)
        prepared = validate_and_preprocess(adapt_content(content, context), preprocessing_context(context), self.preprocessing)
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
        graph = build_goal_graph(goal.text, goal.template_id)
        if graph is None:
            return result("SKIPPED", "UNCERTAIN", "GOAL_GRAPH_UNAVAILABLE")
        scope = [context.user_id, context.session_id, context.run_id, context.goal_id,
                 context.goal_version, graph.fingerprint, self.client.fingerprint,
                 context.policy_version, context.extractor_version, PREPROCESSING_VERSION, ANALYZER_VERSION,
                 asdict(self.preprocessing), asdict(self.hybrid), self.instruction]
        # Keep the source context separate even when two apps/domains show the
        # same text. Goal vectors remain reusable across these observations.
        cache_key = _digest(scope + [prepared.content_hash, content.app_name, content.url_host])
        event_key = _digest([context.user_id, context.session_id, context.run_id, content.event_id])
        # Include all observation metadata. A changed duplicate must never be replayed.
        event_signature = _digest([content.model_dump(mode="json", by_alias=True), scope, context.state_version])
        event = self._events.get(event_key)
        if event:
            if event[0] != event_signature:
                return result("DISCARDED", "UNCERTAIN", "IDEMPOTENCY_CONFLICT")
            decision_result = replace(event[1], cache_hit=True)
        else:
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
                        self.clear_caches()
                        return rejected
                    passage_vectors = self._validated_vectors(self.client.embed(tuple(p.text for p in prepared.passages)), len(prepared.passages))
                    decision = decide_relevance(prepared.passages, graph, passage_vectors, vectors, self.hybrid)
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
            self.clear_caches()
            return rejected
        if not event:
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

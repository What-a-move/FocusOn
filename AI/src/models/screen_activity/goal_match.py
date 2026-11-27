"""Attribute an already-classified study screen to confirmed session goals via E5 || JEV."""
from __future__ import annotations

import asyncio
from dataclasses import dataclass
import math
from typing import Any, Literal, Sequence

from starlette.concurrency import run_in_threadpool

from .activity import parts
from .beta_decision import beta_decision
from .jev_goal_matcher import GoalMatchJudge, JevGoalMatchResult
from ...analysis.acquisition_policy import METADATA_SOURCES
from ...preprocessing.models import ContentSource, ExtractionStatus, PassageKind, PreprocessingConfig
from ...preprocessing.text_cleaner import clean_text, split_text

MIN_BODY_CHARS = 80  # below this a body cannot support a task (file name, menu, one line)
CHUNK_CHARS = 550
CHUNK_OVERLAP = 100
RIVAL_MARGIN = 0.01
PREPROCESSING = PreprocessingConfig()
MatchStatus = Literal["MATCHED", "FAR", "NOT_ENOUGH"]


@dataclass(frozen=True, slots=True)
class MatchConfig:
    """Weighted combination + JEV veto thresholds (tunable without model changes)."""

    w_jev: float = 0.65
    jev_veto_threshold: float = 0.35
    jev_match_min: float = 0.55
    e5_min_floor: float = 0.65
    combined_match_floor: float = 0.72
    jev_far_min: float = 0.65
    rival_margin: float = RIVAL_MARGIN


DEFAULT_MATCH_CONFIG = MatchConfig()


def goal_terms(goal) -> tuple[str, ...]:
    profile = goal.profile
    terms = (goal.text,)
    if profile is not None:
        terms += (profile.main_topic, *profile.core_topics, *profile.supporting_topics, *profile.expected_activities)
    return tuple(dict.fromkeys(term for item in terms if (term := item.strip())))


def _match_terms(goal) -> tuple[str, ...]:
    profile = goal.profile
    terms = (goal.text,) if profile is None else (goal.text, profile.main_topic, *profile.core_topics)
    return tuple(dict.fromkeys(term for item in terms if (term := item.strip())))


def _clean_body_payload(payload: dict[str, Any], content) -> dict[str, Any]:
    body = clean_text(content.text, PassageKind.BODY, PREPROCESSING.ui_fragments, strip_html=False).text
    return {**payload, "contentPayload": {"text": body}}


def review_concept(classifier, content, payload, goal) -> str | None:
    """Return a concept label only when a legacy topic bank is explicitly attached."""
    topic_bank = getattr(classifier, "topic_bank", None)
    if topic_bank is None or not hasattr(topic_bank, "observed_concept"):
        return None
    if (content.content_source not in {ContentSource.CHROME_DOM, ContentSource.DESKTOP_APP_OCR}
            or content.extraction_status is not ExtractionStatus.SUCCESS):
        return None
    title, text = parts(_clean_body_payload(payload, content))
    body = text.removeprefix(title).strip()
    if len(body) < MIN_BODY_CHARS:
        return None
    return topic_bank.observed_concept(body, _match_terms(goal))


@dataclass(frozen=True, slots=True)
class PreparedMatchInput:
    metadata_only: bool
    title: str
    body: str
    text: str
    payload: dict[str, Any]
    terms_by_goal: dict[str, tuple[str, ...]]


def prepare_match_input(content, payload: dict[str, Any], goals: Sequence[Any]) -> PreparedMatchInput | None:
    """Validate preconditions and normalize title/body before invoking E5 or JEV."""
    if content.content_source is ContentSource.APP_META:
        return None
    metadata_only = content.content_source in METADATA_SOURCES
    body = ""
    if not metadata_only:
        if content.content_source is ContentSource.DESKTOP_APP_OCR and content.ocr is not None:
            if content.ocr.capture_status != "SUCCESS" or content.ocr.confidence < PREPROCESSING.min_quality_score:
                return None
        payload = _clean_body_payload(payload, content)
    title, text = parts(payload)
    if not metadata_only:
        body = text.removeprefix(title).strip()
        if len(body) < MIN_BODY_CHARS:
            return None
    if not text:
        return None

    terms_by_goal = {goal.goal_id: _match_terms(goal) for goal in goals}
    if not terms_by_goal or any(not terms for terms in terms_by_goal.values()):
        return None
    return PreparedMatchInput(
        metadata_only=metadata_only,
        title=title,
        body=body,
        text=text,
        payload=payload,
        terms_by_goal=terms_by_goal,
    )


def compute_e5_signals(
    classifier,
    prepared: PreparedMatchInput,
    extraction_status: ExtractionStatus,
) -> tuple[dict[str, float], bool]:
    """Compute per-goal max E5 cosine across views and body-only STUDY signal."""
    encoder = classifier.encoder
    all_terms = tuple(dict.fromkeys(term for terms in prepared.terms_by_goal.values() for term in terms))
    queries = encoder.embed(all_terms, role="query")
    body_chunks = () if prepared.metadata_only else split_text(prepared.body, CHUNK_CHARS, CHUNK_OVERLAP, False)
    view_texts = [prepared.title] if prepared.metadata_only else [prepared.text, *body_chunks]
    views = encoder.embed([item for item in view_texts if item])

    e5_scores: dict[str, float] = {}
    for goal_id, terms in prepared.terms_by_goal.items():
        best_cosine = -math.inf
        for term in terms:
            q_vec = queries[term]
            for view_vec in views.values():
                score = float(q_vec @ view_vec)
                if math.isfinite(score) and score > best_cosine:
                    best_cosine = score
        e5_scores[goal_id] = best_cosine if math.isfinite(best_cosine) else -math.inf

    is_study = False
    if not prepared.metadata_only and extraction_status is ExtractionStatus.SUCCESS:
        body_payload = {
            "contentSource": prepared.payload["contentSource"],
            "contentPayload": {"text": prepared.body},
        }
        prediction = classifier.predict([body_payload])[0]
        is_study = beta_decision(body_payload, prediction, "SUCCESS")["label"] == "STUDY"

    return e5_scores, is_study


def decide_goal_match(
    e5_scores: dict[str, float],
    jev_result: JevGoalMatchResult | None,
    goals: Sequence[Any],
    *,
    metadata_only: bool,
    extraction_status: ExtractionStatus,
    is_study: bool,
    config: MatchConfig = DEFAULT_MATCH_CONFIG,
) -> tuple[str | None, MatchStatus]:
    """Combine E5 and JEV scores using weighted sum + JEV veto gate."""
    if jev_result is None:
        return None, "NOT_ENOUGH"

    candidates: list[tuple[float, float, str]] = []
    max_jev = 0.0
    for goal in goals:
        goal_id = goal.goal_id
        e5_raw = e5_scores.get(goal_id, -math.inf)
        jev_score = jev_result.goal_scores.get(goal_id, 0.0)
        if not math.isfinite(e5_raw) or not math.isfinite(jev_score):
            return None, "NOT_ENOUGH"
        max_jev = max(max_jev, jev_score)
        e5_norm = min(1.0, max(0.0, e5_raw))
        # JEV veto gate: block E5 domain false positives when JEV rejects the specific subtopic.
        if jev_score < config.jev_veto_threshold or e5_norm < config.e5_min_floor:
            continue
        combined = (1.0 - config.w_jev) * e5_norm + config.w_jev * jev_score
        if combined >= config.combined_match_floor and jev_score >= config.jev_match_min:
            candidates.append((combined, jev_score, goal_id))

    if candidates:
        candidates.sort(reverse=True)
        if len(candidates) > 1 and (candidates[0][0] - candidates[1][0]) < config.rival_margin:
            return None, "NOT_ENOUGH"
        return candidates[0][2], "MATCHED"

    if (
        not metadata_only
        and extraction_status is ExtractionStatus.SUCCESS
        and is_study
        and math.isfinite(jev_result.different_study_topic)
        and jev_result.different_study_topic >= config.jev_far_min
        and max_jev < config.jev_match_min
    ):
        return None, "FAR"

    return None, "NOT_ENOUGH"


async def match_goal_async(
    classifier,
    content,
    payload: dict[str, Any],
    goals: Sequence[Any],
    *,
    jev_matcher: GoalMatchJudge | None = None,
    activity_lock: asyncio.Lock | None = None,
    config: MatchConfig = DEFAULT_MATCH_CONFIG,
) -> tuple[str | None, MatchStatus]:
    """Run local E5 scoring and external JEV judgment in parallel."""
    prepared = prepare_match_input(content, payload, goals)
    if prepared is None or jev_matcher is None:
        return None, "NOT_ENOUGH"

    async def _run_e5() -> tuple[dict[str, float], bool]:
        if activity_lock is not None:
            async with activity_lock:
                return await run_in_threadpool(compute_e5_signals, classifier, prepared, content.extraction_status)
        return await run_in_threadpool(compute_e5_signals, classifier, prepared, content.extraction_status)

    async def _run_jev() -> JevGoalMatchResult | None:
        try:
            return await jev_matcher.judge(
                title=prepared.title,
                body=prepared.body,
                content_source=content.content_source.value,
                goals=goals,
            )
        except Exception:
            return None

    (e5_scores, is_study), jev_result = await asyncio.gather(_run_e5(), _run_jev())
    return decide_goal_match(
        e5_scores,
        jev_result,
        goals,
        metadata_only=prepared.metadata_only,
        extraction_status=content.extraction_status,
        is_study=is_study,
        config=config,
    )


def match_goal(
    classifier,
    content,
    payload: dict[str, Any],
    goals: Sequence[Any],
    *,
    jev_matcher: GoalMatchJudge | None = None,
    config: MatchConfig = DEFAULT_MATCH_CONFIG,
) -> tuple[str | None, MatchStatus]:
    """Synchronous wrapper for callers outside an active event loop."""
    return asyncio.run(
        match_goal_async(
            classifier,
            content,
            payload,
            goals,
            jev_matcher=jev_matcher,
            config=config,
        )
    )

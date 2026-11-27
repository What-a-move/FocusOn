"""Analyze verified Backend input without storing or logging content text."""
import json
import logging
import re
import secrets
from time import perf_counter
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Header, HTTPException, Request, Security
from pydantic import Field, ValidationError
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from starlette.concurrency import run_in_threadpool
from starlette.requests import ClientDisconnect

from .reception import (
    REVIEW_CONCEPT_MAX_LENGTH,
    AnalysisDecisionResponse,
    AnalysisReception,
    ReviewEvidence,
)
from .relevance_contract import adapt_screen_request
from .schemas import GoalProfile, InternalSchema, SafeInputError
from ..analysis.acquisition_policy import desktop_target_block_reason
from ..preprocessing.adapter import observation_is_current
from ..preprocessing.models import ContentSource, ExtractionStatus
from ..preprocessing.validator import contains_sensitive_data
from ..models.screen_activity.beta_decision import beta_decision
from ..models.screen_activity.goal_match import goal_terms, match_goal, match_goal_async, review_concept
from ..utils.logger import log_event

logger = logging.getLogger(__name__)
MODEL_VERSION = "e5-activity-10168"
DECISION_VERSION = "e5-activity-v1-uncalibrated"
ACTIVITY_TO_DECISION = {"STUDY": "RELATED", "OFF_TASK": "UNRELATED", "UNCERTAIN": "UNCERTAIN"}
MAX_REQUEST_BYTES = 65_536
bearer = HTTPBearer(auto_error=False, scheme_name="BackendServiceKey")
router = APIRouter()


def require_backend(request: Request,
                    credentials: HTTPAuthorizationCredentials | None = Security(bearer)) -> None:
    key = request.app.state.backend_api_key
    if credentials is None or not secrets.compare_digest(credentials.credentials.encode(), key.encode()):
        raise HTTPException(401, "UNAUTHORIZED", headers={"WWW-Authenticate": "Bearer"})


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


async def read_payload(request: Request) -> object:
    if request.headers.get("content-type", "").split(";", 1)[0].strip().lower() != "application/json":
        raise HTTPException(415, "JSON_REQUIRED")
    if request.headers.get("content-encoding", "identity").lower() != "identity":
        raise HTTPException(415, "CONTENT_ENCODING_UNSUPPORTED")
    data = bytearray()
    try:
        async for chunk in request.stream():
            if len(data) + len(chunk) > MAX_REQUEST_BYTES:
                raise HTTPException(413, "REQUEST_TOO_LARGE")
            data.extend(chunk)
    except ClientDisconnect:
        raise HTTPException(400, "INCOMPLETE_REQUEST") from None
    try:
        return json.loads(data.decode("utf-8"), object_pairs_hook=_unique_object)
    except (ValueError, UnicodeError, RecursionError):
        raise HTTPException(422, "INVALID_INPUT") from None


async def validated_input(request: Request):
    payload = await read_payload(request)
    try:
        envelope = AnalysisReception.parse_safe(payload)
    except (SafeInputError, RecursionError):
        raise HTTPException(422, "INVALID_INPUT") from None
    if envelope.request.guard_status is not None or envelope.context.exclusion_mode.value != "NONE":
        raise HTTPException(409, "GUARD_BLOCKED")
    if envelope.extraction_status is ExtractionStatus.EXCLUDED:
        raise HTTPException(409, "GUARD_BLOCKED")
    try:
        content = adapt_screen_request(envelope.request, envelope.goal, envelope.context,
                                       extraction_status=envelope.extraction_status, ocr=envelope.ocr)
    except ValueError:
        raise HTTPException(422, "OBSERVATION_OR_GOAL_MISMATCH") from None
    if not observation_is_current(content, envelope.context) or not content.is_active_target:
        raise HTTPException(409, "OBSERVATION_NOT_CURRENT")
    if content.content_source is ContentSource.CHROME_VIEWPORT_OCR:
        raise HTTPException(409, "CHROME_OCR_DEFERRED_P1")
    block = desktop_target_block_reason(content.content_source, content.app_name)
    if block:
        raise HTTPException(409, block)
    goals = envelope.request.goals or (envelope.goal,)
    if any(contains_sensitive_data(value) for value in (
            content.text, content.page_title, content.window_title, content.app_name,
            content.url_host, *(term for goal in goals for term in goal_terms(goal)))):
        raise HTTPException(422, "CONTENT_BLOCKED")
    return content, goals


@router.post("/internal/v1/analysis-runs", response_model=AnalysisDecisionResponse,
             dependencies=[Security(require_backend)],
             openapi_extra={"requestBody": {"required": True, "content": {
                 "application/json": {"schema": {"$ref": "#/components/schemas/AnalysisReception"}}}}})
async def analyze_relevance(request: Request) -> AnalysisDecisionResponse:
    """Validate the trusted envelope and classify the active screen locally."""
    content, goals = await validated_input(request)
    return await run_analysis_async(
        content,
        request.app.state.activity_classifier,
        goals,
        jev_matcher=getattr(request.app.state, "jev_goal_matcher", None),
        activity_lock=request.app.state.activity_lock,
    )


def _build_payload(content) -> dict:
    return {
        "contentSource": content.content_source.value,
        "metadata": {
            "appName": content.app_name, "pageTitle": content.page_title,
            "windowTitle": content.window_title, "urlHost": content.url_host,
        },
        "contentPayload": {"text": content.text},
    }


def _predict_activity(classifier, payload: dict, extraction_status: ExtractionStatus) -> tuple[str, str]:
    prediction = classifier.predict([payload])[0]
    result = beta_decision(payload, prediction, extraction_status.value)
    return ACTIVITY_TO_DECISION[result["label"]], result["reasonCode"]


def _extract_review_evidence(classifier, content, payload: dict, goals, goal_id: str | None) -> list[ReviewEvidence]:
    goal = next(item for item in goals if item.goal_id == goal_id)
    concept = review_concept(classifier, content, payload, goal)
    if (concept and len(concept) <= REVIEW_CONCEPT_MAX_LENGTH
            and not re.search(r"(?i)://|www\.", concept) and not contains_sensitive_data(concept)):
        return [ReviewEvidence(conceptLabel=concept, evidenceType="CONCEPT")]
    return []


async def run_analysis_async(
    content,
    classifier,
    goals,
    *,
    jev_matcher=None,
    activity_lock=None,
) -> AnalysisDecisionResponse:
    label, reason = "UNCERTAIN", "ANALYSIS_UNAVAILABLE"
    goal_id = match_status = None
    review_evidence: list[ReviewEvidence] = []
    if classifier is not None and content.extraction_status in {ExtractionStatus.SUCCESS, ExtractionStatus.PARTIAL}:
        payload = _build_payload(content)
        try:
            if activity_lock is not None:
                async with activity_lock:
                    label, reason = await run_in_threadpool(
                        _predict_activity, classifier, payload, content.extraction_status
                    )
            else:
                label, reason = await run_in_threadpool(
                    _predict_activity, classifier, payload, content.extraction_status
                )
        except Exception:
            # Inference errors may contain input text or local runtime details.
            log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE")

        if label == "RELATED":
            try:
                goal_id, match_status = await match_goal_async(
                    classifier,
                    content,
                    payload,
                    goals,
                    jev_matcher=jev_matcher,
                    activity_lock=activity_lock,
                )
            except Exception:
                log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE")
                match_status = "NOT_ENOUGH"

        if label == "RELATED" and match_status == "MATCHED":
            try:
                if activity_lock is not None:
                    async with activity_lock:
                        review_evidence = await run_in_threadpool(
                            _extract_review_evidence, classifier, content, payload, goals, goal_id
                        )
                else:
                    review_evidence = await run_in_threadpool(
                        _extract_review_evidence, classifier, content, payload, goals, goal_id
                    )
            except Exception:
                log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE")

    return AnalysisDecisionResponse(decision=label, confidence=None, reasonCode=reason,
                                    modelVersion=MODEL_VERSION, promptVersion=DECISION_VERSION,
                                    matchedGoalId=goal_id, matchStatus=match_status,
                                    reviewEvidence=review_evidence)


def run_analysis(content, classifier, goals, *, jev_matcher=None) -> AnalysisDecisionResponse:
    label, reason = "UNCERTAIN", "ANALYSIS_UNAVAILABLE"
    goal_id = match_status = None
    review_evidence: list[ReviewEvidence] = []
    if classifier is not None and content.extraction_status in {ExtractionStatus.SUCCESS, ExtractionStatus.PARTIAL}:
        payload = _build_payload(content)
        try:
            label, reason = _predict_activity(classifier, payload, content.extraction_status)
        except Exception:
            log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE")

        if label == "RELATED":
            try:
                goal_id, match_status = match_goal(classifier, content, payload, goals, jev_matcher=jev_matcher)
            except Exception:
                log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE")
                match_status = "NOT_ENOUGH"

        if label == "RELATED" and match_status == "MATCHED":
            try:
                review_evidence = _extract_review_evidence(classifier, content, payload, goals, goal_id)
            except Exception:
                log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE")

    return AnalysisDecisionResponse(decision=label, confidence=None, reasonCode=reason,
                                    modelVersion=MODEL_VERSION, promptVersion=DECISION_VERSION,
                                    matchedGoalId=goal_id, matchStatus=match_status,
                                    reviewEvidence=review_evidence)


from src.api.goal_routes import get_goal_workflow

class ConfirmedProfileRequest(InternalSchema):
    goal_text: str = Field(alias="goalText", min_length=2, max_length=40, repr=False)
    locale: str = Field(default="ko-KR", min_length=2, max_length=35, pattern=r"^[A-Za-z0-9-]+$")


class ConfirmedProfileResponse(InternalSchema):
    profile: GoalProfile = Field(alias="goalProfile")
    model_config = {"serialize_by_alias": True}


@router.post("/internal/v1/goals/profile", response_model=ConfirmedProfileResponse,
             dependencies=[Security(require_backend)])
async def generate_confirmed_profile(
    request: ConfirmedProfileRequest,
    trace_header: Annotated[str | None, Header(alias="X-Trace-Id")] = None,
) -> ConfirmedProfileResponse:
    from src.api.schemas import GoalClarifyRequest
    from src.config import get_settings
    from src.models.errors import InvalidModelResponseError, ModelConfigurationError, ModelUnavailableError
    from src.models.llm_client import ClaudeGoalGenerationClient

    goal = request.goal_text.strip()
    if len(goal) < 2 or contains_sensitive_data(goal):
        raise HTTPException(422, "INVALID_GOAL_TEXT")
    settings = get_settings().model_copy(update={"goal_assistance_max_retries": 0})
    generator = ClaudeGoalGenerationClient(settings)
    try:
        trace_id = str(UUID(trace_header)) if trace_header and len(trace_header) == 36 else None
    except ValueError:
        trace_id = None
    log_fields = {"module": "ai", "component": "goal-profile", "traceId": trace_id,
                  "path": "/internal/v1/goals/profile"}
    started = perf_counter()
    log_event(logger, "ai.call.start", durMs=0, **log_fields)
    try:
        draft = await generator.generate_profile(GoalClarifyRequest(
            requestId="confirmed-profile", goalText=goal, locale=request.locale, conversationTurn=0))
        profile = GoalProfile.model_validate(draft.model_dump(by_alias=True, mode="json",
            include={"main_topic", "purpose", "core_topics", "supporting_topics", "expected_activities"}))
        if any(contains_sensitive_data(term) for term in (
            profile.main_topic, *profile.core_topics, *profile.supporting_topics, *profile.expected_activities
        )):
            raise ValueError("CONTENT_BLOCKED")
        log_event(logger, "ai.call.ok", durMs=round((perf_counter() - started) * 1000, 1), **log_fields)
        return ConfirmedProfileResponse(goalProfile=profile)
    except (ValidationError, ValueError, InvalidModelResponseError):
        log_event(logger, "ai.call.fail", level=logging.WARNING, error_code="INVALID_MODEL_RESPONSE",
                  durMs=round((perf_counter() - started) * 1000, 1), **log_fields)
        raise HTTPException(502, "INVALID_MODEL_RESPONSE") from None
    except (ModelConfigurationError, ModelUnavailableError):
        log_event(logger, "ai.call.fail", level=logging.WARNING, error_code="AI_UNAVAILABLE",
                  durMs=round((perf_counter() - started) * 1000, 1), **log_fields)
        raise HTTPException(503, "AI_UNAVAILABLE") from None
    finally:
        await generator.aclose()

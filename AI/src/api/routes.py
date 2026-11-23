"""Analyze verified Backend input without storing or logging content text."""
import json
import secrets
import logging

from fastapi import APIRouter, HTTPException, Request, Security
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from starlette.concurrency import run_in_threadpool
from starlette.requests import ClientDisconnect

from .reception import AnalysisReception, AnalysisDecisionResponse
from .relevance_contract import adapt_screen_request
from .schemas import SafeInputError
from ..analysis.acquisition_policy import desktop_target_block_reason
from ..preprocessing.adapter import observation_is_current
from ..preprocessing.models import ContentSource, ExtractionStatus
from ..preprocessing.validator import contains_sensitive_data
from ..models.screen_activity.beta_decision import beta_decision
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
    if any(contains_sensitive_data(value) for value in (
            content.text, content.page_title, content.window_title, content.app_name,
            content.url_host, envelope.goal.text)):
        raise HTTPException(422, "CONTENT_BLOCKED")
    return content


@router.post("/internal/v1/analysis-runs", response_model=AnalysisDecisionResponse,
             dependencies=[Security(require_backend)],
             openapi_extra={"requestBody": {"required": True, "content": {
                 "application/json": {"schema": {"$ref": "#/components/schemas/AnalysisReception"}}}}})
async def analyze_relevance(request: Request) -> AnalysisDecisionResponse:
    """Validate the trusted envelope and classify the active screen locally."""
    content = await validated_input(request)
    # Fast tokenizers share mutable padding/truncation settings within a worker.
    async with request.app.state.activity_lock:
        return await run_in_threadpool(run_analysis, content, request.app.state.activity_classifier)


def run_analysis(content, classifier) -> AnalysisDecisionResponse:
    label, reason = "UNCERTAIN", "ANALYSIS_UNAVAILABLE"
    if classifier is not None and content.extraction_status in {ExtractionStatus.SUCCESS, ExtractionStatus.PARTIAL}:
        payload = {
            "contentSource": content.content_source.value,
            "metadata": {
                "appName": content.app_name, "pageTitle": content.page_title,
                "windowTitle": content.window_title, "urlHost": content.url_host,
            },
            "contentPayload": {"text": content.text},
        }
        try:
            prediction = classifier.predict([payload])[0]
            result = beta_decision(payload, prediction, content.extraction_status.value)
            label, reason = ACTIVITY_TO_DECISION[result["label"]], result["reasonCode"]
        except Exception:
            # Inference errors may contain input text or local runtime details.
            log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE")

    return AnalysisDecisionResponse(decision=label, confidence=None, reasonCode=reason,
                                    modelVersion=MODEL_VERSION, promptVersion=DECISION_VERSION)


from src.api.goal_routes import get_goal_workflow

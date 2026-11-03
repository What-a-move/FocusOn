"""Receive verified Backend input without storing text or running a model."""
import json
import secrets
import os

from fastapi import APIRouter, HTTPException, Request, Security
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from starlette.concurrency import run_in_threadpool

from .reception import AnalysisReception, ReceptionReceipt, ExperimentalAnalysisResponse
from .relevance_contract import adapt_screen_request, public_response
from .schemas import SafeInputError
from ..analysis.acquisition_policy import desktop_target_block_reason
from ..preprocessing.adapter import observation_is_current
from ..preprocessing.models import ContentSource, ExtractionStatus
from ..preprocessing.validator import contains_sensitive_data

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
    async for chunk in request.stream():
        if len(data) + len(chunk) > MAX_REQUEST_BYTES:
            raise HTTPException(413, "REQUEST_TOO_LARGE")
        data.extend(chunk)
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
    return envelope, content


@router.post("/internal/v1/analysis-runs", response_model=ReceptionReceipt,
             dependencies=[Security(require_backend)],
             openapi_extra={"requestBody": {"required": True, "content": {
                 "application/json": {"schema": {"$ref": "#/components/schemas/AnalysisReception"}}}}})
async def receive_analysis(request: Request) -> ReceptionReceipt:
    """HTTP 200 confirms reception only; no queue, persistence or scoring."""
    envelope, content = await validated_input(request)
    return ReceptionReceipt.parse_safe({"status": "RECEIVED", "sessionId": content.session_id,
                                       "clientRunId": content.client_run_id, "analysisExecuted": False})


def run_analysis(envelope, content, client_factory):
    # The existing evaluation engine imports its models via `src`. Convert at
    # the boundary so that Enum class identities remain consistent.
    from AI.evaluation.analysis_runner import compare_graphless
    from src.api.schemas import ConfirmedGoal, ContentInput, VerifiedContext
    from ..models.embedding_client import EmbeddingSettings, OllamaEmbeddingClient

    goal = ConfirmedGoal.parse_safe(envelope.goal.model_dump(mode="json", by_alias=True))
    observation = ContentInput.parse_safe(content.model_dump(mode="json", by_alias=True))
    context = VerifiedContext.parse_safe(envelope.context.model_dump(mode="json", by_alias=True))
    revision = os.environ.get("FOCUSON_EMBEDDING_REVISION", "3c93b6415795f9cc3e16c403a492ce25b1c0e260f2f760acc90e2035de25f9bc")
    try:
        client = client_factory() if client_factory else OllamaEmbeddingClient(EmbeddingSettings(
            base_url=os.environ.get("FOCUSON_EMBEDDING_URL", "http://127.0.0.1:11435"), revision=revision))
        try:
            row = compare_graphless(goal, [("request", observation, context, None)], client)["rows"][0]
        finally:
            client.close()
        label, reason = row["relevanceLabel"], row["reasonCode"]
        candidate = label if row["status"] == "COMPLETED" and label in {"RELATED", "UNRELATED", "UNCERTAIN"} else None
        if row["status"] == "FAILED":
            label, reason = "UNCERTAIN", "ANALYSIS_UNAVAILABLE"
        response = public_response(label if label in {"RELATED", "UNRELATED", "UNCERTAIN"} else "UNCERTAIN",
                                   reason, source=content.content_source, confidence=None,
                                   model_version="qwen3-embedding-4b-fp16", prompt_version=(
                                       "title-hybrid-v3-mvp" if content.content_source in {ContentSource.APP_META, ContentSource.TAB_META}
                                       else "body-context-hybrid-v3"))
        return {**response.model_dump(mode="json", by_alias=True), "candidateDecision": candidate,
                "candidateReasonCode": reason if candidate else None, "candidateCalibrated": False}
    except Exception:
        # Avoid logging provider errors, input text and configuration secrets.
        response = public_response("UNCERTAIN", "ANALYSIS_UNAVAILABLE", source=content.content_source,
                                   confidence=None, model_version="qwen3-embedding-4b-fp16",
                                   prompt_version="body-context-hybrid-v3")
        return {**response.model_dump(mode="json", by_alias=True), "candidateDecision": None,
                "candidateReasonCode": None, "candidateCalibrated": False}


@router.post("/internal/v1/analysis-runs/analyze", response_model=ExperimentalAnalysisResponse,
             dependencies=[Security(require_backend)],
             openapi_extra={"requestBody": {"required": True, "content": {
                 "application/json": {"schema": {"$ref": "#/components/schemas/AnalysisReception"}}}}})
async def analyze_relevance(request: Request):
    envelope, content = await validated_input(request)
    return await run_in_threadpool(run_analysis, envelope, content,
                                   getattr(request.app.state, "embedding_client_factory", None))

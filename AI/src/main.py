"""Run from the repository root: python3 -m uvicorn AI.src.main:app --http h11."""
from asyncio import Lock
from contextlib import asynccontextmanager
import os
import logging
from time import perf_counter

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from .api.reception import AnalysisReception
from .api.routes import router
from src.api.goal_routes import router as goal_router
from src.api.schemas import ErrorResponse
from src.utils.logger import configure_logging, log_event
from .models.screen_activity.activity import ActivityClassifier
from .models.screen_activity.jev_goal_matcher import TypeSafeJevGoalMatcher

logger = logging.getLogger(__name__)


def load_activity_classifier():
    classifier = ActivityClassifier()
    # Warm the same inference path before accepting Backend traffic.
    classifier.predict([{"contentSource": "TAB_META", "metadata": {"pageTitle": "FocusOn readiness probe"}}])
    return classifier


def load_jev_goal_matcher():
    from .config import get_settings

    return TypeSafeJevGoalMatcher(get_settings())


def create_app(
    *,
    api_key: str | None = None,
    classifier_factory=load_activity_classifier,
    jev_goal_matcher_factory=load_jev_goal_matcher,
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        key = api_key if api_key is not None else os.environ.get("FOCUSON_AI_API_KEY", "")
        if not 32 <= len(key.encode()) <= 1024 or key.strip() != key:
            raise RuntimeError("FOCUSON_AI_API_KEY_REQUIRED_MIN_32_BYTES")
        application.state.backend_api_key = key
        application.state.activity_lock = Lock()
        started = perf_counter()
        try:
            application.state.activity_classifier = await run_in_threadpool(classifier_factory)
        except Exception:
            application.state.activity_classifier = None
            log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE", path="/health")
        try:
            application.state.jev_goal_matcher = jev_goal_matcher_factory() if jev_goal_matcher_factory else None
        except Exception:
            application.state.jev_goal_matcher = None
            log_event(logger, "request.fail", level=logging.WARNING, error_code="ANALYSIS_UNAVAILABLE", path="/health")
        application.state.model_load_ms = round((perf_counter() - started) * 1000, 1)
        try:
            yield
        finally:
            matcher = getattr(application.state, "jev_goal_matcher", None)
            if matcher is not None and hasattr(matcher, "aclose"):
                await matcher.aclose()
            application.state.jev_goal_matcher = None
            application.state.activity_classifier = None
            application.state.backend_api_key = ""

    configure_logging()
    application = FastAPI(title="FocusOn AI reception", version="0.1.0", lifespan=lifespan)
    application.include_router(router)
    application.include_router(goal_router)

    @application.get("/health")
    async def health():
        if application.state.activity_classifier is None:
            return JSONResponse(status_code=503, content={
                "status": "unavailable", "analysisEnabled": False,
                "modelConnected": False, "reasonCode": "ANALYSIS_UNAVAILABLE",
            })
        return {"status": "ready", "mode": "analysis", "analysisEnabled": True,
                "decisionMode": "candidate", "modelConnected": True,
                "modelProbeMs": application.state.model_load_ms}

    @application.exception_handler(RequestValidationError)
    async def invalid_request(request: Request, exc: RequestValidationError):
        if request.url.path.startswith("/internal/v1/goals/"):
            return await goal_validation_error(request, exc)
        return JSONResponse(status_code=422, content={"detail": "INVALID_INPUT"})

    async def goal_validation_error(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        request_id: str | None = None
        try:
            body = await request.json()
            if isinstance(body, dict) and isinstance(body.get("requestId"), str):
                request_id = body["requestId"][:100]
        except Exception:
            request_id = None
        violations = [
            {
                "field": ".".join(str(part) for part in error.get("loc", [])[1:]),
                "reason": error.get("type", "validation_error"),
            }
            for error in exc.errors()
        ]
        response = ErrorResponse(
            code="VALIDATION_ERROR",
            message="요청 값이 올바르지 않습니다.",
            retryable=False,
            request_id=request_id,
            details={"fallbackAllowed": False, "violations": violations},
        )
        return JSONResponse(
            status_code=422,
            content=response.model_dump(mode="json", by_alias=True, exclude_none=True),
        )


    original_openapi = application.openapi

    def openapi():
        schema = original_openapi()
        envelope = AnalysisReception.model_json_schema(ref_template="#/components/schemas/{model}")
        definitions = envelope.pop("$defs", {})
        schema.setdefault("components", {}).setdefault("schemas", {}).update(definitions)
        schema["components"]["schemas"]["AnalysisReception"] = envelope
        schema["paths"]["/internal/v1/analysis-runs"]["post"]["requestBody"]["content"]["application/json"]["schema"] = {
            "$ref": "#/components/schemas/AnalysisReception"}
        return schema

    application.openapi = openapi
    return application


app = create_app()

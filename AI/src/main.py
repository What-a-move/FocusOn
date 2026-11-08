"""Run from the repository root: python3 -m uvicorn AI.src.main:app."""
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
from .models.embedding_client import configured_embedding_client

logger = logging.getLogger(__name__)


def create_app(*, api_key: str | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        key = api_key if api_key is not None else os.environ.get("FOCUSON_AI_API_KEY", "")
        if not 32 <= len(key.encode()) <= 1024 or key.strip() != key:
            raise RuntimeError("FOCUSON_AI_API_KEY_REQUIRED_MIN_32_BYTES")
        application.state.backend_api_key = key
        yield
        application.state.backend_api_key = ""

    configure_logging()
    application = FastAPI(title="FocusOn AI reception", version="0.1.0", lifespan=lifespan)
    application.include_router(router)
    application.include_router(goal_router)

    @application.get("/health")
    async def health():
        def probe_model():
            factory = getattr(application.state, "embedding_client_factory", None)
            client = factory() if factory else configured_embedding_client(probe=True)
            try:
                vectors = client.embed(["FocusOn model readiness probe"])
                if len(vectors) != 1 or len(vectors[0]) != 2560:
                    raise ValueError("MODEL_PROBE_INVALID")
            finally:
                client.close()

        started = perf_counter()
        try:
            await run_in_threadpool(probe_model)
        except Exception:
            log_event(logger, "request.fail", level=logging.WARNING,
                      error_code="ANALYSIS_UNAVAILABLE", path="/health")
            return JSONResponse(status_code=503, content={
                "status": "unavailable", "analysisEnabled": False,
                "modelConnected": False, "reasonCode": "ANALYSIS_UNAVAILABLE",
            })
        return {"status": "ready", "mode": "analysis", "analysisEnabled": True,
                "decisionMode": os.environ.get("FOCUSON_ANALYSIS_DECISION_MODE", "safe"),
                "modelConnected": True, "modelProbeMs": round((perf_counter() - started) * 1000, 1)}

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

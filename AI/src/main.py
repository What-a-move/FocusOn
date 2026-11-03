"""Run from the repository root: python3 -m uvicorn AI.src.main:app."""
from contextlib import asynccontextmanager
import os

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .api.reception import AnalysisReception
from .api.routes import router


def create_app(*, api_key: str | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        key = api_key if api_key is not None else os.environ.get("FOCUSON_AI_API_KEY", "")
        if not 32 <= len(key.encode()) <= 1024 or key.strip() != key:
            raise RuntimeError("FOCUSON_AI_API_KEY_REQUIRED_MIN_32_BYTES")
        application.state.backend_api_key = key
        yield
        application.state.backend_api_key = ""

    application = FastAPI(title="FocusOn AI reception", version="0.1.0", lifespan=lifespan)
    application.include_router(router)

    @application.get("/health")
    async def health():
        return {"status": "ready", "mode": "experimental-analysis", "analysisEnabled": True}

    @application.exception_handler(RequestValidationError)
    async def invalid_request(request: Request, exc: RequestValidationError):
        # FastAPI's default validation response can include original text.
        return JSONResponse(status_code=422, content={"detail": "INVALID_INPUT"})

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

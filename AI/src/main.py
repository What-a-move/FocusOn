from __future__ import annotations

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from src.api.routes import router
from src.api.schemas import ErrorResponse
from src.utils.logger import configure_logging


def create_app() -> FastAPI:
    configure_logging()
    app = FastAPI(
        title="FocusOn AI Internal API",
        version="0.1.0",
        description="Local/internal API only. Deployment authentication is not yet defined.",
    )
    app.include_router(router)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        request_id: str | None = None
        try:
            body: Any = await request.json()
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

    @app.get("/health", include_in_schema=False)
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    return app


app = create_app()

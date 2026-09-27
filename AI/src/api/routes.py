from typing import Annotated, AsyncIterator

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from src.api.schemas import (
    ErrorResponse,
    GoalClarifyRequest,
    GoalClarifyResponse,
)
from src.config import Settings, get_settings
from src.models.errors import (
    InputValidationError,
    InvalidModelResponseError,
    ModelConfigurationError,
    ModelUnavailableError,
)
from src.models.jev_client import TypeSafeJevClient
from src.models.llm_client import OpenAIGoalGenerationClient
from src.workflow.goal_assistance import GoalAssistanceWorkflow


router = APIRouter(prefix="/internal/v1/goals", tags=["goal-assistance"])


async def get_goal_workflow() -> AsyncIterator[GoalAssistanceWorkflow]:
    settings = get_settings()
    judgments = TypeSafeJevClient(settings)
    generator = OpenAIGoalGenerationClient(settings)
    try:
        yield GoalAssistanceWorkflow(settings, judgments, generator)
    finally:
        await judgments.aclose()


WorkflowDependency = Annotated[GoalAssistanceWorkflow, Depends(get_goal_workflow)]


def _error_response(
    status_code: int,
    *,
    code: str,
    message: str,
    retryable: bool,
    fallback_allowed: bool,
    request_id: str,
    details: list[dict[str, object]] | None = None,
) -> JSONResponse:
    error_details: dict[str, object] = {"fallbackAllowed": fallback_allowed}
    if details:
        error_details["violations"] = details
    body = ErrorResponse(
        code=code,
        message=message,
        retryable=retryable,
        retry_after_seconds=1 if retryable else None,
        request_id=request_id,
        details=error_details,
    )
    return JSONResponse(
        status_code=status_code,
        content=body.model_dump(mode="json", by_alias=True, exclude_none=True),
    )


@router.post(
    "/clarify",
    response_model=GoalClarifyResponse,
    responses={422: {"model": ErrorResponse}, 502: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
async def clarify_goal(
    request: GoalClarifyRequest,
    workflow: WorkflowDependency,
) -> GoalClarifyResponse | JSONResponse:
    try:
        result = await workflow.run(request)
        return GoalClarifyResponse(data=result)
    except InputValidationError as exc:
        return _error_response(
            422,
            code="VALIDATION_ERROR",
            message="요청 값이 올바르지 않습니다.",
            retryable=False,
            fallback_allowed=False,
            request_id=request.request_id,
            details=exc.details,
        )
    except InvalidModelResponseError:
        return _error_response(
            502,
            code="INVALID_MODEL_RESPONSE",
            message="AI 응답을 확인하지 못했습니다. 다시 시도해 주세요.",
            retryable=True,
            fallback_allowed=True,
            request_id=request.request_id,
        )
    except ModelUnavailableError:
        return _error_response(
            503,
            code="AI_UNAVAILABLE",
            message="AI 서비스를 일시적으로 사용할 수 없습니다.",
            retryable=True,
            fallback_allowed=True,
            request_id=request.request_id,
        )
    except ModelConfigurationError:
        return _error_response(
            503,
            code="AI_UNAVAILABLE",
            message="AI 서비스 설정을 확인해야 합니다.",
            retryable=False,
            fallback_allowed=True,
            request_id=request.request_id,
        )

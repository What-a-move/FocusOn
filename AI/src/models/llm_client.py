from __future__ import annotations

from typing import Any, Protocol, TypeVar

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.exceptions import OutputParserException
from langchain_openai import ChatOpenAI
from openai import (
    APIStatusError,
    AuthenticationError,
    BadRequestError,
    PermissionDeniedError,
)
from pydantic import BaseModel, ValidationError

from src.api.schemas import (
    ClarityStatus,
    ClarificationQuestion,
    GoalClarifyRequest,
    GoalProfileDraft,
    RecommendedGoal,
    RecommendedGoalBatch,
)
from src.config import Settings
from src.models.errors import (
    InvalidModelResponseError,
    ModelConfigurationError,
    ModelUnavailableError,
)
from src.prompts.goal_prompt import (
    SYSTEM_PROMPT,
    candidate_prompt,
    profile_prompt,
    question_prompt,
    repair_prompt,
)


class GoalGenerationClient(Protocol):
    async def generate_candidates(
        self, request: GoalClarifyRequest, status: ClarityStatus
    ) -> RecommendedGoalBatch: ...

    async def generate_question(
        self, request: GoalClarifyRequest, status: ClarityStatus
    ) -> ClarificationQuestion: ...

    async def generate_profile(self, request: GoalClarifyRequest) -> GoalProfileDraft: ...

    async def repair_candidate(
        self,
        request: GoalClarifyRequest,
        candidate: RecommendedGoal,
        failed_checks: list[str],
    ) -> RecommendedGoal: ...

    async def repair_profile(
        self,
        request: GoalClarifyRequest,
        profile: GoalProfileDraft,
        failed_checks: list[str],
    ) -> GoalProfileDraft: ...


SchemaT = TypeVar("SchemaT", bound=BaseModel)


class OpenAIGoalGenerationClient:
    """OpenAI adapter restricted to schema-bound natural-language generation."""

    def __init__(self, settings: Settings) -> None:
        self._using_gateway = settings.uses_ai_gateway
        api_key = settings.generation_api_key
        if api_key is None:
            self._llm = None
        else:
            self._llm = ChatOpenAI(
                model=settings.generation_model,
                api_key=api_key,
                base_url=settings.generation_base_url,
                timeout=settings.goal_assistance_timeout_seconds,
                max_retries=settings.goal_assistance_max_retries,
            )

    async def _generate(self, schema: type[SchemaT], prompt: str) -> SchemaT:
        if self._llm is None:
            raise ModelConfigurationError("OpenAI API 설정을 사용할 수 없습니다.")
        structured = self._llm.with_structured_output(schema, method="json_schema")
        messages = [SystemMessage(content=SYSTEM_PROMPT), HumanMessage(content=prompt)]
        last_schema_error: Exception | None = None
        for attempt in range(2):
            try:
                result = await structured.ainvoke(messages)
                if isinstance(result, schema):
                    return result
                return schema.model_validate(result)
            except (ValidationError, TypeError, OutputParserException) as exc:
                last_schema_error = exc
                if attempt == 0:
                    messages = [
                        *messages,
                        HumanMessage(
                            content=(
                                "The previous result failed schema validation. Repair it and "
                                "return only an object that exactly matches the requested schema."
                            )
                        ),
                    ]
                    continue
            except (TimeoutError, ConnectionError) as exc:
                raise ModelUnavailableError(
                    "OpenAI 생성 서비스를 사용할 수 없습니다."
                ) from exc
            except (AuthenticationError, PermissionDeniedError, BadRequestError) as exc:
                provider = "Vercel AI Gateway" if self._using_gateway else "OpenAI"
                raise ModelConfigurationError(
                    f"{provider}의 API Key 또는 문장 생성 모델 설정이 올바르지 않습니다."
                ) from exc
            except APIStatusError as exc:
                if exc.status_code == 402:
                    provider = "Vercel AI Gateway" if self._using_gateway else "OpenAI"
                    raise ModelConfigurationError(
                        f"{provider} 사용 가능 잔액이 부족하거나 결제가 중지됐습니다."
                    ) from exc
                raise ModelUnavailableError(
                    "문장 생성 서비스를 사용할 수 없습니다."
                ) from exc
            except Exception as exc:  # provider exception types vary by SDK version
                raise ModelUnavailableError(
                    "OpenAI 생성 서비스를 사용할 수 없습니다."
                ) from exc
        raise InvalidModelResponseError(
            "OpenAI 구조화 응답이 올바르지 않습니다."
        ) from last_schema_error

    async def generate_candidates(
        self, request: GoalClarifyRequest, status: ClarityStatus
    ) -> RecommendedGoalBatch:
        return await self._generate(
            RecommendedGoalBatch, candidate_prompt(request, status)
        )

    async def generate_question(
        self, request: GoalClarifyRequest, status: ClarityStatus
    ) -> ClarificationQuestion:
        return await self._generate(
            ClarificationQuestion, question_prompt(request, status)
        )

    async def generate_profile(self, request: GoalClarifyRequest) -> GoalProfileDraft:
        return await self._generate(GoalProfileDraft, profile_prompt(request))

    async def repair_candidate(
        self,
        request: GoalClarifyRequest,
        candidate: RecommendedGoal,
        failed_checks: list[str],
    ) -> RecommendedGoal:
        return await self._generate(
            RecommendedGoal,
            repair_prompt(
                request,
                "recommended_goal",
                candidate.model_dump(mode="json", by_alias=True),
                failed_checks,
            ),
        )

    async def repair_profile(
        self,
        request: GoalClarifyRequest,
        profile: GoalProfileDraft,
        failed_checks: list[str],
    ) -> GoalProfileDraft:
        return await self._generate(
            GoalProfileDraft,
            repair_prompt(
                request,
                "goal_profile",
                profile.model_dump(mode="json", by_alias=True),
                failed_checks,
            ),
        )

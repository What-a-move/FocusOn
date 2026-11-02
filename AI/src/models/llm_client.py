from __future__ import annotations

import asyncio
from typing import Any, Callable, Protocol, TypeVar

from langchain_core.exceptions import OutputParserException
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.output_parsers import JsonOutputParser
from langchain_core.outputs import Generation
from langchain_openai import ChatOpenAI
from openai import (
    APIStatusError,
    AsyncOpenAI,
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
    GoalQuestionOption,
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
        self,
        request: GoalClarifyRequest,
        status: ClarityStatus,
        on_partial: Callable[[str], None] | None = None,
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
        self._question_parallelism = settings.openai_question_parallelism
        self._question_deadline_seconds = settings.openai_question_deadline_seconds
        self._openai_model = settings.openai_model
        self._openai_reasoning_effort = settings.openai_reasoning_effort
        self._openai_max_completion_tokens = settings.openai_max_completion_tokens
        api_key = settings.openai_api_key
        self._response_client: AsyncOpenAI | None = None
        self._response_api_key = api_key.get_secret_value() if api_key else None
        self._response_timeout_seconds = settings.goal_assistance_timeout_seconds
        self._response_max_retries = settings.goal_assistance_max_retries
        if api_key is None:
            self._llm = None
        else:
            self._llm = ChatOpenAI(
                model=settings.openai_model,
                api_key=api_key,
                timeout=settings.goal_assistance_timeout_seconds,
                max_retries=settings.goal_assistance_max_retries,
                reasoning_effort=settings.openai_reasoning_effort,
                verbosity=settings.openai_verbosity,
                max_completion_tokens=settings.openai_max_completion_tokens,
            )

    async def aclose(self) -> None:
        if self._response_client is not None:
            await self._response_client.close()
            self._response_client = None

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
                raise ModelConfigurationError(
                    "OpenAI API Key 또는 문장 생성 모델 설정이 올바르지 않습니다."
                ) from exc
            except APIStatusError as exc:
                if exc.status_code == 402:
                    raise ModelConfigurationError(
                        "OpenAI 사용 가능 잔액이 부족하거나 결제가 중지됐습니다."
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

    async def _stream_question(
        self, prompt: str, on_partial: Callable[[str], None]
    ) -> ClarificationQuestion:
        if self._response_client is None and self._response_api_key is None:
            raise ModelConfigurationError("OpenAI API 설정을 사용할 수 없습니다.")
        if self._response_client is None:
            self._response_client = AsyncOpenAI(
                api_key=self._response_api_key,
                timeout=self._response_timeout_seconds,
                max_retries=self._response_max_retries,
            )
        parser = JsonOutputParser()
        raw_text = ""
        last_preview = ""
        try:
            async with self._response_client.responses.stream(
                model=self._openai_model,
                instructions=SYSTEM_PROMPT,
                input=prompt,
                text_format=ClarificationQuestion,
                reasoning={"effort": self._openai_reasoning_effort},
                max_output_tokens=self._openai_max_completion_tokens,
            ) as stream:
                async for event in stream:
                    if event.type != "response.output_text.delta":
                        continue
                    raw_text += event.delta
                    try:
                        partial = parser.parse_result(
                            [Generation(text=raw_text)], partial=True
                        )
                    except (ValueError, OutputParserException):
                        continue
                    preview = partial.get("text") if isinstance(partial, dict) else None
                    if isinstance(preview, str) and preview and preview != last_preview:
                        on_partial(preview)
                        last_preview = preview
                response = await stream.get_final_response()
            question = response.output_parsed
            if isinstance(question, ClarificationQuestion):
                return question
            return ClarificationQuestion.model_validate(question)
        except (ValidationError, TypeError, ValueError, AttributeError) as exc:
            raise InvalidModelResponseError(
                "OpenAI 구조화 응답이 올바르지 않습니다."
            ) from exc
        except (TimeoutError, ConnectionError) as exc:
            raise ModelUnavailableError(
                "OpenAI 생성 서비스를 사용할 수 없습니다."
            ) from exc
        except (AuthenticationError, PermissionDeniedError, BadRequestError) as exc:
            raise ModelConfigurationError(
                "OpenAI API Key 또는 문장 생성 모델 설정이 올바르지 않습니다."
            ) from exc
        except APIStatusError as exc:
            if exc.status_code == 402:
                raise ModelConfigurationError(
                    "OpenAI 사용 가능 잔액이 부족하거나 결제가 중지됐습니다."
                ) from exc
            raise ModelUnavailableError(
                "OpenAI 생성 서비스를 사용할 수 없습니다."
            ) from exc
        except Exception as exc:
            raise ModelUnavailableError(
                "OpenAI 생성 서비스를 사용할 수 없습니다."
            ) from exc

    @staticmethod
    def _fallback_question(
        request: GoalClarifyRequest, status: ClarityStatus
    ) -> ClarificationQuestion:
        if status is ClarityStatus.UNRECOGNIZED_TERM:
            questions = [
                "목표에서 사용한 표현은 어떤 뜻인가요?",
                "확인이 필요한 표현을 풀어서 적어주실 수 있나요?",
            ]
            options = [
                GoalQuestionOption(id="technology", label="기술·도구 이름"),
                GoalQuestionOption(id="activity", label="학습 활동"),
            ]
        else:
            questions = [
                "이 목표에서 무엇을 해보고 싶나요?",
                "어떤 학습 활동에 집중하고 싶나요?",
            ]
            options = [
                GoalQuestionOption(id="concept", label="개념 학습"),
                GoalQuestionOption(id="implementation", label="직접 구현"),
            ]
        previous = {
            " ".join(answer.question.casefold().split())
            for answer in request.clarification_answers
        }
        text = next(
            (item for item in questions if " ".join(item.casefold().split()) not in previous),
            "학습할 대상이나 활동을 한 문장으로 적어주실 수 있나요?",
        )
        return ClarificationQuestion(
            id=f"fallback-{len(request.clarification_answers) + 1}",
            text=text,
            options=options,
            allow_custom_answer=True,
        )

    async def generate_question(
        self,
        request: GoalClarifyRequest,
        status: ClarityStatus,
        on_partial: Callable[[str], None] | None = None,
    ) -> ClarificationQuestion:
        if self._llm is None:
            raise ModelConfigurationError("OpenAI API 설정을 사용할 수 없습니다.")

        prompt = question_prompt(request, status)
        previous_questions = {
            " ".join(answer.question.casefold().split())
            for answer in request.clarification_answers
        }
        tasks = {
            asyncio.create_task(
                self._stream_question(prompt, on_partial)
                if on_partial is not None and index == 0
                else self._generate(ClarificationQuestion, prompt)
            )
            for index in range(self._question_parallelism)
        }
        all_tasks = tasks.copy()
        errors: list[Exception] = []
        repeated_question = False
        loop = asyncio.get_running_loop()
        fallback_at = loop.time() + self._question_deadline_seconds
        try:
            async with asyncio.timeout_at(fallback_at):
                while tasks:
                    completed, remaining = await asyncio.wait(
                        tasks, return_when=asyncio.FIRST_COMPLETED
                    )
                    for task in completed:
                        try:
                            question = task.result()
                        except Exception as exc:
                            errors.append(exc)
                            continue
                        if " ".join(question.text.casefold().split()) in previous_questions:
                            repeated_question = True
                            continue
                        return question
                    tasks = remaining
                configuration_error = next(
                    (error for error in errors if isinstance(error, ModelConfigurationError)),
                    None,
                )
                if configuration_error is not None:
                    raise configuration_error
                if repeated_question or (
                    errors
                    and all(
                        isinstance(error, (ModelUnavailableError, InvalidModelResponseError))
                        for error in errors
                    )
                ):
                    # Even failed model attempts must not show a rule-based question early.
                    await asyncio.sleep(max(0.0, fallback_at - loop.time()))
                    return self._fallback_question(request, status)
                assert errors
                raise errors[0]
        except TimeoutError:
            configuration_error = next(
                (error for error in errors if isinstance(error, ModelConfigurationError)),
                None,
            )
            if configuration_error is not None:
                raise configuration_error
            return self._fallback_question(request, status)
        finally:
            for task in all_tasks:
                task.cancel()
            await asyncio.gather(*all_tasks, return_exceptions=True)

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

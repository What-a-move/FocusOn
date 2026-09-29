from __future__ import annotations

import asyncio
from typing import Any, Protocol

from langsmith import traceable
from typesafe_sdk import (
    AsyncTypeSafeClient,
    Noul,
    RetryPolicy,
    Score,
    TypeSafeAPIError,
    TypeSafeAPIResponseValidationError,
    TypeSafeAuthenticationError,
    TypeSafeBadRequestError,
    TypeSafeError,
    TypeSafePermissionDeniedError,
    TypeSafeUnprocessableEntityError,
)

from src.analysis.goal_analyzer import (
    GoalAssessment,
    GeneratedResultVerification,
)
from src.analysis.goal_assessment_contract import (
    build_goal_assessment_question_specs,
    build_goal_assessment_state,
)
from src.api.schemas import GoalClarifyRequest
from src.config import Settings
from src.models.errors import (
    InvalidModelResponseError,
    ModelConfigurationError,
    ModelUnavailableError,
)


def _summarize_goal_request(request: GoalClarifyRequest) -> dict[str, Any]:
    return {
        "requestId": request.request_id,
        "originalTextLength": len(request.original_text),
        "hasSelectedGoalText": request.selected_goal_text is not None,
        "selectedGoalTextLength": (
            len(request.selected_goal_text) if request.selected_goal_text else 0
        ),
        "clarificationAnswersCount": len(request.clarification_answers),
    }


def _trace_jev_assessment_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    request = inputs.get("request")
    if not isinstance(request, GoalClarifyRequest):
        return {"input": "unavailable"}
    return {"request": _summarize_goal_request(request)}


def _trace_jev_assessment_outputs(output: GoalAssessment) -> dict[str, Any]:
    return {
        "isUsableGoal": output.is_usable_goal,
        "hasMultipleMainGoals": output.has_multiple_main_goals,
        "hasUnclearTerm": output.has_unclear_term,
        "specificityLevel": output.specificity_level,
        "specificityConfidence": output.specificity_confidence,
    }


def _trace_jev_verification_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    request = inputs.get("request")
    generated_result = inputs.get("generated_result")
    summarized: dict[str, Any] = {
        "resultKind": inputs.get("result_kind"),
        "generatedResultKeys": (
            sorted(generated_result.keys()) if isinstance(generated_result, dict) else []
        ),
    }
    if isinstance(request, GoalClarifyRequest):
        summarized["request"] = _summarize_goal_request(request)
    return summarized


def _trace_jev_verification_outputs(
    output: GeneratedResultVerification,
) -> dict[str, Any]:
    return {
        "preservesUserIntent": output.preserves_user_intent,
        "containsOneGoal": output.contains_one_goal,
        "isSpecificEnough": output.is_specific_enough,
    }


def _build_jev_goal_assessment_questions() -> dict[str, Noul | Score]:
    """Adapt the shared comparison contract to TypeSafe SDK primitives."""

    typed_questions: dict[str, Noul | Score] = {}
    for name, spec in build_goal_assessment_question_specs().items():
        question_type = spec["type"]
        if question_type == "noul":
            typed_questions[name] = Noul(
                instructions=spec["instructions"], criteria=spec["criteria"]
            )
        elif question_type == "score":
            typed_questions[name] = Score(
                instructions=spec["instructions"], criteria=spec["criteria"]
            )
        else:
            raise ValueError(f"Unsupported JEV question type: {question_type}")
    return typed_questions


class GoalJudgmentClient(Protocol):
    async def assess_goal(self, request: GoalClarifyRequest) -> GoalAssessment: ...

    async def verify_generated_result(
        self,
        request: GoalClarifyRequest,
        generated_result: dict[str, Any],
        result_kind: str,
    ) -> GeneratedResultVerification: ...

    async def aclose(self) -> None: ...


class TypeSafeJevClient:
    """JEV adapter. It performs typed judgments and never generates display text."""

    def __init__(self, settings: Settings) -> None:
        api_key = settings.typesafe_api_key
        if api_key is None:
            self._client = None
        else:
            self._client = AsyncTypeSafeClient(
                api_key=api_key.get_secret_value(),
                model=settings.typesafe_default_model,
                retry=RetryPolicy(
                    max_retries=settings.goal_assistance_max_retries,
                    timeout=settings.goal_assistance_timeout_seconds,
                ),
                timeout=settings.goal_assistance_timeout_seconds,
            )

    @traceable(
        name="jev_assess_goal",
        run_type="tool",
        tags=["jev", "typesafe", "goal-assistance"],
        process_inputs=_trace_jev_assessment_inputs,
        process_outputs=_trace_jev_assessment_outputs,
    )
    async def assess_goal(self, request: GoalClarifyRequest) -> GoalAssessment:
        if self._client is None:
            raise ModelConfigurationError("TypeSafe API 설정을 사용할 수 없습니다.")
        state = build_goal_assessment_state(request)
        questions = _build_jev_goal_assessment_questions()
        try:
            response = await self._client.system_one(state=state, questions=questions)
            return GoalAssessment(
                is_usable_goal=float(response.answers["is_usable_goal"].noul),
                has_multiple_main_goals=float(
                    response.answers["has_multiple_main_goals"].noul
                ),
                has_unclear_term=float(response.answers["has_unclear_term"].noul),
                specificity_level=float(response.answers["specificity_level"].score),
                specificity_confidence=float(
                    response.answers["specificity_level"].confidence
                ),
            )
        except (KeyError, TypeError, ValueError, AttributeError) as exc:
            raise InvalidModelResponseError("JEV 응답 형식이 올바르지 않습니다.") from exc
        except TypeSafeAPIResponseValidationError as exc:
            raise InvalidModelResponseError("JEV 응답 형식이 올바르지 않습니다.") from exc
        except (
            TypeSafeAuthenticationError,
            TypeSafePermissionDeniedError,
            TypeSafeBadRequestError,
            TypeSafeUnprocessableEntityError,
        ) as exc:
            raise ModelConfigurationError(
                "TypeSafe API Key 또는 JEV 모델 설정이 올바르지 않습니다."
            ) from exc
        except TypeSafeAPIError as exc:
            if exc.status == 402:
                raise ModelConfigurationError(
                    "TypeSafe 사용 가능 잔액이 부족하거나 결제가 중지됐습니다."
                ) from exc
            raise ModelUnavailableError("JEV 판단 서비스를 사용할 수 없습니다.") from exc
        except TypeSafeError as exc:
            raise ModelUnavailableError("JEV 판단 서비스를 사용할 수 없습니다.") from exc

    @traceable(
        name="jev_verify_generated_result",
        run_type="tool",
        tags=["jev", "typesafe", "goal-assistance"],
        process_inputs=_trace_jev_verification_inputs,
        process_outputs=_trace_jev_verification_outputs,
    )
    async def verify_generated_result(
        self,
        request: GoalClarifyRequest,
        generated_result: dict[str, Any],
        result_kind: str,
    ) -> GeneratedResultVerification:
        if self._client is None:
            raise ModelConfigurationError("TypeSafe API 설정을 사용할 수 없습니다.")
        state = {
            "original_goal_text": request.original_text,
            "selected_goal_text": request.selected_goal_text,
            "clarification_answers": [
                answer.model_dump(mode="json", by_alias=False)
                for answer in request.clarification_answers
            ],
            "generated_result_kind": result_kind,
            "generated_result": generated_result,
        }
        questions = {
            "preserves_user_intent": Noul(
                instructions=(
                    "Does generated_result preserve the user's intended learning topic and "
                    "activity without inventing a materially different goal?"
                ),
                criteria={
                    "true": "The generated result matches the user's supplied intent",
                    "false": "It changes, invents, or contradicts the intended goal",
                },
            ),
            "contains_one_goal": Noul(
                instructions=(
                    "Does generated_result describe exactly one primary learning goal rather "
                    "than combining independent goals?"
                ),
                criteria={
                    "true": "Exactly one primary learning goal",
                    "false": "Multiple independent primary goals or no goal",
                },
            ),
            "is_specific_enough": Noul(
                instructions=(
                    "Is generated_result specific and actionable enough to guide a single "
                    "study session while avoiding fabricated requirements?"
                ),
                criteria={
                    "true": "Specific and actionable for one study session",
                    "false": "Still too vague, unusable, or over-specified with invented scope",
                },
            ),
        }
        try:
            response = await self._client.system_one(state=state, questions=questions)
            return GeneratedResultVerification(
                preserves_user_intent=float(
                    response.answers["preserves_user_intent"].noul
                ),
                contains_one_goal=float(response.answers["contains_one_goal"].noul),
                is_specific_enough=float(response.answers["is_specific_enough"].noul),
            )
        except (KeyError, TypeError, ValueError, AttributeError) as exc:
            raise InvalidModelResponseError("JEV 검증 응답 형식이 올바르지 않습니다.") from exc
        except TypeSafeAPIResponseValidationError as exc:
            raise InvalidModelResponseError("JEV 검증 응답 형식이 올바르지 않습니다.") from exc
        except (
            TypeSafeAuthenticationError,
            TypeSafePermissionDeniedError,
            TypeSafeBadRequestError,
            TypeSafeUnprocessableEntityError,
        ) as exc:
            raise ModelConfigurationError(
                "TypeSafe API Key 또는 JEV 모델 설정이 올바르지 않습니다."
            ) from exc
        except TypeSafeAPIError as exc:
            if exc.status == 402:
                raise ModelConfigurationError(
                    "TypeSafe 사용 가능 잔액이 부족하거나 결제가 중지됐습니다."
                ) from exc
            raise ModelUnavailableError("JEV 판단 서비스를 사용할 수 없습니다.") from exc
        except TypeSafeError as exc:
            raise ModelUnavailableError("JEV 판단 서비스를 사용할 수 없습니다.") from exc

    async def aclose(self) -> None:
        if self._client is None:
            return
        close = getattr(self._client, "aclose", None)
        if close is not None:
            result = close()
            if asyncio.iscoroutine(result):
                await result

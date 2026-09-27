from __future__ import annotations

import asyncio
from typing import Any, Protocol

from typesafe_sdk import (
    AsyncTypeSafeClient,
    Noul,
    RetryPolicy,
    Score,
    TypeSafeAPIResponseValidationError,
    TypeSafeAuthenticationError,
    TypeSafeBadRequestError,
    TypeSafeError,
    TypeSafePermissionDeniedError,
    TypeSafeUnprocessableEntityError,
)

from src.analysis.goal_analyzer import GoalAssessment, GeneratedResultVerification
from src.api.schemas import GoalClarifyRequest
from src.config import Settings
from src.models.errors import (
    InvalidModelResponseError,
    ModelConfigurationError,
    ModelUnavailableError,
)


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
        if settings.typesafe_api_key is None:
            self._client = None
        else:
            self._client = AsyncTypeSafeClient(
                api_key=settings.typesafe_api_key.get_secret_value(),
                model=settings.typesafe_default_model,
                retry=RetryPolicy(
                    max_retries=settings.goal_assistance_max_retries,
                    timeout=settings.goal_assistance_timeout_seconds,
                ),
                timeout=settings.goal_assistance_timeout_seconds,
            )

    async def assess_goal(self, request: GoalClarifyRequest) -> GoalAssessment:
        if self._client is None:
            raise ModelConfigurationError("TypeSafe API 설정을 사용할 수 없습니다.")
        state = {
            "goal_text": request.original_text,
            "selected_goal_text": request.selected_goal_text,
            "clarification_answers": [
                answer.model_dump(mode="json", by_alias=False)
                for answer in request.clarification_answers
            ],
        }
        questions = {
            "is_usable_goal": Noul(
                instructions=(
                    "Determine whether the effective goal in the state is a learning goal "
                    "that a user can meaningfully perform or study. Use selected_goal_text "
                    "when present, and use clarification_answers as added context."
                ),
                criteria={
                    "true": "A learnable topic, implementation, practice, assignment, or problem-solving goal",
                    "false": "Meaningless text or not a usable learning goal",
                },
            ),
            "has_multiple_main_goals": Noul(
                instructions=(
                    "Determine whether the effective goal contains two or more independent "
                    "primary learning goals that should not be completed as one session goal."
                ),
                criteria={
                    "true": "Two or more independent primary goals",
                    "false": "One primary goal, even if it includes subordinate steps",
                },
            ),
            "has_unclear_term": Noul(
                instructions=(
                    "Determine whether an abbreviation, typo, slang expression, or ambiguous "
                    "term prevents confident interpretation of the effective learning goal."
                ),
                criteria={
                    "true": "A term must be confirmed with the user before interpretation",
                    "false": "The meaning can be interpreted confidently without guessing",
                },
            ),
            "specificity_level": Score(
                instructions=(
                    "Rate how specific and actionable the effective learning goal is, using "
                    "clarification_answers as context."
                ),
                criteria=[
                    "Not interpretable or unusable as a learning goal",
                    "Missing essential topic or activity information",
                    "Understandable but broad and should be narrowed",
                    "A clear, single, actionable learning goal",
                ],
            ),
        }
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
            raise ModelConfigurationError("JEV 호출 설정이 올바르지 않습니다.") from exc
        except TypeSafeError as exc:
            raise ModelUnavailableError("JEV 판단 서비스를 사용할 수 없습니다.") from exc

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
            raise ModelConfigurationError("JEV 호출 설정이 올바르지 않습니다.") from exc
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

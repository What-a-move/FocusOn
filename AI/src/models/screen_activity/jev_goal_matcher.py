"""Parallel JEV goal-matching client replacing the static topic bank."""
from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any, Protocol, Sequence

from typesafe_sdk import (
    AsyncTypeSafeClient,
    Noul,
    RetryPolicy,
    TypeSafeAPIResponseValidationError,
    TypeSafeAuthenticationError,
    TypeSafeBadRequestError,
    TypeSafeError,
    TypeSafePermissionDeniedError,
    TypeSafeUnprocessableEntityError,
)

from ...config import Settings
from ..errors import (
    InvalidModelResponseError,
    ModelConfigurationError,
    ModelUnavailableError,
)

DEFAULT_JEV_MATCH_TIMEOUT_SECONDS = 3.0
DEFAULT_JEV_MAX_BODY_CHARS = 2_000


@dataclass(frozen=True, slots=True)
class JevGoalMatchResult:
    """Typed System-1 probabilities for each session goal and off-target study."""

    goal_scores: dict[str, float]
    different_study_topic: float


class GoalMatchJudge(Protocol):
    async def judge(
        self,
        *,
        title: str,
        body: str,
        content_source: str,
        goals: Sequence[Any],
    ) -> JevGoalMatchResult: ...

    async def aclose(self) -> None: ...


def build_goal_match_state(
    *,
    title: str,
    body: str,
    content_source: str,
    goals: Sequence[Any],
    max_body_chars: int = DEFAULT_JEV_MAX_BODY_CHARS,
) -> dict[str, Any]:
    """Build sanitized JEV state containing screen title/body and all session goals."""
    goal_items = []
    for goal in goals:
        profile = getattr(goal, "profile", None)
        goal_items.append(
            {
                "goal_id": goal.goal_id,
                "text": goal.text,
                "main_topic": profile.main_topic if profile is not None else "",
                "core_topics": list(profile.core_topics) if profile is not None else [],
            }
        )
    return {
        "screen": {
            "title": title,
            "body": body[:max_body_chars],
            "content_source": content_source,
        },
        "goals": goal_items,
    }


def build_goal_match_questions(goals: Sequence[Any]) -> dict[str, Noul]:
    """Create per-goal direct relevance questions plus one off-target study question."""
    questions: dict[str, Noul] = {}
    for goal in goals:
        questions[f"match_{goal.goal_id}"] = Noul(
            instructions=(
                f"Determine whether the screen content directly studies or practices the goal "
                f"'{goal.text}' (goal_id={goal.goal_id})."
            ),
            criteria={
                "true": (
                    "The screen directly addresses the specific topic, concept, or operation "
                    "of this goal."
                ),
                "false": (
                    "The screen is about a different subtopic, a neighbouring technology in the "
                    "same broad domain, or lacks sufficient context."
                ),
            },
        )
    questions["is_different_study_topic"] = Noul(
        instructions=(
            "Determine whether the screen is genuine educational or technical study material "
            "for a clearly different technology, subject, or domain from all listed goals "
            "(not an adjacent or related subtopic within the same technology or subject)."
        ),
        criteria={
            "true": (
                "The screen clearly studies a different technology, framework, or academic subject "
                "unrelated to any listed goal (e.g., Django or Git while the goal is React)."
            ),
            "false": (
                "The screen matches a listed goal, covers an adjacent or sibling subtopic within "
                "the same technology or subject (e.g., React props for a React hooks goal, Git stash "
                "for a Git branch goal, positional encoding for a multi-head attention goal), "
                "is not study material, or lacks enough context."
            ),
        },
    )
    return questions


class TypeSafeJevGoalMatcher:
    """Async JEV matcher with 3s timeout and a single retry on transient failure."""

    def __init__(
        self,
        settings: Settings,
        *,
        timeout_seconds: float = DEFAULT_JEV_MATCH_TIMEOUT_SECONDS,
        max_retries: int = 1,
    ) -> None:
        self._timeout_seconds = timeout_seconds
        self._max_retries = max_retries
        api_key = settings.typesafe_api_key
        if api_key is None:
            self._client = None
        else:
            self._client = AsyncTypeSafeClient(
                api_key=api_key.get_secret_value(),
                model=settings.typesafe_default_model,
                retry=RetryPolicy(max_retries=0, timeout=timeout_seconds),
                timeout=timeout_seconds,
            )

    async def judge(
        self,
        *,
        title: str,
        body: str,
        content_source: str,
        goals: Sequence[Any],
    ) -> JevGoalMatchResult:
        if self._client is None:
            raise ModelConfigurationError("TypeSafe API 설정을 사용할 수 없습니다.")
        if not goals:
            raise ValueError("GOALS_REQUIRED")

        state = build_goal_match_state(
            title=title,
            body=body,
            content_source=content_source,
            goals=goals,
        )
        questions = build_goal_match_questions(goals)

        last_exc: Exception | None = None
        for attempt in range(self._max_retries + 1):
            try:
                response = await asyncio.wait_for(
                    self._client.system_one(state=state, questions=questions),
                    timeout=self._timeout_seconds,
                )
                answers = response.answers
                goal_scores = {
                    goal.goal_id: float(answers[f"match_{goal.goal_id}"].noul)
                    for goal in goals
                }
                different_study = float(answers["is_different_study_topic"].noul)
                return JevGoalMatchResult(
                    goal_scores=goal_scores,
                    different_study_topic=different_study,
                )
            except (
                TypeSafeAuthenticationError,
                TypeSafePermissionDeniedError,
                TypeSafeBadRequestError,
                TypeSafeUnprocessableEntityError,
            ) as exc:
                raise ModelConfigurationError(
                    "TypeSafe API Key 또는 JEV 모델 설정이 올바르지 않습니다."
                ) from exc
            except (KeyError, TypeError, ValueError, AttributeError, TypeSafeAPIResponseValidationError) as exc:
                last_exc = InvalidModelResponseError("JEV 응답 형식이 올바르지 않습니다.")
                if attempt >= self._max_retries:
                    raise last_exc from exc
            except (TimeoutError, asyncio.TimeoutError, TypeSafeError) as exc:
                last_exc = ModelUnavailableError("JEV 호출이 시간 초과되었거나 일시적으로 실패했습니다.")
                if attempt >= self._max_retries:
                    raise last_exc from exc

        assert last_exc is not None
        raise last_exc

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.aclose()

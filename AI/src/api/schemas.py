from __future__ import annotations

from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class ClarityStatus(StrEnum):
    CLEAR = "CLEAR"
    NEEDS_SELECTION = "NEEDS_SELECTION"
    NEEDS_SUGGESTION = "NEEDS_SUGGESTION"
    NEEDS_QUESTION = "NEEDS_QUESTION"
    UNRECOGNIZED_TERM = "UNRECOGNIZED_TERM"
    INVALID = "INVALID"


class GoalPurpose(StrEnum):
    CONCEPT_LEARNING = "CONCEPT_LEARNING"
    IMPLEMENTATION = "IMPLEMENTATION"
    PROBLEM_SOLVING = "PROBLEM_SOLVING"
    DEBUGGING = "DEBUGGING"
    PRACTICE = "PRACTICE"
    PRESENTATION_OR_ASSIGNMENT = "PRESENTATION_OR_ASSIGNMENT"
    OTHER = "OTHER"


class ProfileSource(StrEnum):
    AI_ASSISTED = "AI_ASSISTED"
    DIRECT_INPUT = "DIRECT_INPUT"


class ClarificationAnswer(CamelModel):
    question_id: str = Field(min_length=1, max_length=100)
    question: str = Field(min_length=1)
    answer: str = Field(min_length=1)


class GoalClarifyRequest(CamelModel):
    request_id: str = Field(min_length=1, max_length=100)
    original_text: str = Field(min_length=1)
    selected_goal_text: str | None = None
    clarification_answers: list[ClarificationAnswer] = Field(default_factory=list)

    @field_validator("original_text", "selected_goal_text")
    @classmethod
    def reject_blank_text(cls, value: str | None) -> str | None:
        if value is not None and not value.strip():
            raise ValueError("공백만 입력할 수 없습니다.")
        return value


class RecommendedGoal(CamelModel):
    id: str = Field(min_length=1, max_length=100)
    title: str = Field(min_length=1)
    reason: str = Field(min_length=1)


class RecommendedGoalBatch(CamelModel):
    goals: list[RecommendedGoal] = Field(min_length=2, max_length=3)

    @field_validator("goals")
    @classmethod
    def require_unique_titles(cls, goals: list[RecommendedGoal]) -> list[RecommendedGoal]:
        titles = {goal.title.casefold() for goal in goals}
        if len(titles) != len(goals):
            raise ValueError("추천 목표 제목은 서로 달라야 합니다.")
        return goals


class GoalQuestionOption(CamelModel):
    id: str = Field(min_length=1, max_length=100)
    label: str = Field(min_length=1)


class ClarificationQuestion(CamelModel):
    id: str = Field(min_length=1, max_length=100)
    text: str = Field(min_length=1)
    options: list[GoalQuestionOption] = Field(min_length=2, max_length=3)
    allow_custom_answer: bool = True

    @field_validator("options")
    @classmethod
    def require_unique_options(
        cls, options: list[GoalQuestionOption]
    ) -> list[GoalQuestionOption]:
        if len({option.id.casefold() for option in options}) != len(options):
            raise ValueError("질문 선택지 ID는 서로 달라야 합니다.")
        if len({option.label.casefold() for option in options}) != len(options):
            raise ValueError("질문 선택지는 서로 달라야 합니다.")
        return options


class GoalProfileDraft(CamelModel):
    original_text: str = Field(min_length=1)
    interpreted_goal: str = Field(min_length=1)
    main_topic: str = Field(min_length=1)
    purpose: GoalPurpose
    core_topics: list[str] = Field(default_factory=list, max_length=5)
    supporting_topics: list[str] = Field(default_factory=list, max_length=5)
    expected_activities: list[str] = Field(default_factory=list, max_length=5)
    profile_source: ProfileSource = ProfileSource.AI_ASSISTED

    @field_validator("core_topics", "supporting_topics", "expected_activities")
    @classmethod
    def require_unique_values(cls, values: list[str]) -> list[str]:
        if len({value.casefold() for value in values}) != len(values):
            raise ValueError("GoalProfile 배열 값은 서로 달라야 합니다.")
        return values


class GoalClarifyData(CamelModel):
    clarity_status: ClarityStatus
    interpreted_goal: str | None = None
    recommended_goals: list[RecommendedGoal] = Field(default_factory=list, max_length=3)
    question: ClarificationQuestion | None = None
    goal_profile_draft: GoalProfileDraft | None = None
    invalid_reason: str | None = None
    requires_user_confirmation: bool = True
    fallback_allowed: bool = True


class GoalClarifyResponse(CamelModel):
    data: GoalClarifyData


class ErrorResponse(CamelModel):
    code: str
    message: str
    retryable: bool
    retry_after_seconds: int | None = None
    request_id: str | None = None
    details: dict[str, Any] = Field(default_factory=dict)

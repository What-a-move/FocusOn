from __future__ import annotations

from collections import deque
from typing import Any

from src.analysis.goal_analyzer import GoalAssessment, GeneratedResultVerification
from src.api.schemas import (
    ClarityStatus,
    ClarificationQuestion,
    GoalClarifyRequest,
    GoalProfileDraft,
    GoalPurpose,
    GoalQuestionOption,
    ProfileSource,
    RecommendedGoal,
    RecommendedGoalBatch,
)


class FakeJevClient:
    """Deterministic test double; it never performs network I/O."""

    def __init__(
        self,
        assessments: list[GoalAssessment],
        verifications: list[GeneratedResultVerification] | None = None,
    ) -> None:
        self.assessments = deque(assessments)
        self.verifications = deque(verifications or [])
        self.assess_calls = 0
        self.verify_calls = 0
        self.assessed_requests: list[GoalClarifyRequest] = []

    async def assess_goal(self, request: GoalClarifyRequest) -> GoalAssessment:
        self.assess_calls += 1
        self.assessed_requests.append(request)
        return self.assessments.popleft()

    async def verify_generated_result(
        self,
        request: GoalClarifyRequest,
        generated_result: dict[str, Any],
        result_kind: str,
    ) -> GeneratedResultVerification:
        self.verify_calls += 1
        return self.verifications.popleft()

    async def aclose(self) -> None:
        return None


class FakeGoalGenerationClient:
    """Safe default content used only by tests and local dependency overrides."""

    def __init__(self) -> None:
        self.repair_calls = 0

    async def generate_candidates(
        self, request: GoalClarifyRequest, status: ClarityStatus
    ) -> RecommendedGoalBatch:
        return RecommendedGoalBatch(
            goals=[
                RecommendedGoal(id="cache-concept", title="React Query 캐싱 원리 학습", reason="캐시 동작에 집중합니다."),
                RecommendedGoal(id="cache-implementation", title="React Query 캐시 갱신 구현", reason="구현 활동에 집중합니다."),
            ]
        )

    async def generate_question(
        self, request: GoalClarifyRequest, status: ClarityStatus
    ) -> ClarificationQuestion:
        return ClarificationQuestion(
            id="goal_detail",
            text="어떤 결과를 만드는 데 집중할까요?",
            options=[
                GoalQuestionOption(id="concept", label="개념 이해"),
                GoalQuestionOption(id="implementation", label="직접 구현"),
            ],
            allow_custom_answer=True,
        )

    async def generate_profile(self, request: GoalClarifyRequest) -> GoalProfileDraft:
        interpreted_goal = request.selected_goal_text or request.original_text
        if request.clarification_answers:
            interpreted_goal = (
                f"{interpreted_goal} - {request.clarification_answers[-1].answer}"
            )
        return GoalProfileDraft(
            original_text=request.original_text,
            interpreted_goal=interpreted_goal,
            main_topic=request.selected_goal_text or request.original_text,
            purpose=GoalPurpose.CONCEPT_LEARNING,
            core_topics=["핵심 원리"],
            supporting_topics=[],
            expected_activities=["개념 정리"],
            profile_source=ProfileSource.AI_ASSISTED,
        )

    async def repair_candidate(
        self,
        request: GoalClarifyRequest,
        candidate: RecommendedGoal,
        failed_checks: list[str],
    ) -> RecommendedGoal:
        self.repair_calls += 1
        return candidate

    async def repair_profile(
        self,
        request: GoalClarifyRequest,
        profile: GoalProfileDraft,
        failed_checks: list[str],
    ) -> GoalProfileDraft:
        self.repair_calls += 1
        return profile

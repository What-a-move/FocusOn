import asyncio

import pytest

from src.analysis.goal_analyzer import (
    GoalAssessment,
    GeneratedResultVerification,
)
from src.api.schemas import ClarityStatus, ClarificationAnswer, GoalClarifyRequest
from src.config import Settings
from src.models.fakes import FakeGoalGenerationClient, FakeJevClient
from src.workflow.goal_assistance import GoalAssistanceWorkflow


PASS = GeneratedResultVerification(0.95, 0.95, 0.95)
FAIL = GeneratedResultVerification(0.4, 0.95, 0.95)


def req(**kwargs) -> GoalClarifyRequest:
    values = {"requestId": "req-test", "originalText": "React Query 학습"}
    values.update(kwargs)
    return GoalClarifyRequest.model_validate(values)


def score(*, usable=0.9, multiple=0.1, unclear=0.1, specificity=3.0, confidence=0.9):
    return GoalAssessment(usable, multiple, unclear, specificity, confidence)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("assessment", "expected"),
    [
        (score(specificity=3.0), ClarityStatus.CLEAR),
        (score(multiple=0.9), ClarityStatus.NEEDS_SELECTION),
        (score(specificity=1.5), ClarityStatus.NEEDS_SUGGESTION),
        (score(specificity=0.9), ClarityStatus.NEEDS_QUESTION),
        (score(unclear=0.9), ClarityStatus.UNRECOGNIZED_TERM),
        (score(usable=0.1), ClarityStatus.INVALID),
    ],
)
async def test_routable_statuses_reach_expected_response(assessment, expected):
    verification_count = 1 if expected is ClarityStatus.CLEAR else 2 if expected in {
        ClarityStatus.NEEDS_SELECTION, ClarityStatus.NEEDS_SUGGESTION
    } else 0
    jev = FakeJevClient([assessment], [PASS] * verification_count)
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    result = await workflow.run(req())

    assert result.clarity_status is expected
    assert result.requires_user_confirmation is True
    assert jev.verify_calls == verification_count


@pytest.mark.asyncio
@pytest.mark.parametrize("assessment", [score(), score(usable=0.1), score(unclear=0.9)])
async def test_session_id_survives_all_response_paths(assessment):
    workflow = GoalAssistanceWorkflow(
        Settings(), FakeJevClient([assessment], [PASS]), FakeGoalGenerationClient()
    )
    result = await workflow.run(req(clarificationSessionId="test-dialog"))
    assert result.clarification_session_id == "test-dialog"


@pytest.mark.asyncio
@pytest.mark.parametrize("repair_fallback", [False, True])
async def test_two_answers_never_generate_a_third_question(repair_fallback):
    generator = FakeGoalGenerationClient()
    original = generator.generate_question
    from unittest.mock import AsyncMock
    generator.generate_question = AsyncMock(wraps=original)
    jev = FakeJevClient(
        [score() if repair_fallback else score(unclear=0.9)],
        [FAIL, FAIL] if repair_fallback else [],
    )
    result = await GoalAssistanceWorkflow(Settings(), jev, generator).run(req(
        conversationTurn=2,
        previousAnswers=[{"question": "첫 질문", "answer": "첫 답변"}, {"question": "둘째 질문", "answer": "둘째 답변"}],
    ))
    assert result.question is None
    assert result.requires_user_confirmation is True
    assert result.fallback_allowed is True
    generator.generate_question.assert_not_called()


@pytest.mark.asyncio
async def test_failed_generation_is_repaired_once_then_falls_back_to_question():
    jev = FakeJevClient([score()], [FAIL, FAIL])
    generator = FakeGoalGenerationClient()
    workflow = GoalAssistanceWorkflow(Settings(), jev, generator)

    result = await workflow.run(req())

    assert result.clarity_status is ClarityStatus.NEEDS_QUESTION
    assert result.question is not None
    assert generator.repair_calls == 1
    assert jev.verify_calls == 2


@pytest.mark.asyncio
async def test_candidate_selection_and_answer_start_full_assessment_again():
    jev = FakeJevClient(
        [score(multiple=0.9), score(specificity=3)],
        [PASS, PASS, PASS],
    )
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    first = await workflow.run(req())
    second = await workflow.run(
        req(
            selectedGoalText=first.recommended_goals[0].title,
            clarificationAnswers=[
                {"questionId": "kind", "question": "어떤 방식인가요?", "answer": "개념 이해"}
            ],
        )
    )

    assert second.clarity_status is ClarityStatus.CLEAR
    assert jev.assess_calls == 2
    assert jev.assessed_requests[1].selected_goal_text is not None
    assert jev.assessed_requests[1].clarification_answers == [
        ClarificationAnswer(
            question_id="kind", question="어떤 방식인가요?", answer="개념 이해"
        )
    ]


@pytest.mark.asyncio
async def test_answer_is_reflected_in_the_final_interpreted_goal():
    jev = FakeJevClient(
        [
            score(specificity=0.9),
            score(specificity=3.0),
        ],
        [PASS],
    )
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    first = await workflow.run(req(originalText="상태 관리"))
    second = await workflow.run(
        req(
            originalText="상태 관리",
            clarificationAnswers=[
                {
                    "questionId": first.question.id,
                    "question": first.question.text,
                    "answer": "React에서 Context API로 전역 상태 구현",
                }
            ],
        )
    )

    assert first.clarity_status is ClarityStatus.NEEDS_QUESTION
    assert second.clarity_status is ClarityStatus.CLEAR
    assert "React에서 Context API로 전역 상태 구현" in second.interpreted_goal


@pytest.mark.asyncio
async def test_user_can_start_the_original_broad_goal_after_explicit_selection():
    jev = FakeJevClient(
        [score(specificity=1.5), score(specificity=1.5)],
        [PASS, PASS, PASS],
    )
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    first = await workflow.run(req(originalText="상태 관리 공부하기"))
    second = await workflow.run(
        req(
            originalText="상태 관리 공부하기",
            selectedGoalText="상태 관리 공부하기",
        )
    )

    assert first.clarity_status is ClarityStatus.NEEDS_SUGGESTION
    assert second.clarity_status is ClarityStatus.CLEAR
    assert second.interpreted_goal == "상태 관리 공부하기"


@pytest.mark.asyncio
async def test_follow_up_uses_displayed_jev_assessment_without_reassessing():
    jev = FakeJevClient([score(multiple=0.9)], [PASS, PASS])
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    assessment, clarity_status = await workflow.assess(req())
    result = await workflow.run_after_assessment(req(), assessment, clarity_status)

    assert result.clarity_status is ClarityStatus.NEEDS_SELECTION
    assert jev.assess_calls == 1
    assert jev.verify_calls == 2


@pytest.mark.asyncio
async def test_candidate_results_are_verified_independently():
    jev = FakeJevClient([score(multiple=0.9)], [PASS, PASS])
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    result = await workflow.run(req())

    assert len(result.recommended_goals) == 2
    assert jev.verify_calls == 2


@pytest.mark.asyncio
async def test_candidate_results_are_verified_in_parallel():
    class ParallelProbeJev(FakeJevClient):
        def __init__(self):
            super().__init__([score(multiple=0.9)], [PASS, PASS])
            self.in_flight = 0
            self.max_in_flight = 0

        async def verify_generated_result(self, *args, **kwargs):
            self.in_flight += 1
            self.max_in_flight = max(self.max_in_flight, self.in_flight)
            await asyncio.sleep(0.01)
            try:
                return await super().verify_generated_result(*args, **kwargs)
            finally:
                self.in_flight -= 1

    jev = ParallelProbeJev()
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    await workflow.run(req())

    assert jev.max_in_flight == 2


@pytest.mark.asyncio
async def test_only_failed_candidate_is_repaired_once():
    jev = FakeJevClient(
        [score(multiple=0.9)],
        [FAIL, PASS, PASS, PASS],
    )
    generator = FakeGoalGenerationClient()
    workflow = GoalAssistanceWorkflow(Settings(), jev, generator)

    result = await workflow.run(req())

    assert result.clarity_status is ClarityStatus.NEEDS_SELECTION
    assert generator.repair_calls == 1
    assert jev.verify_calls == 4


@pytest.mark.asyncio
async def test_failed_candidates_are_repaired_in_parallel():
    class ParallelProbeGenerator(FakeGoalGenerationClient):
        def __init__(self):
            super().__init__()
            self.in_flight = 0
            self.max_in_flight = 0

        async def repair_candidate(self, request, candidate, failed_checks):
            self.in_flight += 1
            self.max_in_flight = max(self.max_in_flight, self.in_flight)
            await asyncio.sleep(0.01)
            try:
                return await super().repair_candidate(
                    request, candidate, failed_checks
                )
            finally:
                self.in_flight -= 1

    jev = FakeJevClient(
        [score(multiple=0.9)], [FAIL, FAIL, PASS, PASS]
    )
    generator = ParallelProbeGenerator()
    workflow = GoalAssistanceWorkflow(Settings(), jev, generator)

    result = await workflow.run(req())

    assert result.clarity_status is ClarityStatus.NEEDS_SELECTION
    assert generator.repair_calls == 2
    assert generator.max_in_flight == 2

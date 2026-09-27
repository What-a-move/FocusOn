import pytest

from src.analysis.goal_analyzer import GoalAssessment, GeneratedResultVerification
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
        (score(specificity=3), ClarityStatus.CLEAR),
        (score(multiple=0.9), ClarityStatus.NEEDS_SELECTION),
        (score(specificity=2), ClarityStatus.NEEDS_SUGGESTION),
        (score(specificity=1), ClarityStatus.NEEDS_QUESTION),
        (score(unclear=0.9), ClarityStatus.UNRECOGNIZED_TERM),
        (score(usable=0.1), ClarityStatus.INVALID),
    ],
)
async def test_six_statuses_reach_expected_response(assessment, expected):
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
async def test_candidate_results_are_verified_independently():
    jev = FakeJevClient([score(multiple=0.9)], [PASS, PASS])
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    result = await workflow.run(req())

    assert len(result.recommended_goals) == 2
    assert jev.verify_calls == 2


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

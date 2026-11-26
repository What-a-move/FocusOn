import asyncio

import pytest

from src.analysis.goal_analyzer import (
    GoalAssessment,
    GeneratedResultVerification,
)
from src.api.schemas import (
    ClarityStatus,
    ClarificationAnswer,
    GoalClarifyRequest,
    RecommendedGoal,
    RecommendedGoalBatch,
)
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
    jev = FakeJevClient([assessment], [])
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    result = await workflow.run(req())

    assert result.clarity_status is expected
    assert result.requires_user_confirmation is True
    assert jev.verify_calls == 0


@pytest.mark.asyncio
@pytest.mark.parametrize("assessment", [score(), score(usable=0.1), score(unclear=0.9)])
async def test_session_id_survives_all_response_paths(assessment):
    workflow = GoalAssistanceWorkflow(
        Settings(), FakeJevClient([assessment], []), FakeGoalGenerationClient()
    )
    result = await workflow.run(req(clarificationSessionId="test-dialog"))
    assert result.clarification_session_id == "test-dialog"


@pytest.mark.asyncio
@pytest.mark.parametrize("candidate_fallback", [False, True])
async def test_two_answers_never_generate_a_third_question(candidate_fallback):
    from unittest.mock import AsyncMock

    class DuplicateCandidateGenerator(FakeGoalGenerationClient):
        async def generate_candidates(self, request, status):
            return RecommendedGoalBatch.model_construct(
                goals=[
                    RecommendedGoal(id="a", title="자료구조 스택 정리", reason="스택에 집중해요."),
                    RecommendedGoal(id="b", title="자료구조  스택 정리", reason="중복 제목"),
                ]
            )

    generator = DuplicateCandidateGenerator() if candidate_fallback else FakeGoalGenerationClient()
    generator.generate_question = AsyncMock(wraps=generator.generate_question)
    jev = FakeJevClient(
        [score(specificity=1.5) if candidate_fallback else score(unclear=0.9)],
        [],
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
async def test_clear_goal_skips_post_verification_and_never_demotes_to_question():
    jev = FakeJevClient([score(specificity=3.0)], [])
    generator = FakeGoalGenerationClient()
    workflow = GoalAssistanceWorkflow(Settings(), jev, generator)

    result = await workflow.run(req(originalText="리액트 useEffect 의존성 배열 정리하기"))

    assert result.clarity_status is ClarityStatus.CLEAR
    assert result.goal_profile_draft is not None
    assert result.interpreted_goal == "리액트 useEffect 의존성 배열 정리하기"
    assert jev.verify_calls == 0
    assert generator.profile_calls == 1
    assert generator.repair_calls == 0


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("assessment", "expected", "goal_text"),
    [
        (score(specificity=1.5), ClarityStatus.NEEDS_SUGGESTION, "자료구조 공부하기"),
        (score(multiple=0.9), ClarityStatus.NEEDS_SELECTION, "알고리즘이랑 OS 공부하기"),
    ],
)
async def test_candidate_paths_skip_jev_post_verification_and_return_candidates(
    assessment, expected, goal_text
):
    jev = FakeJevClient([assessment], [])
    generator = FakeGoalGenerationClient()
    workflow = GoalAssistanceWorkflow(Settings(), jev, generator)

    result = await workflow.run(req(originalText=goal_text))

    assert result.clarity_status is expected
    assert len(result.recommended_goals) == 2
    assert jev.assess_calls == 1
    assert jev.verify_calls == 0
    assert generator.repair_calls == 0


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
    jev = FakeJevClient([score(multiple=0.9)], [])
    workflow = GoalAssistanceWorkflow(Settings(), jev, FakeGoalGenerationClient())

    assessment, clarity_status = await workflow.assess(req())
    result = await workflow.run_after_assessment(req(), assessment, clarity_status)

    assert result.clarity_status is ClarityStatus.NEEDS_SELECTION
    assert jev.assess_calls == 1
    assert jev.verify_calls == 0


@pytest.mark.asyncio
async def test_candidate_deduplication_keeps_two_unique_candidates_without_500():
    class CollidingThreeGenerator(FakeGoalGenerationClient):
        async def generate_candidates(self, request, status):
            return RecommendedGoalBatch.model_construct(
                goals=[
                    RecommendedGoal(id="g1", title="자료구조 스택 개념 정리하기", reason="스택에 집중해요."),
                    RecommendedGoal(id="g2", title="자료구조 큐와 덱 구현하기", reason="큐에 집중해요."),
                    RecommendedGoal(id="g3", title="자료구조  큐와 덱 구현하기", reason="공백 차이 중복"),
                ]
            )

    jev = FakeJevClient([score(specificity=1.5)], [])
    generator = CollidingThreeGenerator()
    workflow = GoalAssistanceWorkflow(Settings(), jev, generator)

    result = await workflow.run(req(originalText="자료구조 공부하기"))

    assert result.clarity_status is ClarityStatus.NEEDS_SUGGESTION
    assert [goal.title for goal in result.recommended_goals] == [
        "자료구조 스택 개념 정리하기",
        "자료구조 큐와 덱 구현하기",
    ]
    assert jev.verify_calls == 0
    assert generator.repair_calls == 0


@pytest.mark.asyncio
async def test_duplicate_candidates_below_two_fall_back_to_question_without_500():
    class DuplicateTwoGenerator(FakeGoalGenerationClient):
        async def generate_candidates(self, request, status):
            return RecommendedGoalBatch.model_construct(
                goals=[
                    RecommendedGoal(id="g1", title="자료구조 스택 정리", reason="스택에 집중해요."),
                    RecommendedGoal(id="g2", title="자료구조  스택 정리", reason="중복 제목"),
                ]
            )

    jev = FakeJevClient([score(specificity=1.5)], [])
    generator = DuplicateTwoGenerator()
    workflow = GoalAssistanceWorkflow(Settings(), jev, generator)

    result = await workflow.run(req(originalText="자료구조 공부하기"))

    assert result.clarity_status is ClarityStatus.NEEDS_QUESTION
    assert result.question is not None
    assert jev.verify_calls == 0
    assert generator.repair_calls == 0

import json
from types import SimpleNamespace

import pytest

from src.api.schemas import ClarityStatus, GoalClarifyRequest, RecommendedGoal
from src.analysis.goal_assessment_contract import (
    build_goal_assessment_question_specs,
    build_goal_assessment_state,
)
from src.config import Settings
from src.models.jev_client import TypeSafeJevClient
from src.models.laya_client import LayaComparisonClient
from src.models.llm_client import OpenAIGoalGenerationClient
from src.prompts.goal_prompt import candidate_prompt, profile_prompt


class CapturingSystemOne:
    def __init__(self, response):
        self.response = response
        self.calls = []

    async def system_one(self, **kwargs):
        self.calls.append(kwargs)
        return self.response


def noul(value):
    return SimpleNamespace(noul=value)


def test_direct_provider_keys_configure_official_clients():
    settings = Settings(
        _env_file=None,
        typesafe_api_key="direct-typesafe-test-key",
        openai_api_key="direct-openai-test-key",
    )

    jev = TypeSafeJevClient(settings)
    generator = OpenAIGoalGenerationClient(settings)

    assert jev._client is not None
    assert jev._client._config.base_url == "https://api.typesafe.ai"
    assert jev._client._config.default_model == "jev-latest"
    assert jev._client._config.api_key == "direct-typesafe-test-key"
    assert generator._llm is not None
    assert generator._llm.model_name == "gpt-5-mini"
    assert generator._llm.openai_api_base is None
    assert generator._llm.reasoning_effort == "minimal"
    assert generator._llm.verbosity == "low"
    assert generator._llm.max_tokens == 600


@pytest.mark.asyncio
async def test_initial_jev_assessment_sends_four_typed_questions_in_one_call():
    response = SimpleNamespace(
        answers={
            "is_usable_goal": noul(0.9),
            "has_multiple_main_goals": noul(0.1),
            "has_unclear_term": noul(0.2),
            "specificity_level": SimpleNamespace(score=2, confidence=0.8),
        },
    )
    transport = CapturingSystemOne(response)
    client = TypeSafeJevClient.__new__(TypeSafeJevClient)
    client._client = transport

    result = await client.assess_goal(
        GoalClarifyRequest(request_id="req", original_text="rq 캐싱")
    )

    assert result.is_usable_goal == 0.9
    assert len(transport.calls) == 1
    request = GoalClarifyRequest(request_id="req", original_text="rq 캐싱")
    assert transport.calls[0]["state"] == build_goal_assessment_state(request)
    assert set(transport.calls[0]["questions"]) == {
        "is_usable_goal", "has_multiple_main_goals", "has_unclear_term", "specificity_level"
    }
    unclear_term_question = transport.calls[0]["questions"]["has_unclear_term"]
    assert "abbreviation, typo, slang" in unclear_term_question.instructions
    specificity_question = transport.calls[0]["questions"]["specificity_level"]
    assert "specific and actionable" in specificity_question.instructions
    assert "Missing essential topic" in specificity_question.criteria[1]
    usable_question = transport.calls[0]["questions"]["is_usable_goal"]
    assert "meaningfully perform or study" in usable_question.instructions
    assert "A learnable topic" in usable_question.criteria["true"]


def test_laya_uses_the_same_state_and_question_contract_as_jev(monkeypatch):
    class Agent:
        def __init__(self):
            self.state = None
            self.questions = None

        def predict(self, state, questions):
            self.state = state
            self.questions = questions
            return {
                "answers": {
                    "is_usable_goal": {"noul": 0.91},
                    "has_multiple_main_goals": {"noul": 0.08},
                    "has_unclear_term": {"noul": 0.05},
                    "specificity_level": {"score": 2.0, "confidence": 0.72},
                }
            }

    agent = Agent()
    monkeypatch.setattr(
        LayaComparisonClient,
        "_load_agent",
        staticmethod(lambda _model: agent),
    )
    request = GoalClarifyRequest(request_id="req-laya", original_text="상태 관리 공부")

    result = LayaComparisonClient(Settings()).assess_goal(request)

    assert agent.state == build_goal_assessment_state(request)
    assert agent.questions == build_goal_assessment_question_specs()
    assert result.specificity_confidence == 0.72


@pytest.mark.asyncio
async def test_generated_result_verification_uses_three_noul_questions_together():
    response = SimpleNamespace(
        answers={
            "preserves_user_intent": noul(0.9),
            "contains_one_goal": noul(0.8),
            "is_specific_enough": noul(0.7),
        }
    )
    transport = CapturingSystemOne(response)
    client = TypeSafeJevClient.__new__(TypeSafeJevClient)
    client._client = transport

    result = await client.verify_generated_result(
        GoalClarifyRequest(request_id="req", original_text="캐싱 학습"),
        {"title": "React Query 캐싱 학습"},
        "recommended_goal",
    )

    assert result.is_specific_enough == 0.7
    assert len(transport.calls) == 1
    assert set(transport.calls[0]["questions"]) == {
        "preserves_user_intent", "contains_one_goal", "is_specific_enough"
    }
    assert "preserve the user's intended learning topic" in transport.calls[0]["questions"]["preserves_user_intent"].instructions


def test_profile_prompt_requires_the_latest_answer_to_refine_the_goal():
    request = GoalClarifyRequest.model_validate(
        {
            "requestId": "req-answer",
            "originalText": "상태 관리",
            "clarificationAnswers": [
                {
                    "questionId": "scope",
                    "question": "어떤 상태 관리를 할까요?",
                    "answer": "React Context API로 전역 상태 구현",
                }
            ],
        }
    )

    payload = json.loads(profile_prompt(request))

    assert payload["context"]["clarificationAnswers"][0]["answer"] == (
        "React Context API로 전역 상태 구현"
    )
    assert any("latest clarification answer" in item for item in payload["requirements"])

    candidate_payload = json.loads(
        candidate_prompt(request, ClarityStatus.NEEDS_SUGGESTION)
    )
    assert any(
        "latest clarification answer" in item
        for item in candidate_payload["requirements"]
    )


@pytest.mark.asyncio
async def test_openai_schema_failure_is_repaired_at_most_once():
    class Structured:
        def __init__(self):
            self.calls = 0

        async def ainvoke(self, messages):
            self.calls += 1
            if self.calls == 1:
                return {"id": "missing-required-fields"}
            return {"id": "goal-1", "title": "캐싱 학습", "reason": "범위를 명확히 함"}

    class Llm:
        def __init__(self, structured):
            self.structured = structured

        def with_structured_output(self, schema, method):
            assert method == "json_schema"
            return self.structured

    structured = Structured()
    client = OpenAIGoalGenerationClient.__new__(OpenAIGoalGenerationClient)
    client._llm = Llm(structured)

    result = await client._generate(RecommendedGoal, "safe prompt")

    assert result.title == "캐싱 학습"
    assert structured.calls == 2

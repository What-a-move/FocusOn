from types import SimpleNamespace

import pytest

from src.api.schemas import GoalClarifyRequest
from src.config import Settings
from src.models.jev_client import TypeSafeJevClient
from src.models.llm_client import OpenAIGoalGenerationClient
from src.api.schemas import RecommendedGoal


class CapturingSystemOne:
    def __init__(self, response):
        self.response = response
        self.calls = []

    async def system_one(self, **kwargs):
        self.calls.append(kwargs)
        return self.response


def noul(value):
    return SimpleNamespace(noul=value)


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
    assert set(transport.calls[0]["questions"]) == {
        "is_usable_goal", "has_multiple_main_goals", "has_unclear_term", "specificity_level"
    }


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

import asyncio
import json
from types import SimpleNamespace

import pytest

from src.api.schemas import (
    ClarityStatus,
    ClarificationQuestion,
    GoalClarifyRequest,
    GoalQuestionOption,
    RecommendedGoal,
)
from src.analysis.goal_analyzer import GoalAssessment
from src.analysis.goal_assessment_contract import (
    build_goal_assessment_question_specs,
    build_goal_assessment_state,
)
from src.config import Settings
from src.models.jev_client import TypeSafeJevClient
from src.models.laya_client import LayaComparisonClient
from src.models.langsmith_tracing import (
    RAW_GOAL_TEXT_ENV,
    summarize_goal_request,
    trace_goal_assessment_outputs,
)
from src.models.errors import ModelUnavailableError
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


def test_langsmith_goal_trace_is_redacted_by_default(monkeypatch):
    monkeypatch.delenv(RAW_GOAL_TEXT_ENV, raising=False)
    request = GoalClarifyRequest(
        request_id="req-trace",
        original_text="React Query 캐시 무효화 구현하기",
    )

    trace_input = summarize_goal_request(request)

    assert trace_input["traceInputMode"] == "redacted"
    assert "originalText" not in trace_input


def test_langsmith_goal_trace_can_include_raw_local_test_input(monkeypatch):
    monkeypatch.setenv(RAW_GOAL_TEXT_ENV, "true")
    request = GoalClarifyRequest.model_validate(
        {
            "requestId": "req-trace",
            "originalText": "React Query 캐시 무효화 구현하기",
            "selectedGoalText": "React Query staleTime 학습",
            "clarificationAnswers": [
                {
                    "questionId": "scope",
                    "question": "무엇을 구현하나요?",
                    "answer": "invalidateQueries 호출 흐름",
                }
            ],
        }
    )

    trace_input = summarize_goal_request(request)

    assert trace_input["traceInputMode"] == "raw_goal_text"
    assert trace_input["originalText"] == request.original_text
    assert trace_input["selectedGoalText"] == request.selected_goal_text
    assert trace_input["clarificationAnswers"][0]["answer"] == "invalidateQueries 호출 흐름"


def test_langsmith_goal_trace_includes_the_router_status():
    trace_output = trace_goal_assessment_outputs(
        GoalAssessment(0.9, 0.1, 0.1, 2.0, 0.9)
    )

    assert trace_output["routerStatus"] == ClarityStatus.CLEAR


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


def test_rule_based_question_window_is_at_least_ten_seconds(monkeypatch):
    monkeypatch.delenv("OPENAI_QUESTION_DEADLINE_SECONDS", raising=False)
    assert Settings(_env_file=None).openai_question_deadline_seconds == 10
    with pytest.raises(ValueError):
        Settings(_env_file=None, openai_question_deadline_seconds=9.9)


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


def question(text: str) -> ClarificationQuestion:
    return ClarificationQuestion(
        id="scope",
        text=text,
        options=[
            GoalQuestionOption(id="concept", label="개념 학습"),
            GoalQuestionOption(id="practice", label="직접 구현"),
        ],
    )


@pytest.mark.asyncio
async def test_parallel_question_generation_returns_first_valid_result():
    client = OpenAIGoalGenerationClient.__new__(OpenAIGoalGenerationClient)
    client._llm = object()
    client._question_parallelism = 2
    client._question_deadline_seconds = 10.0
    calls = 0
    cancelled = []

    async def generate(_schema, _prompt):
        nonlocal calls
        calls += 1
        call_number = calls
        try:
            await asyncio.sleep(0.01 if call_number == 1 else 1)
            return question(f"질문 {call_number}")
        except asyncio.CancelledError:
            cancelled.append(call_number)
            raise

    client._generate = generate

    result = await client.generate_question(
        GoalClarifyRequest(request_id="req", original_text="React Query 학습"),
        ClarityStatus.NEEDS_QUESTION,
    )

    assert calls == 2
    assert result.text == "질문 1"
    assert cancelled == [2]


@pytest.mark.asyncio
async def test_parallel_question_generation_skips_previously_answered_question():
    client = OpenAIGoalGenerationClient.__new__(OpenAIGoalGenerationClient)
    client._llm = object()
    client._question_parallelism = 2
    client._question_deadline_seconds = 10.0
    calls = 0

    async def generate(_schema, _prompt):
        nonlocal calls
        calls += 1
        call_number = calls
        await asyncio.sleep(0.01 * call_number)
        return question(
            "무엇을 만들까요?" if call_number == 1 else "어떤 범위를 다룰까요?"
        )

    client._generate = generate
    request = GoalClarifyRequest.model_validate(
        {
            "requestId": "req",
            "originalText": "React Query 학습",
            "clarificationAnswers": [
                {
                    "questionId": "previous",
                    "question": "무엇을 만들까요?",
                    "answer": "캐시 무효화",
                }
            ],
        }
    )

    result = await client.generate_question(request, ClarityStatus.NEEDS_QUESTION)

    assert result.text == "어떤 범위를 다룰까요?"


@pytest.mark.asyncio
async def test_parallel_question_generation_falls_back_if_both_repeat_prior_question():
    client = OpenAIGoalGenerationClient.__new__(OpenAIGoalGenerationClient)
    client._llm = object()
    client._question_parallelism = 2
    client._question_deadline_seconds = 0.03

    async def generate(_schema, _prompt):
        return question("무엇을 만들까요?")

    client._generate = generate
    request = GoalClarifyRequest.model_validate(
        {
            "requestId": "req",
            "originalText": "React Query 학습",
            "clarificationAnswers": [
                {
                    "questionId": "previous",
                    "question": "무엇을 만들까요?",
                    "answer": "캐시 무효화",
                }
            ],
        }
    )

    started = asyncio.get_running_loop().time()
    result = await client.generate_question(request, ClarityStatus.NEEDS_QUESTION)

    assert result.id == "fallback-2"
    assert result.text != "무엇을 만들까요?"
    assert asyncio.get_running_loop().time() - started >= 0.03


@pytest.mark.asyncio
async def test_streamed_question_emits_text_before_validated_result():
    class Stream:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *_args):
            return None

        async def __aiter__(self):
            yield SimpleNamespace(
                type="response.output_text.delta",
                delta='{"id":"scope","text":"어떤',
            )
            yield SimpleNamespace(
                type="response.output_text.delta",
                delta=' 범위를 다룰까요?","options":[]}',
            )

        async def get_final_response(self):
            return SimpleNamespace(output_parsed=question("어떤 범위를 다룰까요?"))

    class Responses:
        def stream(self, **_kwargs):
            return Stream()

    client = OpenAIGoalGenerationClient.__new__(OpenAIGoalGenerationClient)
    client._response_client = SimpleNamespace(responses=Responses())
    client._openai_model = "gpt-5-mini"
    client._openai_reasoning_effort = "minimal"
    client._openai_max_completion_tokens = 600
    previews = []

    result = await client._stream_question("safe prompt", previews.append)

    assert previews == ["어떤", "어떤 범위를 다룰까요?"]
    assert result.text == "어떤 범위를 다룰까요?"


@pytest.mark.asyncio
async def test_question_returns_local_fallback_after_deadline():
    client = OpenAIGoalGenerationClient.__new__(OpenAIGoalGenerationClient)
    client._llm = object()
    client._question_parallelism = 2
    client._question_deadline_seconds = 0.02

    async def slow_generate(_schema, _prompt):
        await asyncio.sleep(1)
        return question("늦은 질문")

    client._generate = slow_generate
    result = await client.generate_question(
        GoalClarifyRequest(request_id="req", original_text="React Query 학습"),
        ClarityStatus.NEEDS_QUESTION,
    )

    assert result.id == "fallback-1"
    assert result.allow_custom_answer is True
    assert len(result.options) == 2


@pytest.mark.asyncio
async def test_question_returns_local_fallback_when_provider_is_unavailable():
    client = OpenAIGoalGenerationClient.__new__(OpenAIGoalGenerationClient)
    client._llm = object()
    client._question_parallelism = 2
    client._question_deadline_seconds = 0.03

    async def unavailable_generate(_schema, _prompt):
        raise ModelUnavailableError("테스트 연결 실패")

    client._generate = unavailable_generate
    started = asyncio.get_running_loop().time()
    result = await client.generate_question(
        GoalClarifyRequest(request_id="req", original_text="React Query 학습"),
        ClarityStatus.NEEDS_QUESTION,
    )

    assert result.id == "fallback-1"
    assert asyncio.get_running_loop().time() - started >= 0.03

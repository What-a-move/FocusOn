
import json

import pytest

from src.api.schemas import ConfirmedGoal, SafeInputError
from src.analysis.goal_analyzer import (
    GoalGraphResolver,
    LLMGoalGraphGenerator,
    compile_minimal_goal_graph,
)
from src.analysis.goal_graph import ContentRole
from src.models.llm_client import GoalGraphLLMError
from src.prompts import goal_prompt


def arbitrary_goal(**changes):
    value = {
        "goalId": "goal-rust",
        "goalVersion": 1,
        "text": "Rust ownership과 borrowing 학습",
        "confirmed": True,
    }
    value.update(changes)
    return ConfirmedGoal.parse_safe(value)


class ScriptedGoalClient:
    fingerprint = "fake-goal-llm-v1"

    def __init__(self, response=None, error=None):
        self.response = response
        self.error = error
        self.calls = 0
        self.prompts = []

    def complete(self, prompt):
        self.calls += 1
        self.prompts.append(prompt)
        if self.error:
            raise self.error
        return json.dumps(self.response, ensure_ascii=False)


class MemoryGoalStore:
    def __init__(self):
        self.items = {}

    def load(self, key):
        return self.items.get(key)

    def save(self, key, graph):
        self.items[key] = graph

    def clear(self):
        self.items.clear()


def test_confirmed_arbitrary_goal_no_longer_requires_template_id():
    goal = arbitrary_goal()

    assert goal.template_id is None
    assert goal.profile is None


def test_minimal_graph_uses_only_confirmed_goal_scope():
    goal = arbitrary_goal(goalProfile={
        "mainTopic": "Rust 메모리 안전성",
        "purpose": "LEARNING",
        "coreTopics": ["ownership", "borrowing"],
        "supportingTopics": ["lifetime"],
        "expectedActivities": ["컴파일 오류 확인"],
    })

    graph = compile_minimal_goal_graph(goal)

    assert graph.template_id == "generated:goal-rust:v1"
    assert [concept.role for concept in graph.concepts] == [
        ContentRole.DIRECT,
        ContentRole.DIRECT,
        ContentRole.DIRECT,
        ContentRole.SUPPORTING,
        ContentRole.SUPPORTING,
    ]
    assert [concept.aliases[0] for concept in graph.concepts] == [
        "Rust 메모리 안전성", "ownership", "borrowing", "lifetime", "컴파일 오류 확인"
    ]
    assert graph.unrelated_topics == ()


def test_llm_graph_is_schema_validated_and_cached_per_goal_version():
    client = ScriptedGoalClient({
        "concepts": [
            {
                "id": "rust-ownership",
                "description": "Rust ownership and borrowing rules",
                "aliases": ["Rust ownership", "borrowing", "소유권"],
                "role": "DIRECT",
                "connection": "The confirmed goal explicitly asks to learn ownership and borrowing.",
            },
            {
                "id": "rust-lifetime",
                "description": "Rust lifetime annotations used with borrowed references",
                "aliases": ["lifetime", "라이프타임"],
                "role": "SUPPORTING",
                "connection": "Lifetimes support safe borrowing.",
            },
        ],
        "unrelated_topics": [
            {
                "id": "video-game",
                "description": "Play an online action game",
                "aliases": ["온라인 게임", "게임 매치"],
                "role": None,
                "connection": "This activity is outside the confirmed Rust learning goal.",
            }
        ],
    })
    resolver = GoalGraphResolver(LLMGoalGraphGenerator(client))
    goal = arbitrary_goal()

    first = resolver.resolve(goal)
    second = resolver.resolve(goal)

    assert first is second
    assert client.calls == 1
    assert [concept.role for concept in first.concepts] == [ContentRole.DIRECT, ContentRole.SUPPORTING]
    assert len(first.unrelated_topics) == 1
    assert first.unrelated_topics[0].role is None
    assert "INPUT_JSON" in client.prompts[0]


def test_generated_graph_is_reused_from_persistent_store():
    response = {
        "concepts": [{
            "id": "rust-ownership",
            "description": "Rust ownership and borrowing rules",
            "aliases": ["Rust ownership", "borrowing"],
            "role": "DIRECT",
            "connection": "The confirmed goal explicitly names ownership and borrowing.",
        }],
        "unrelated_topics": [{
            "id": "video-game",
            "description": "Play an online game",
            "aliases": ["온라인 게임", "게임 매치"],
            "role": None,
            "connection": "This activity is outside the confirmed goal.",
        }],
    }
    store = MemoryGoalStore()
    first_client = ScriptedGoalClient(response)
    first = GoalGraphResolver(LLMGoalGraphGenerator(first_client), store=store).resolve(arbitrary_goal())
    second_client = ScriptedGoalClient(error=AssertionError("LLM must not be called"))
    second = GoalGraphResolver(LLMGoalGraphGenerator(second_client), store=store).resolve(arbitrary_goal())

    assert first == second
    assert first_client.calls == 1
    assert second_client.calls == 0


@pytest.mark.parametrize(
    "response",
    [
        {"concepts": [], "unrelated_topics": []},
        {"concepts": [{"id": "direct", "description": "Rust ownership", "aliases": ["ownership"],
                       "role": "DIRECT", "connection": "confirmed goal"}], "unrelated_topics": []},
        {"concepts": [{"id": "only-support", "description": "support", "aliases": ["support"],
                       "role": "SUPPORTING", "connection": "support only"}], "unrelated_topics": []},
        {"concepts": [{"id": "bad", "description": "bad", "aliases": ["same", "same"],
                       "role": "DIRECT", "connection": "duplicate aliases"}], "unrelated_topics": []},
        {"concepts": [{"id": "bad", "description": "bad", "aliases": ["bad"],
                       "role": "UNRELATED", "connection": "forbidden role"}], "unrelated_topics": []},
        {
            "concepts": [{"id": "direct", "description": "Rust ownership", "aliases": ["ownership"],
                          "role": "DIRECT", "connection": "confirmed goal"}],
            "unrelated_topics": [{"id": "other", "description": "other activity",
                                  "aliases": ["ownership", "other"], "role": None,
                                  "connection": "outside goal"}],
        },
    ],
)
def test_invalid_llm_graph_falls_back_to_minimal_confirmed_goal(response):
    client = ScriptedGoalClient(response)
    graph = GoalGraphResolver(LLMGoalGraphGenerator(client)).resolve(arbitrary_goal())

    assert client.calls == 1
    assert graph.version == "confirmed-goal-profile-v1"
    assert len(graph.concepts) == 1
    assert graph.concepts[0].role is ContentRole.DIRECT


def test_provider_failure_falls_back_without_blocking_goal_confirmation():
    client = ScriptedGoalClient(error=TimeoutError("provider unavailable"))
    graph = GoalGraphResolver(LLMGoalGraphGenerator(client)).resolve(arbitrary_goal())

    assert graph.version == "confirmed-goal-profile-v1"
    assert client.calls == 1


def test_provider_failure_does_not_persist_minimal_fallback():
    store = MemoryGoalStore()
    client = ScriptedGoalClient(error=TimeoutError("provider unavailable"))

    graph = GoalGraphResolver(LLMGoalGraphGenerator(client), store=store).resolve(arbitrary_goal())

    assert graph.version == "confirmed-goal-profile-v1"
    assert store.items == {}


def test_unconfirmed_goal_is_rejected_before_graph_generation():
    with pytest.raises(SafeInputError):
        arbitrary_goal(confirmed=False)


def test_fallback_logs_provider_diagnostics_without_exception_body(caplog):
    client = ScriptedGoalClient(error=GoalGraphLLMError(
        "GOAL_GRAPH_PROVIDER_FAILED", http_status=400,
        provider_code="unsupported_value", provider_param="reasoning_effort",
    ))
    graph = GoalGraphResolver(LLMGoalGraphGenerator(client)).resolve(arbitrary_goal())
    assert graph.version == "confirmed-goal-profile-v1"
    assert '"httpStatus": 400' in caplog.text
    assert '"providerParameter": "reasoning_effort"' in caplog.text
    assert "Rust ownership" not in caplog.text


def test_fallback_logs_validation_location_without_rejected_data(caplog):
    client = ScriptedGoalClient({"concepts": [], "unrelated_topics": [],
                                "private-secret-field": "secret-key private goal text"})
    GoalGraphResolver(LLMGoalGraphGenerator(client)).resolve(arbitrary_goal())
    assert '"validationErrors"' in caplog.text
    assert '"concepts"' in caplog.text
    assert '"<extra>"' in caplog.text
    assert "secret-key" not in caplog.text
    assert "private-secret-field" not in caplog.text


def test_fallback_logs_timeout_without_raw_exception(caplog):
    client = ScriptedGoalClient(error=TimeoutError("secret-key private goal text"))
    GoalGraphResolver(LLMGoalGraphGenerator(client)).resolve(arbitrary_goal())
    assert '"failureKind": "TIMEOUT"' in caplog.text
    assert "secret-key" not in caplog.text


def test_prompt_change_invalidates_in_memory_and_persistent_graph_cache(monkeypatch):
    client = ScriptedGoalClient({
        "concepts": [{"id": "rust", "description": "Rust ownership", "aliases": ["ownership", "소유권"],
                      "role": "DIRECT", "connection": "confirmed goal"}],
        "unrelated_topics": [{"id": "other", "description": "online gameplay", "aliases": ["게임", "랭크전"],
                              "role": None, "connection": "outside goal"}],
    })
    store = MemoryGoalStore()
    generator = LLMGoalGraphGenerator(client)
    resolver = GoalGraphResolver(generator, store=store)
    first = resolver.resolve(arbitrary_goal())
    monkeypatch.setattr(goal_prompt, "SYSTEM_PROMPT", goal_prompt.SYSTEM_PROMPT + "\nUpdated quality rules.")
    second = resolver.resolve(arbitrary_goal())
    assert first.version != second.version
    assert client.calls == 2 and len(store.items) == 2
    assert "Updated quality rules." in client.prompts[-1]


def test_goal_only_generation_prompt_does_not_receive_screen_or_expected_label():
    goal = arbitrary_goal()
    prompt = goal_prompt.build_goal_graph_prompt(goal)
    payload = json.loads(prompt.split("\nINPUT_JSON:\n", 1)[1])
    assert payload == {
        "goalId": goal.goal_id, "goalVersion": goal.goal_version,
        "confirmedGoalText": goal.text,
    }
    assert "JWT" not in goal_prompt.SYSTEM_PROMPT  # No hard-coded development case.
    assert "Spring Security" not in goal_prompt.SYSTEM_PROMPT


def test_generation_prompt_omits_unagreed_goal_profile_even_when_supplied():
    goal = arbitrary_goal(goalProfile={
        "mainTopic": "profile-only-topic", "purpose": "LEARNING",
        "coreTopics": ["profile-only-concept"],
    })
    prompt = goal_prompt.build_goal_graph_prompt(goal)
    payload = json.loads(prompt.split("\nINPUT_JSON:\n", 1)[1])
    assert payload == {
        "goalId": goal.goal_id, "goalVersion": goal.goal_version,
        "confirmedGoalText": goal.text,
    }
    assert "profile-only-topic" not in prompt
    assert "profile-only-concept" not in prompt

import pytest

from src.analysis.goal_graph_runtime import build_goal_graph_resolver
from src.config import GoalGraphRuntimeSettings
from src.storage.goal_graph_store import ChromaGoalGraphStore


def settings(tmp_path, **changes):
    values = {
        "llm_base_url": "https://llm.example/v1",
        "llm_api_key": "secret-key",
        "llm_model": "test-model",
        "llm_timeout_seconds": 20,
        "llm_max_retries": 1,
        "chroma_path": str(tmp_path / "chroma"),
        "chroma_collection": "runtime_goal_graphs",
    }
    values.update(changes)
    return GoalGraphRuntimeSettings(**values)


def test_runtime_wires_llm_generator_and_chroma_store(tmp_path):
    resolver = build_goal_graph_resolver(settings(tmp_path))

    assert resolver.generator is not None
    assert isinstance(resolver.store, ChromaGoalGraphStore)


def test_runtime_rejects_partial_llm_configuration(tmp_path):
    with pytest.raises(ValueError, match="LLM_CONFIGURATION_INCOMPLETE"):
        build_goal_graph_resolver(settings(tmp_path, llm_model=""))

from src.analysis.goal_analyzer import compile_minimal_goal_graph
from src.api.schemas import ConfirmedGoal
from src.storage.goal_graph_store import ChromaGoalGraphStore


def test_chroma_goal_graph_store_round_trip_and_clear(tmp_path):
    store = ChromaGoalGraphStore(path=str(tmp_path / "chroma"), collection_name="test_goal_graphs")
    goal = ConfirmedGoal.parse_safe({
        "goalId": "goal-rust",
        "goalVersion": 1,
        "text": "Rust ownership 학습",
        "confirmed": True,
    })
    graph = compile_minimal_goal_graph(goal)

    assert store.load("goal-key") is None
    store.save("goal-key", graph)
    assert store.load("goal-key") == graph

    store.clear()
    assert store.load("goal-key") is None

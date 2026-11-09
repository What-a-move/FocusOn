from src.main import create_app


def test_goal_and_relevance_routes_are_both_registered():
    app = create_app(api_key="x" * 32)
    paths = app.openapi()["paths"]
    assert "/internal/v1/goals/clarify" in paths
    assert "/internal/v1/analysis-runs" in paths
    assert "/internal/v1/analysis-runs/analyze" not in paths

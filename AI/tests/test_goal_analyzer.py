import pytest

from src.analysis.goal_analyzer import GoalAssessment, route_goal_assessment
from src.api.schemas import ClarityStatus
from src.config import Settings


def assessment(
    *, usable: float = 0.9, multiple: float = 0.1, unclear: float = 0.1,
    specificity: float = 3.0, confidence: float = 0.9
) -> GoalAssessment:
    return GoalAssessment(usable, multiple, unclear, specificity, confidence)


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        (assessment(usable=0.1, unclear=0.95, multiple=0.95), ClarityStatus.INVALID),
        (assessment(unclear=0.9, multiple=0.9), ClarityStatus.UNRECOGNIZED_TERM),
        (assessment(multiple=0.9), ClarityStatus.NEEDS_SELECTION),
        (assessment(usable=0.59), ClarityStatus.NEEDS_QUESTION),
        (assessment(confidence=0.44), ClarityStatus.NEEDS_QUESTION),
        (assessment(specificity=2.00), ClarityStatus.CLEAR),
        (assessment(specificity=1.00), ClarityStatus.NEEDS_SUGGESTION),
        (assessment(specificity=0.99), ClarityStatus.NEEDS_QUESTION),
    ],
)
def test_router_uses_deterministic_priority(value, expected):
    assert route_goal_assessment(value, Settings()) is expected


@pytest.mark.parametrize(
    "specificity",
    [
        pytest.param(3.0, id="react-query-caching-study"),
        pytest.param(3.0, id="spring-security-study"),
        pytest.param(3.0, id="english-reading-practice"),
    ],
)
def test_understandable_topic_and_activity_are_clear(specificity):
    """Representative labels: routing depends on the scored meaning, not the text."""
    assert (
        route_goal_assessment(assessment(specificity=specificity), Settings())
        is ClarityStatus.CLEAR
    )

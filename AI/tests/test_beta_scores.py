import pytest

from AI.src.api.routes import experimental_score
from AI.src.api.reception import AnalysisDecisionResponse
from AI.src.preprocessing.models import ContentSource


def test_related_body_score():
    row = {"status": "COMPLETED", "relevanceLabel": "RELATED", "decisionScore": .8, "relatedCoverage": .6}
    assert experimental_score(row, ContentSource.CHROME_DOM) == pytest.approx(.74)


def test_unrelated_body_score():
    row = {"status": "COMPLETED", "relevanceLabel": "UNRELATED", "unrelatedCoverage": .9, "relatedCoverage": .1}
    assert experimental_score(row, ContentSource.DESKTOP_APP_OCR) == pytest.approx(.81)


def test_metadata_and_uncertainty():
    related = {"status": "COMPLETED", "relevanceLabel": "RELATED", "decisionScore": .685}
    assert experimental_score(related, ContentSource.TAB_META) == pytest.approx(.685)
    unrelated = {"status": "COMPLETED", "relevanceLabel": "UNRELATED"}
    assert experimental_score(unrelated, ContentSource.TAB_META) is None
    assert experimental_score({"status": "COMPLETED", "relevanceLabel": "UNCERTAIN"}, ContentSource.CHROME_DOM) is None
    assert experimental_score({"status": "FAILED", "relevanceLabel": "RELATED"}, ContentSource.CHROME_DOM) is None


@pytest.mark.parametrize("value", [None, -0.1, 1.1, float('nan'), True])
def test_invalid_score_is_not_fabricated(value):
    row = {"status": "COMPLETED", "relevanceLabel": "RELATED", "decisionScore": value, "relatedCoverage": .5}
    with pytest.raises(ValueError):
        experimental_score(row, ContentSource.CHROME_DOM)


@pytest.mark.parametrize("confidence", [None, .49, .599])
def test_beta_response_rejects_low_scores(confidence):
    with pytest.raises(ValueError):
        AnalysisDecisionResponse.parse_safe({
            "decision": "UNRELATED", "confidence": confidence, "reasonCode": "SEMANTIC_GOAL_MISMATCH",
            "modelVersion": "test-model", "promptVersion": "beta-uncalibrated",
        })


def test_beta_response_accepts_threshold_score():
    result = AnalysisDecisionResponse.parse_safe({
        "decision": "UNRELATED", "confidence": .6, "reasonCode": "SEMANTIC_GOAL_MISMATCH",
        "modelVersion": "test-model", "promptVersion": "beta-uncalibrated",
    })
    assert result.decision == "UNRELATED" and result.confidence == .6


def test_ordinary_response_still_requires_calibrated_threshold():
    with pytest.raises(ValueError):
        AnalysisDecisionResponse.parse_safe({
            "decision": "UNRELATED", "confidence": .49, "reasonCode": "SEMANTIC_GOAL_MISMATCH",
            "modelVersion": "test-model", "promptVersion": "safe-v1",
        })

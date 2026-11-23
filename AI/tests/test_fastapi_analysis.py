"""Exercise the E5 decision gates through the existing Backend JSON contract."""
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from AI.src.main import create_app
from AI.src.models.screen_activity.activity import model_result

KEY = "local-test-key-only-0123456789abcdef"
PATH = "/internal/v1/analysis-runs"
HEADERS = {"Authorization": f"Bearer {KEY}"}


class FixedScores:
    def __init__(self, scores=(.9, .05, .05)):
        self.scores = scores
        self.inputs = []

    def predict(self, payloads):
        self.inputs.extend(payloads)
        return [model_result(self.scores) for _ in payloads]


@pytest.fixture
def payload():
    return json.loads((Path(__file__).parents[1] / "examples/analysis-reception.json").read_text())


@pytest.mark.parametrize("source,scores,decision,reason", [
    ("TAB_META", (.9, .05, .05), "RELATED", "SEMANTIC_GOAL_MATCH"),
    ("TAB_META", (.05, .9, .05), "UNCERTAIN", "INSUFFICIENT_CONTEXT"),
    ("APP_META", (.05, .9, .05), "UNCERTAIN", "INSUFFICIENT_CONTEXT"),
    ("CHROME_DOM", (.05, .9, .05), "UNRELATED", "SEMANTIC_GOAL_MISMATCH"),
    ("DESKTOP_APP_OCR", (.05, .9, .05), "UNRELATED", "SEMANTIC_GOAL_MISMATCH"),
    ("CHROME_DOM", (.499, .495, .006), "UNCERTAIN", "AMBIGUOUS_CONTEXT"),
    ("CHROME_DOM", (.599, .3, .101), "UNCERTAIN", "INSUFFICIENT_CONTEXT"),
    ("CHROME_DOM", (.6, .3, .1), "RELATED", "SEMANTIC_GOAL_MATCH"),
])
def test_decision_contract(payload, source, scores, decision, reason):
    payload["request"]["contentSource"] = payload["context"]["contentSource"] = source
    if source in {"APP_META", "DESKTOP_APP_OCR"}:
        payload["request"]["metadata"]["appName"] = "Preview"
    if source.endswith("_META"):
        payload["request"].pop("contentPayload")

    model = FixedScores(scores)
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)

    assert response.status_code == 200
    assert response.json() == {"decision": decision, "confidence": None, "reasonCode": reason,
                               "modelVersion": "e5-activity-10168",
                               "promptVersion": "e5-activity-v1-uncalibrated"}
    assert model.inputs == [{
        "contentSource": source,
        "metadata": {**payload["request"]["metadata"], "windowTitle": payload["request"]["metadata"].get("windowTitle") or ""},
        "contentPayload": {"text": payload["request"].get("contentPayload", {}).get("text", "")},
    }]


@pytest.mark.parametrize("failure", ["startup", "inference", "extraction"])
def test_failure_is_uncertain(payload, failure, caplog):
    class Broken:
        def predict(self, payloads):
            raise RuntimeError("private model error")

    def factory():
        if failure == "startup":
            raise ValueError("private model path")
        if failure == "extraction":
            return FixedScores()
        return Broken()

    if failure == "extraction":
        payload["extractionStatus"] = "FAILED"
        payload["request"].pop("contentPayload")
    with TestClient(create_app(api_key=KEY, classifier_factory=factory)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS)
    assert result.status_code == 200
    assert result.json()["decision"] == "UNCERTAIN"
    assert result.json()["reasonCode"] == "ANALYSIS_UNAVAILABLE"
    assert result.json()["confidence"] is None
    assert "private" not in result.text + caplog.text


def test_sensitive_data_is_rejected_before_model(payload):
    payload["request"]["contentPayload"]["text"] = "private@example.com"
    model = FixedScores()
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 422
    assert not model.inputs



def test_chrome_year_selector_is_sanitized_without_bypassing_card_guard(payload):
    model = FixedScores((.05, .9, .05))
    years = " ".join(str(year) for year in range(2026, 1999, -1))
    body = f"Grand Prix results {years} Drivers and teams"
    payload["request"]["contentPayload"]["text"] = body
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        accepted = client.post(PATH, json=payload, headers=HEADERS)
        assert accepted.json()["decision"] == "UNRELATED"
        assert model.inputs[0]["contentPayload"]["text"] == "Grand Prix results [연도 목록] Drivers and teams"

        # Public test PAN used by payment processors.
        for text in (f"{body} 4111 1111 1111 1111", f"{years} 4111 1111 1111 1111",
                     f"4111 1111 1111 1111 {years}"):
            payload["request"]["contentPayload"]["text"] = text
            assert client.post(PATH, json=payload, headers=HEADERS).json() == {"detail": "CONTENT_BLOCKED"}
    assert len(model.inputs) == 1

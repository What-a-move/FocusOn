"""Exercise the E5 decision gates through the existing Backend JSON contract."""
import json
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from AI.src.main import create_app
from AI.src.models.screen_activity.activity import model_result

KEY = "local-test-key-only-0123456789abcdef"
PATH = "/internal/v1/analysis-runs"
HEADERS = {"Authorization": f"Bearer {KEY}"}


class FixedScores:
    def __init__(self, scores=(.9, .05, .05), similarities=None):
        self.scores = scores
        self.inputs = []
        self.encoder = self
        self.similarities = similarities or {}
        self.embeddings = []

    def predict(self, payloads):
        self.inputs.extend(payloads)
        return [model_result(self.scores) for _ in payloads]

    def embed(self, texts, *, role="passage"):
        texts = tuple(texts)
        self.embeddings.append((role, texts))
        if role == "query":
            return {text: np.array([self.similarities.get(text, .4)]) for text in texts}
        return {text: np.array([1.]) for text in texts}


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
                               "promptVersion": "e5-activity-v1-uncalibrated",
                               "matchedGoalId": None,
                               "matchStatus": "NOT_ENOUGH" if decision == "RELATED" else None}
    if decision != "RELATED":
        assert not model.embeddings
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
    assert result.json()["matchedGoalId"] is None and result.json()["matchStatus"] is None
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


def test_metadata_then_body_uses_confirmed_goal_profile(payload):
    payload.pop("goal")
    request = payload["request"]
    request.pop("goalSnapshot")
    request.update(goalVersion=1, goals=[
        {"goalId": "goal-demo", "goalText": "React 실습", "goalProfile": None},
        {"goalId": "auth", "goalText": "인증 구현", "goalProfile": {
            "mainTopic": "인증", "purpose": "CONCEPT_LEARNING", "coreTopics": ["JWT 서명"],
        }},
    ])
    body = request.pop("contentPayload")
    request["contentSource"] = payload["context"]["contentSource"] = "TAB_META"
    model = FixedScores(similarities={"JWT 서명": .8593})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        first = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (first["decision"], first["matchedGoalId"], first["matchStatus"]) == (
            "RELATED", None, "NOT_ENOUGH")
        request["contentSource"] = payload["context"]["contentSource"] = "CHROME_DOM"
        request["contentPayload"] = body
        second = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (second["decision"], second["matchedGoalId"], second["matchStatus"]) == (
            "RELATED", None, "NOT_ENOUGH")
        model.similarities["JWT 서명"] = .86
        matched = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (matched["decision"], matched["matchedGoalId"], matched["matchStatus"]) == (
            "RELATED", "auth", "MATCHED")
        assert model.embeddings[-2] == ("query", ("React 실습", "인증 구현", "인증", "JWT 서명"))
        request["goalVersion"] = 2
        stale = client.post(PATH, json=payload, headers=HEADERS)
        assert stale.status_code == 422
        assert len(model.inputs) == 3


@pytest.mark.parametrize("quality", [None, {"confidence": .2, "captureStatus": "LOW_CONFIDENCE"}])
def test_ocr_preserves_study_when_match_evidence_is_insufficient(payload, quality):
    payload["request"]["contentSource"] = payload["context"]["contentSource"] = "DESKTOP_APP_OCR"
    payload["request"]["metadata"] = {"appName": "Preview", "windowTitle": "학습 메모"}
    if quality:
        payload["ocr"] = quality
    model = FixedScores(similarities={"JWT 인증 구현": .91 if quality else .4})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == (
        "RELATED", None, "NOT_ENOUGH")
    assert bool(model.embeddings) == (quality is None)


def test_body_that_only_repeats_title_does_not_attribute_goal(payload):
    request = payload["request"]
    request["contentPayload"]["text"] = request["metadata"]["pageTitle"]
    model = FixedScores(similarities={"JWT 인증 구현": .95})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == (
        "RELATED", None, "NOT_ENOUGH")
    assert not model.embeddings


def test_matching_failure_keeps_activity_decision(payload):
    model = FixedScores()

    def failed_embed(*args, **kwargs):
        raise RuntimeError("private model error")

    model.embed = failed_embed
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 200
    result = response.json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == (
        "RELATED", None, "NOT_ENOUGH")


def test_versioned_goals_can_include_matching_legacy_snapshot(payload):
    request = payload["request"]
    request.update(goalVersion=1, goals=[
        {"goalId": "goal-demo", "goalText": "JWT 인증 구현"},
        {"goalId": "other", "goalText": "쿠키 읽기"},
    ])
    with TestClient(create_app(api_key=KEY, classifier_factory=FixedScores)) as client:
        assert client.post(PATH, json=payload, headers=HEADERS).status_code == 200
        request["goalSnapshot"] = "다른 목표"
        assert client.post(PATH, json=payload, headers=HEADERS).status_code == 422


def test_far_requires_full_body_with_independent_study_signal(payload):
    payload["request"]["contentPayload"]["text"] = (
        "행렬을 연립방정식으로 바꾼 뒤 가우스 소거법을 사용한다. "
        "첫 번째 열의 피벗 아래 원소를 모두 제거하고 두 번째 열도 같은 방법으로 계산한다. "
        "맨 아래 행에서부터 역대입하여 각 미지수의 값을 구한다."
    )
    model = FixedScores(similarities={"JWT 인증 구현": .75})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        far = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (far["decision"], far["matchStatus"], far["matchedGoalId"]) == ("RELATED", "FAR", None)
        payload["extractionStatus"] = "PARTIAL"
        partial = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (partial["decision"], partial["matchStatus"]) == ("RELATED", "NOT_ENOUGH")

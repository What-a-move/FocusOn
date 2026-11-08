"""Test the real Hybrid scoring path without contacting the remote model."""
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from AI.src.main import create_app

KEY = "local-test-key-only-0123456789abcdef"
PATH = "/internal/v1/analysis-runs"


class FakeEmbedding:
    fingerprint = "synthetic-test"
    dimensions = 2560
    calls = 0

    def embed(self, texts):
        self.calls += 1
        return [([1.0, 0.0] if "JWT" in text else [0.0, 1.0]) + [0.0] * 2558 for text in texts]

    def close(self):
        pass


@pytest.fixture
def payload():
    return json.loads((Path(__file__).parents[1] / "examples/analysis-reception.json").read_text())


def test_safe_mode_returns_uncertain_with_five_field_contract(payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "safe")
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        response = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"})
    assert response.status_code == 200
    body = response.json()
    assert body["decision"] == "UNCERTAIN" and body["confidence"] is None
    assert set(body) == {"decision", "confidence", "reasonCode", "modelVersion", "promptVersion"}


def test_legacy_analysis_path_is_removed(payload):
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        response = client.post(PATH + "/analyze", json=payload,
                               headers={"Authorization": f"Bearer {KEY}"})
    assert response.status_code == 404


def test_beta_candidate_related_is_not_changed_by_score_gate(payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "candidate")
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        response = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"})
    body = response.json()
    assert response.status_code == 200
    assert body["decision"] == "RELATED"
    assert 0 <= body["confidence"] <= 1
    assert body["promptVersion"].endswith("-uncalibrated")


def test_beta_candidate_uncertain_if_score_unavailable(payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "candidate")
    def unavailable_score(*args):
        raise ValueError("score unavailable")
    monkeypatch.setattr("AI.src.api.routes.experimental_score", unavailable_score)
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        response = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"})
    assert response.status_code == 200
    assert response.json()["decision"] == "UNCERTAIN"
    assert response.json()["confidence"] is None


def test_beta_candidate_uncertain_below_server_threshold(payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "candidate")
    monkeypatch.setattr("AI.src.api.routes.experimental_score", lambda *_: .42)
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        response = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"})
    assert response.status_code == 200
    assert response.json()["decision"] == "UNCERTAIN"
    assert response.json()["confidence"] is None
    assert response.json()["promptVersion"].endswith("-uncalibrated")


@pytest.mark.parametrize("score,expected", [(0.599, "UNCERTAIN"), (0.6, "RELATED")])
def test_beta_threshold_boundary(payload, monkeypatch, score, expected):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "candidate")
    monkeypatch.setattr("AI.src.api.routes.experimental_score", lambda *_: score)
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        response = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"})
    assert response.status_code == 200
    assert response.json()["decision"] == expected


def test_beta_unrelated_kept_and_failure_stays_uncertain(payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "candidate")
    payload["request"]["contentPayload"]["text"] = "게임 방송을 시청하면서 오늘 열린 e스포츠 대회의 경기 결과를 확인합니다. 각 팀의 선수 명단과 순위를 살펴보고 다음 주말에 열릴 결승전의 중계 일정과 참가하는 팀을 확인합니다. 팬들이 올린 댓글과 경기 하이라이트도 살펴봅니다."
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        result = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"}).json()
        assert result["decision"] == "UNRELATED"
        assert result["confidence"] == pytest.approx(1)

        class Broken(FakeEmbedding):
            def embed(self, texts):
                raise RuntimeError("sensitive detail")

        app.state.embedding_client_factory = Broken
        result = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"}).json()
        assert result["decision"] == "UNCERTAIN" and result["confidence"] is None
        assert result["reasonCode"] == "ANALYSIS_UNAVAILABLE"
        assert result["promptVersion"].endswith("-uncalibrated")


def test_beta_metadata_unrelated_remains_uncertain(payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "candidate")
    payload["request"]["contentSource"] = payload["context"]["contentSource"] = "TAB_META"
    payload["request"]["metadata"]["pageTitle"] = "게임 방송 시청"
    payload["request"]["metadata"]["windowTitle"] = None
    payload["request"].pop("contentPayload")
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        result = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"}).json()
    assert result["decision"] == "UNCERTAIN"
    assert result["confidence"] is None


def test_beta_metadata_related_uses_title_hybrid_score(payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "candidate")
    payload["request"]["contentSource"] = payload["context"]["contentSource"] = "TAB_META"
    payload["request"]["metadata"]["pageTitle"] = "JWT 인증"
    payload["request"]["metadata"]["windowTitle"] = None
    payload["request"].pop("contentPayload")
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        result = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"}).json()
    assert result["decision"] == "RELATED"
    assert result["confidence"] == pytest.approx(1.0)
    assert result["promptVersion"] == "title-hybrid-v3-mvp-uncalibrated"


def test_model_failure_is_uncertain_without_candidate(payload):
    class Broken(FakeEmbedding):
        def embed(self, texts):
            raise RuntimeError("secret model error")
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = Broken
        response = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"})
    assert response.json()["decision"] == "UNCERTAIN"
    assert response.json()["reasonCode"] == "ANALYSIS_UNAVAILABLE"
    assert "secret" not in response.text


@pytest.mark.parametrize("body,expected", [
    ("게임 방송을 시청하면서 오늘 열린 e스포츠 대회의 경기 결과를 확인합니다. 각 팀의 선수 명단과 순위를 살펴보고 다음 주말에 열릴 결승전의 중계 일정과 참가하는 팀을 확인합니다. 팬들이 올린 댓글과 경기 하이라이트도 살펴봅니다.", "UNRELATED"),
    ("JWT 서명을 검증합니다.\n\n" + "게임 방송과 e스포츠 경기 소식입니다. " * 10, "UNCERTAIN"),
])
def test_unrelated_and_mixed_candidates(payload, body, expected):
    payload["request"]["contentPayload"]["text"] = body
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = FakeEmbedding
        result = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"}).json()
    assert result["decision"] == "UNCERTAIN" and result["confidence"] is None


def test_sensitive_data_is_rejected_before_model(payload):
    payload["request"]["contentPayload"]["text"] = "private@example.com"
    app = create_app(api_key=KEY)
    with TestClient(app) as client:
        app.state.embedding_client_factory = lambda: pytest.fail("model must not be called")
        response = client.post(PATH, json=payload, headers={"Authorization": f"Bearer {KEY}"})
    assert response.status_code == 422

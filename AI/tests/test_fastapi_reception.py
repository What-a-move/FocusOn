"""HTTP transport acceptance, privacy and trust-boundary regressions."""
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from AI.src.main import create_app

KEY = "local-test-key-only-0123456789abcdef"
PATH = "/internal/v1/analysis-runs"
HEADERS = {"Authorization": f"Bearer {KEY}"}


@pytest.fixture
def payload():
    return json.loads((Path(__file__).parents[1] / "examples/analysis-reception.json").read_text())


@pytest.fixture
def client():
    with TestClient(create_app(api_key=KEY)) as client:
        class ReadyModel:
            def embed(self, texts):
                return [[1.0] + [0.0] * 2559]

            def close(self):
                pass

        client.app.state.embedding_client_factory = ReadyModel
        yield client


def test_health_checks_model_and_reports_failure(client):
    assert client.get("/health").json()["modelConnected"] is True

    class BrokenModel:
        def embed(self, texts):
            raise RuntimeError("sensitive provider details")

        def close(self):
            pass

    client.app.state.embedding_client_factory = BrokenModel
    response = client.get("/health")
    assert response.status_code == 503
    assert response.json()["modelConnected"] is False
    assert response.json()["analysisEnabled"] is False
    assert response.json()["reasonCode"] == "ANALYSIS_UNAVAILABLE"
    assert "sensitive" not in response.text


@pytest.mark.parametrize("key,value", [("FOCUSON_EMBEDDING_MAX_RETRIES", "invalid"),
                                       ("FOCUSON_EMBEDDING_TIMEOUT_SECONDS", "-1")])
def test_health_rejects_invalid_analysis_configuration(monkeypatch, key, value):
    monkeypatch.setenv(key, value)
    with TestClient(create_app(api_key=KEY)) as client:
        response = client.get("/health")
    assert response.status_code == 503
    assert response.json()["analysisEnabled"] is False


def test_health_probe_uses_bounded_settings(monkeypatch):
    monkeypatch.setenv("FOCUSON_EMBEDDING_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("FOCUSON_EMBEDDING_MAX_RETRIES", "2")
    seen = []

    class Probe:
        def __init__(self, settings):
            seen.append(settings)
        def embed(self, texts):
            return [[1.0] + [0.0] * 2559]
        def close(self):
            pass

    monkeypatch.setattr("AI.src.models.embedding_client.OllamaEmbeddingClient", Probe)
    with TestClient(create_app(api_key=KEY)) as client:
        assert client.get("/health").status_code == 200
    assert seen[0].timeout_seconds == 3.0 and seen[0].max_retries == 0


def test_primary_path_returns_analysis_result(client, payload, monkeypatch):
    monkeypatch.setenv("FOCUSON_ANALYSIS_DECISION_MODE", "safe")
    response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert {"decision", "confidence", "reasonCode", "modelVersion", "promptVersion"} <= body.keys()
    assert body["decision"] in {"RELATED", "UNRELATED", "UNCERTAIN"}
    assert "status" not in body
    assert client.get("/health").json()["analysisEnabled"] is True


@pytest.mark.parametrize("token", [None, "wrong", "한글키"])
def test_authentication_before_body_validation(client, token):
    headers = {} if token is None else {"Authorization": "Bearer " + token}
    # HTTP headers are ASCII on the wire; byte form lets us test a non-ASCII token safely.
    if token == "한글키":
        headers = {b"Authorization": b"Bearer " + token.encode()}
    response = client.post(PATH, content="not-json", headers=headers)
    assert response.status_code == 401


@pytest.mark.parametrize("source", ["TAB_META", "APP_META", "DESKTOP_APP_OCR"])
def test_supported_sources(client, payload, source):
    payload["request"]["contentSource"] = payload["context"]["contentSource"] = source
    payload["request"]["metadata"]["appName"] = "Preview" if source != "TAB_META" else "Google Chrome"
    if source.endswith("META"):
        payload["request"].pop("contentPayload")
    else:
        # Beta request contains only the already-extracted text; OCR quality
        # metadata is optional and must not be fabricated by the AI service.
        payload.pop("ocr", None)
    assert client.post(PATH, json=payload, headers=HEADERS).status_code == 200


def test_legacy_analysis_route_is_not_registered(client, payload):
    response = client.post(PATH + "/analyze", json=payload, headers=HEADERS)
    assert response.status_code == 404


@pytest.mark.parametrize("field,value", [("sequence", 2), ("goalVersion", 2), ("sessionId", "another")])
def test_backend_context_mismatch(client, payload, field, value):
    payload["context"][field] = value
    assert client.post(PATH, json=payload, headers=HEADERS).status_code == 422


@pytest.mark.parametrize("field,value", [("sessionStatus", "ENDED"), ("isActiveTarget", False),
                                        ("resumeRequired", True), ("exclusionMode", "ANALYSIS_ONLY")])
def test_inactive_or_excluded_context(client, payload, field, value):
    payload["context"][field] = value
    assert client.post(PATH, json=payload, headers=HEADERS).status_code == 409


@pytest.mark.parametrize("length,expected", [(2000, 200), (2001, 422)])
def test_text_limit(client, payload, length, expected):
    payload["request"]["contentPayload"]["text"] = "가" * length
    assert client.post(PATH, json=payload, headers=HEADERS).status_code == expected


def test_no_validation_input_or_text_leaks(client, payload, caplog):
    secret = "private.person@example.com"
    payload["request"]["contentPayload"]["text"] = secret
    response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 422
    assert response.json() == {"detail": "CONTENT_BLOCKED"}
    payload["request"]["unknown"] = secret
    invalid = client.post(PATH, json=payload, headers=HEADERS)
    assert invalid.json() == {"detail": "INVALID_INPUT"}
    assert secret not in response.text + invalid.text + caplog.text


def test_chrome_ocr_and_desktop_browser_misrouting(client, payload):
    for source in ("CHROME_VIEWPORT_OCR", "DESKTOP_APP_OCR"):
        payload["request"]["contentSource"] = payload["context"]["contentSource"] = source
        assert client.post(PATH, json=payload, headers=HEADERS).status_code == 409


def test_guard_payload_never_reaches_analysis(client, payload):
    payload["request"]["guardStatus"] = "EXCLUDED"
    for field in ("goalSnapshot", "metadata", "contentPayload"):
        payload["request"].pop(field)
    assert client.post(PATH, json=payload, headers=HEADERS).json() == {"detail": "GUARD_BLOCKED"}


def test_invalid_json_and_bounded_body(client):
    headers = {**HEADERS, "Content-Type": "application/json"}
    for body in ('{"request":', '{"request":1,"request":2}'):
        assert client.post(PATH, content=body, headers=headers).status_code == 422
    assert client.post(PATH, content=b"x" * 65537, headers=headers).status_code == 413
    assert client.post(PATH, content="{}", headers=HEADERS).status_code == 415


def test_openapi_refs_are_resolvable(client):
    schema = client.get("/openapi.json").json()
    def visit(value):
        if isinstance(value, dict):
            if "$ref" in value:
                target = schema
                for part in value["$ref"].removeprefix("#/").split("/"):
                    target = target[part]
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    visit(schema)
    assert schema["components"]["securitySchemes"]["BackendServiceKey"]["scheme"] == "bearer"


def test_missing_service_key_fails_startup(monkeypatch):
    monkeypatch.delenv("FOCUSON_AI_API_KEY", raising=False)
    with pytest.raises(RuntimeError, match="FOCUSON_AI_API_KEY_REQUIRED"):
        with TestClient(create_app()):
            pass

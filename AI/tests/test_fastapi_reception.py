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
        yield client


def test_receipt_is_not_an_analysis_result(client, payload):
    response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 200
    assert response.json() == {"status": "RECEIVED", "sessionId": "session-demo",
                               "clientRunId": payload["request"]["clientRunId"], "analysisExecuted": False}
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
        payload["ocr"] = {"confidence": .91, "captureStatus": "SUCCESS"}
    assert client.post(PATH, json=payload, headers=HEADERS).status_code == 200


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

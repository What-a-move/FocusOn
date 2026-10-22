import json

import httpx
import pytest

from src.models.llm_client import GoalGraphLLMError, OpenAICompatibleGoalGraphClient


def test_openai_compatible_client_requests_json_without_exposing_key():
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json={
            "choices": [{"message": {"content": json.dumps({"concepts": [], "unrelated_topics": []})}}]
        })

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = OpenAICompatibleGoalGraphClient(
        base_url="https://llm.example/v1",
        api_key="secret-key",
        model="test-model",
        max_retries=0,
        client=http,
    )

    content = client.complete("Return JSON")

    assert json.loads(content) == {"concepts": [], "unrelated_topics": []}
    assert requests[0].url == "https://llm.example/v1/chat/completions"
    assert requests[0].headers["authorization"] == "Bearer secret-key"
    body = json.loads(requests[0].content)
    assert body["model"] == "test-model"
    assert body["response_format"]["type"] == "json_schema"
    assert body["response_format"]["json_schema"]["strict"] is True
    assert "temperature" not in body
    assert "secret-key" not in client.fingerprint


def test_openai_compatible_client_uses_no_reasoning_for_gpt_6_luna():
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json={
            "choices": [{"message": {"content": json.dumps({"status": "ok"})}}]
        })

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = OpenAICompatibleGoalGraphClient(
        base_url="https://api.openai.com/v1",
        api_key="secret-key",
        model="gpt-6-luna",
        max_retries=0,
        client=http,
    )

    client.complete("Return JSON")

    body = json.loads(requests[0].content)
    assert body["reasoning_effort"] == "none"
    assert "temperature" not in body


def test_openai_compatible_client_returns_fixed_error_for_invalid_response():
    http = httpx.Client(transport=httpx.MockTransport(
        lambda request: httpx.Response(200, json={"choices": []})
    ))
    client = OpenAICompatibleGoalGraphClient(
        base_url="https://llm.example/v1",
        api_key="secret-key",
        model="test-model",
        max_retries=0,
        client=http,
    )

    with pytest.raises(GoalGraphLLMError, match="GOAL_GRAPH_PROVIDER_RESPONSE_INVALID"):
        client.complete("Return JSON")


@pytest.mark.parametrize("provider_code,provider_param,expected_code,expected_param", [
    ("unsupported_value", "reasoning_effort", "unsupported_value", "reasoning_effort"),
    ("secret-key", "private goal text", "other", None),
])
def test_provider_failure_keeps_only_safe_diagnostics(provider_code, provider_param,
                                                     expected_code, expected_param):
    http = httpx.Client(transport=httpx.MockTransport(lambda request: httpx.Response(400, json={
        "error": {"message": "secret-key private goal text", "code": provider_code,
                  "param": provider_param},
    })))
    client = OpenAICompatibleGoalGraphClient(
        base_url="https://llm.example/v1", api_key="secret-key", model="test-model",
        max_retries=0, client=http,
    )
    with pytest.raises(GoalGraphLLMError) as caught:
        client.complete("private goal text")
    assert caught.value.http_status == 400
    assert caught.value.provider_code == expected_code
    assert caught.value.provider_param == expected_param
    assert "secret-key" not in str(caught.value)

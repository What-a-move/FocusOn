from __future__ import annotations

import hashlib
import json
import math
import time
from typing import Protocol

import httpx


_GOAL_GRAPH_RESPONSE_FORMAT = {
    "type": "json_schema",
    "json_schema": {
        "name": "focuson_goal_graph",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "concepts": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "id": {"type": "string"},
                            "description": {"type": "string"},
                            "aliases": {"type": "array", "items": {"type": "string"}},
                            "role": {
                                "type": "string",
                                "enum": ["DIRECT", "PREREQUISITE", "SUPPORTING"],
                            },
                            "connection": {"type": "string"},
                        },
                        "required": ["id", "description", "aliases", "role", "connection"],
                        "additionalProperties": False,
                    },
                },
                "unrelated_topics": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "id": {"type": "string"},
                            "description": {"type": "string"},
                            "aliases": {"type": "array", "items": {"type": "string"}},
                            "role": {"type": "null"},
                            "connection": {"type": "string"},
                        },
                        "required": ["id", "description", "aliases", "role", "connection"],
                        "additionalProperties": False,
                    },
                },
            },
            "required": ["concepts", "unrelated_topics"],
            "additionalProperties": False,
        },
    },
}


class GoalGraphLLMError(RuntimeError):
    """Safe provider-independent goal graph generation failure."""

    def __init__(self, code: str, *, http_status: int | None = None,
                 provider_code: str | None = None, provider_param: str | None = None):
        super().__init__(code)
        self.http_status = http_status
        self.provider_code = provider_code
        self.provider_param = provider_param


def _provider_failure(response: httpx.Response) -> GoalGraphLLMError:
    # Provider messages may echo credentials or input. Retain only known identifiers.
    try:
        error = response.json().get("error", {})
    except (ValueError, AttributeError):
        error = {}
    if not isinstance(error, dict):
        error = {}
    codes = {
        "unsupported_value", "unsupported_parameter", "invalid_api_key", "insufficient_quota",
        "rate_limit_exceeded", "model_not_found", "invalid_json_schema", "invalid_request_error",
        "context_length_exceeded", "content_policy_violation", "billing_hard_limit_reached",
    }
    params = {"model", "reasoning_effort", "response_format", "temperature", "max_tokens",
              "max_completion_tokens", "messages"}
    code, param = error.get("code"), error.get("param")
    return GoalGraphLLMError(
        "GOAL_GRAPH_PROVIDER_FAILED",
        http_status=response.status_code,
        provider_code=code if isinstance(code, str) and code in codes else "other",
        provider_param=param if isinstance(param, str) and param in params else None,
    )


class GoalGraphLLMClient(Protocol):
    """Provider adapter. A concrete authenticated client is added after provider selection."""

    fingerprint: str

    def complete(self, prompt: str) -> str:
        ...


class OpenAICompatibleGoalGraphClient:
    """Small Chat Completions adapter for schema-validated goal graph JSON."""

    def __init__(self, *, base_url: str, api_key: str, model: str,
                 timeout_seconds: float = 20, max_retries: int = 1,
                 client: httpx.Client | None = None):
        base_url = base_url.strip().rstrip("/")
        api_key = api_key.strip()
        model = model.strip()
        if not base_url.startswith(("https://", "http://")):
            raise ValueError("INVALID_LLM_BASE_URL")
        if not api_key or not model:
            raise ValueError("LLM_CONFIGURATION_INCOMPLETE")
        if not math.isfinite(timeout_seconds) or timeout_seconds <= 0 or timeout_seconds > 120:
            raise ValueError("INVALID_LLM_TIMEOUT")
        if isinstance(max_retries, bool) or not isinstance(max_retries, int) or not 0 <= max_retries <= 2:
            raise ValueError("INVALID_LLM_RETRY_LIMIT")
        self.base_url = base_url
        self.model = model
        self.timeout_seconds = timeout_seconds
        self.max_retries = max_retries
        self._authorization_header = f"Bearer {api_key}"
        self._owns_client = client is None
        self._client = client or httpx.Client(
            timeout=timeout_seconds,
        )
        identity = json.dumps(
            [base_url, model, "chat-completions-json-schema-v2"], separators=(",", ":")
        )
        self.fingerprint = "openai-compatible:" + hashlib.sha256(identity.encode()).hexdigest()[:16]

    def complete(self, prompt: str) -> str:
        if not isinstance(prompt, str) or not prompt or len(prompt) > 64_000:
            raise GoalGraphLLMError("GOAL_GRAPH_PROMPT_INVALID")
        payload = {
            "model": self.model,
            "messages": [{"role": "user", "content": prompt}],
            "response_format": _GOAL_GRAPH_RESPONSE_FORMAT,
        }
        if self.model.startswith("gpt-6-luna"):
            payload["reasoning_effort"] = "none"
        response: httpx.Response | None = None
        for attempt in range(self.max_retries + 1):
            try:
                response = self._client.post(
                    f"{self.base_url}/chat/completions",
                    headers={"Authorization": self._authorization_header, "Content-Type": "application/json"},
                    json=payload,
                    timeout=self.timeout_seconds,
                )
            except (httpx.TimeoutException, httpx.NetworkError) as error:
                if attempt < self.max_retries:
                    time.sleep(0.25 * (attempt + 1))
                    continue
                raise GoalGraphLLMError("GOAL_GRAPH_PROVIDER_FAILED") from error
            if (response.status_code == 429 or response.status_code >= 500) and attempt < self.max_retries:
                time.sleep(0.25 * (attempt + 1))
                continue
            try:
                response.raise_for_status()
            except httpx.HTTPStatusError as error:
                raise _provider_failure(response) from error
            break
        if response is None:
            raise GoalGraphLLMError("GOAL_GRAPH_PROVIDER_FAILED")
        try:
            body = response.json()
            content = body["choices"][0]["message"]["content"]
        except (ValueError, KeyError, IndexError, TypeError) as error:
            raise GoalGraphLLMError("GOAL_GRAPH_PROVIDER_RESPONSE_INVALID", http_status=response.status_code) from error
        if not isinstance(content, str) or not content:
            raise GoalGraphLLMError("GOAL_GRAPH_PROVIDER_RESPONSE_INVALID", http_status=response.status_code)
        return content

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

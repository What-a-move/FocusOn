import json

import httpx
import pytest

from src.models.embedding_client import EmbeddingError, EmbeddingSettings, OllamaEmbeddingClient


def mock_service(request):
    if request.url.path == "/api/tags":
        return httpx.Response(200, json={"models": [{"name": "qwen3-embedding:4b-fp16", "digest": "synthetic-digest", "details": {"quantization_level": "F16"}}]})
    if request.url.path == "/api/show":
        return httpx.Response(200, json={"capabilities": ["embedding"], "model_info": {"qwen3.embedding_length": 2560}})
    if request.url.path == "/api/version":
        return httpx.Response(200, json={"version": "test-version"})
    body = json.loads(request.content)
    assert request.url.path == "/api/embed" and body["truncate"] is False
    assert "dimensions" not in body
    return httpx.Response(200, json={"model": body["model"], "embeddings": [[1]+[0]*2559 for _ in body["input"]], "prompt_eval_count": 12})


def settings(**changes):
    return EmbeddingSettings(**{"revision": "synthetic-digest", **changes})


def test_batch_payload_and_metadata_verification():
    paths = []
    def service(request):
        paths.append(request.url.path)
        return mock_service(request)
    with OllamaEmbeddingClient(settings(batch_size=2), transport=httpx.MockTransport(service)) as client:
        vectors = client.embed(("synthetic document", "another document", "third document"))
        assert len(vectors) == 3 and len(vectors[0]) == 2560
        assert client.calls == 2 and client.prompt_tokens == 24
        assert client.service_version == "test-version"
    assert paths.count("/api/tags") == 2 and paths.count("/api/embed") == 2


@pytest.mark.parametrize("override,code", [({"digest": "changed"}, "REVISION"), ({"details": {"quantization_level": "Q4"}}, "PRECISION")])
def test_rejects_wrong_model_before_text_sent(override, code):
    paths = []
    def service(request):
        paths.append(request.url.path)
        response = mock_service(request)
        if request.url.path == "/api/tags":
            body = response.json()
            body["models"][0].update(override)
            return httpx.Response(200, json=body)
        return response
    with OllamaEmbeddingClient(settings(), transport=httpx.MockTransport(service)) as client:
        with pytest.raises(EmbeddingError, match=code):
            client.embed(("synthetic text",))
    assert "/api/embed" not in paths


@pytest.mark.parametrize("bad,code", [([[0]*2560], "ZERO"), ([[float("nan")]*2560], "NONFINITE"), ([[1,0]], "DIMENSION"), ([], "COUNT")])
def test_rejects_invalid_provider_vectors(bad, code):
    def service(request):
        if request.url.path == "/api/embed":
            # Raw JSON permits testing a nonfinite response from a broken provider.
            return httpx.Response(200, content=json.dumps({"model": "qwen3-embedding:4b-fp16", "embeddings": bad}).encode())
        return mock_service(request)
    with OllamaEmbeddingClient(settings(), transport=httpx.MockTransport(service)) as client:
        with pytest.raises(EmbeddingError, match=code):
            client.embed(("synthetic text",))


def test_timeout_retries_are_bounded_and_error_sanitized():
    attempts = []
    def service(request):
        attempts.append(request.url.path)
        raise httpx.ReadTimeout("secret in provider error", request=request)
    with OllamaEmbeddingClient(settings(max_retries=1), transport=httpx.MockTransport(service)) as client:
        with pytest.raises(EmbeddingError) as error:
            client.embed(("private synthetic text",))
    assert len(attempts) == 2
    assert str(error.value) == "EMBEDDING_TIMEOUT"
    assert error.value.__context__ is None


@pytest.mark.parametrize("changes", [{"revision": ""}, {"model": "qwen3:4b"}, {"base_url": "http://public.example.invalid:11434"}, {"base_url": "https://secret:password@example.invalid"}, {"max_retries": 10}, {"batch_size": 0}, {"dimensions": 1024}])
def test_invalid_settings(changes):
    with pytest.raises(ValueError):
        settings(**changes)


def test_input_limit_no_request_and_post_batch_tag_swap():
    paths = []
    def service(request):
        paths.append(request.url.path)
        if request.url.path == "/api/tags" and paths.count("/api/tags") == 2:
            return httpx.Response(200, json={"models": []})
        return mock_service(request)
    with OllamaEmbeddingClient(settings(max_input_bytes=10), transport=httpx.MockTransport(service)) as client:
        with pytest.raises(EmbeddingError, match="INPUT_LIMIT"):
            client.embed(("too long synthetic document",))
        assert not paths
        with pytest.raises(EmbeddingError, match="REVISION"):
            client.embed(("valid",))


def test_retry_is_counted_as_an_actual_embedding_call():
    attempts = []
    def service(request):
        if request.url.path == "/api/embed":
            attempts.append(1)
            if len(attempts) == 1:
                raise httpx.ReadTimeout("temporary synthetic failure", request=request)
        return mock_service(request)
    with OllamaEmbeddingClient(settings(max_retries=1), transport=httpx.MockTransport(service)) as client:
        assert len(client.embed(("synthetic document",))) == 1
        assert client.calls == 2

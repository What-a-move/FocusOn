import json

import httpx
import pytest

from src.models.embedding_client import EmbeddingError, EmbeddingSettings, OllamaEmbeddingClient


def mock_service(request):
    assert request.method == "POST" and request.url.path == "/api/embed"
    body = json.loads(request.content)
    assert body["model"] == "qwen3-embedding:4b-fp16" and body["truncate"] is False
    assert body["keep_alive"] == "10m"
    assert "dimensions" not in body
    assert "revision" not in body and "precision" not in body
    return httpx.Response(200, json={"model": body["model"], "embeddings": [[1]+[0]*2559 for _ in body["input"]], "prompt_eval_count": 12})


def settings(**changes):
    return EmbeddingSettings(**{"revision": "synthetic-digest", **changes})


def test_batches_and_repeated_calls_only_request_embeddings():
    paths = []
    def service(request):
        paths.append(request.url.path)
        return mock_service(request)
    with OllamaEmbeddingClient(settings(batch_size=2), transport=httpx.MockTransport(service)) as client:
        vectors = client.embed(("synthetic document", "another document", "third document"))
        assert len(vectors) == 3 and len(vectors[0]) == 2560
        assert len(client.embed(("fourth document",))) == 1
        assert client.calls == 3 and client.prompt_tokens == 36
        assert client.service_version is None
    assert paths == ["/api/embed"] * 3


@pytest.mark.parametrize("model", [None, "different-model"])
def test_rejects_wrong_model_in_embedding_response(model):
    paths = []
    def service(request):
        paths.append(request.url.path)
        body = mock_service(request).json()
        body["model"] = model
        return httpx.Response(200, json=body)
    with OllamaEmbeddingClient(settings(), transport=httpx.MockTransport(service)) as client:
        with pytest.raises(EmbeddingError, match="MODEL_RESPONSE_MISMATCH"):
            client.embed(("synthetic text",))
    assert paths == ["/api/embed"]


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
    assert attempts == ["/api/embed"] * 2
    assert str(error.value) == "EMBEDDING_TIMEOUT"
    assert error.value.__context__ is None


@pytest.mark.parametrize("changes", [{"revision": ""}, {"model": "qwen3:4b"}, {"base_url": "http://public.example.invalid:11434"}, {"base_url": "https://secret:password@example.invalid"}, {"max_retries": 10}, {"batch_size": 0}, {"dimensions": 1024}])
def test_invalid_settings(changes):
    with pytest.raises(ValueError):
        settings(**changes)


def test_empty_and_oversized_input_make_no_request():
    paths = []
    def service(request):
        paths.append(request.url.path)
        return mock_service(request)
    with OllamaEmbeddingClient(settings(max_input_bytes=10), transport=httpx.MockTransport(service)) as client:
        assert client.embed(()) == ()
        with pytest.raises(EmbeddingError, match="INPUT_LIMIT"):
            client.embed(("too long synthetic document",))
        assert not paths
        assert len(client.embed(("valid",))) == 1
    assert paths == ["/api/embed"]


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

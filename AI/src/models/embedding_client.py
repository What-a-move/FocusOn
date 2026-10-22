"""Qwen3 Ollama embedding transport, with no generation or tracing integration."""
from __future__ import annotations

import hashlib
import json
import math
from dataclasses import dataclass
from threading import BoundedSemaphore
from typing import Protocol, Sequence
from urllib.parse import urlsplit

import httpx

from ..analysis.hybrid_scoring import VectorError, normalize_vector


class EmbeddingError(RuntimeError):
    pass


class EmbeddingClient(Protocol):
    fingerprint: str
    dimensions: int

    def embed(self, texts: Sequence[str]) -> tuple[tuple[float, ...], ...]: ...


@dataclass(frozen=True, slots=True)
class EmbeddingSettings:
    base_url: str = "http://127.0.0.1:11435"
    model: str = "qwen3-embedding:4b-fp16"
    revision: str = ""  # Configured cache namespace; not verified against the server.
    dimensions: int = 2560
    timeout_seconds: float = 30.0
    max_retries: int = 1
    batch_size: int = 16
    max_input_bytes: int = 8_000
    max_concurrency: int = 2
    max_response_bytes: int = 4_000_000
    keep_alive: str = "10m"

    def __post_init__(self) -> None:
        url = urlsplit(self.base_url)
        if (url.scheme not in {"http", "https"} or not url.hostname or url.username or url.password
                or url.query or url.fragment or url.path not in {"", "/"}):
            raise ValueError("INVALID_EMBEDDING_URL")
        if url.scheme == "http" and url.hostname not in {"localhost", "127.0.0.1", "::1"}:
            raise ValueError("USE_TLS_OR_LOCAL_SSH_TUNNEL")
        if self.model != "qwen3-embedding:4b-fp16" or not self.revision:
            raise ValueError("PIN_QWEN_FP16_MODEL_DIGEST")
        for value in (self.dimensions, self.batch_size, self.max_input_bytes, self.max_concurrency, self.max_response_bytes):
            if isinstance(value, bool) or not isinstance(value, int) or value < 1:
                raise ValueError("INVALID_EMBEDDING_LIMITS")
        if self.dimensions != 2560:
            raise ValueError("DIMENSION_REDUCTION_NOT_CALIBRATED")
        if isinstance(self.timeout_seconds, bool) or not math.isfinite(self.timeout_seconds) or self.timeout_seconds <= 0:
            raise ValueError("INVALID_TIMEOUT")
        if isinstance(self.max_retries, bool) or not isinstance(self.max_retries, int) or not 0 <= self.max_retries <= 2:
            raise ValueError("INVALID_RETRY_LIMIT")


class OllamaEmbeddingClient:
    def __init__(self, settings: EmbeddingSettings, *, transport: httpx.BaseTransport | None = None):
        self.settings = settings
        self.dimensions = settings.dimensions
        self.fingerprint = hashlib.sha256(json.dumps({
            "provider": "ollama", "model": settings.model, "revision": settings.revision,
            "precision": "F16", "dimensions": settings.dimensions, "normalization": "l2-v1",
        }, sort_keys=True).encode()).hexdigest()
        self._http = httpx.Client(base_url=settings.base_url, timeout=settings.timeout_seconds,
                                  transport=transport, trust_env=False, follow_redirects=False)
        self._slots = BoundedSemaphore(settings.max_concurrency)
        self.calls = 0
        self.prompt_tokens = 0
        self.service_version: str | None = None  # Metadata is not queried.

    def close(self) -> None:
        self._http.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def _request(self, method: str, path: str, body: dict | None = None) -> dict:
        code = "EMBEDDING_UNAVAILABLE"
        for attempt in range(self.settings.max_retries + 1):
            if path == "/api/embed":
                self.calls += 1
            try:
                with self._http.stream(method, path, json=body) as response:
                    if response.status_code in {429, 503, 504}:
                        code = "EMBEDDING_UNAVAILABLE"
                        continue
                    if response.status_code != 200:
                        code = "EMBEDDING_HTTP_ERROR"
                        break
                    chunks, size = [], 0
                    for chunk in response.iter_bytes():
                        size += len(chunk)
                        if size > self.settings.max_response_bytes:
                            raise EmbeddingError("EMBEDDING_RESPONSE_TOO_LARGE")
                        chunks.append(chunk)
                    value = json.loads(b"".join(chunks))
                    if not isinstance(value, dict):
                        raise EmbeddingError("EMBEDDING_RESPONSE_INVALID")
                    return value
            except httpx.TimeoutException:
                code = "EMBEDDING_TIMEOUT"
            except httpx.TransportError:
                code = "EMBEDDING_UNAVAILABLE"
            except (ValueError, TypeError):
                code = "EMBEDDING_RESPONSE_INVALID"
                break
        # Raised outside exception handlers: provider errors/URLs/bodies are not chained.
        raise EmbeddingError(code)

    def embed(self, texts: Sequence[str]) -> tuple[tuple[float, ...], ...]:
        if not texts:
            return ()
        if any(not isinstance(text, str) or not text or len(text.encode()) > self.settings.max_input_bytes for text in texts):
            raise EmbeddingError("EMBEDDING_INPUT_LIMIT")
        if not self._slots.acquire(timeout=self.settings.timeout_seconds):
            raise EmbeddingError("EMBEDDING_QUEUE_TIMEOUT")
        try:
            output = []
            for start in range(0, len(texts), self.settings.batch_size):
                batch = list(texts[start:start + self.settings.batch_size])
                response = self._request("POST", "/api/embed", {
                    "model": self.settings.model, "input": batch, "truncate": False,
                    "keep_alive": self.settings.keep_alive,
                })
                if response.get("model") != self.settings.model:
                    raise EmbeddingError("MODEL_RESPONSE_MISMATCH")
                vectors = response.get("embeddings")
                if not isinstance(vectors, list) or len(vectors) != len(batch):
                    raise EmbeddingError("VECTOR_COUNT_INVALID")
                try:
                    normalized = tuple(normalize_vector(vector, self.dimensions) for vector in vectors)
                except VectorError as error:
                    code = str(error)
                else:
                    output.extend(normalized)
                    tokens = response.get("prompt_eval_count", 0)
                    if isinstance(tokens, int) and not isinstance(tokens, bool) and tokens >= 0:
                        self.prompt_tokens += tokens
                    continue
                raise EmbeddingError(code)
            return tuple(output)
        finally:
            self._slots.release()

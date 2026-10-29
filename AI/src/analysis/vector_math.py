"""Validated vector normalization and cosine similarity for embedding clients."""
from __future__ import annotations

import math
from typing import Sequence


class VectorError(ValueError):
    pass


def normalize_vector(vector: Sequence[float], dimensions: int) -> tuple[float, ...]:
    if not isinstance(vector, (list, tuple)) or len(vector) != dimensions:
        raise VectorError("VECTOR_DIMENSION_INVALID")
    if any(isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) for value in vector):
        raise VectorError("VECTOR_NONFINITE")
    # Scaling avoids norm overflow on finite but large provider values.
    scale = max((abs(value) for value in vector), default=0)
    if scale == 0:
        raise VectorError("VECTOR_ZERO")
    scaled = tuple(value / scale for value in vector)
    norm = math.sqrt(sum(value * value for value in scaled))
    return tuple(value / norm for value in scaled)


def cosine_similarity(first: Sequence[float], second: Sequence[float]) -> float:
    a = normalize_vector(first, len(first))
    b = normalize_vector(second, len(first))
    return max(-1.0, min(1.0, sum(x * y for x, y in zip(a, b))))

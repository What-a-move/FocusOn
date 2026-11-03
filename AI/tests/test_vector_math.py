import math

import pytest

from src.analysis.vector_math import VectorError, cosine_similarity, normalize_vector


@pytest.mark.parametrize("vector,code", [([], "DIMENSION"), ([0, 0], "ZERO"), ([math.nan, 1], "NONFINITE"), ([math.inf, 1], "NONFINITE"), ([True, 1], "NONFINITE"), (["1", 1], "NONFINITE")])
def test_invalid_vectors(vector, code):
    with pytest.raises(VectorError, match=code):
        normalize_vector(vector, 2)


def test_cosine_ranges_and_large_values():
    assert cosine_similarity([1, 0], [1, 0]) == 1
    assert cosine_similarity([1, 0], [-1, 0]) == -1
    assert cosine_similarity([1e308, 1e308], [1e308, 1e308]) == pytest.approx(1)
    with pytest.raises(VectorError):
        cosine_similarity([1, 0], [1])

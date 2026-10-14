import math

import pytest

from src.analysis.goal_graph import ContentRole, build_goal_graph, keyword_match
from src.analysis.hybrid_scoring import (HybridConfig, VectorError, cosine_similarity,
                                         decide_relevance, normalize_vector)
from src.preprocessing.models import CleanedPassage, PassageKind


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


@pytest.mark.parametrize("text,term,expected", [("Promise.then", "Promise", True), ("Promiseland", "Promise", False), ("useStates", "useState", False), ("useState로", "useState", True), ("벡터를", "벡터", True), ("새벡터", "벡터", False)])
def test_keyword_boundaries(text, term, expected):
    assert keyword_match(text, term) is expected


def test_roles_are_independent_of_direct_score():
    graph = build_goal_graph("React useState로 객체 상태 변경하기", "react-object-state")
    passage = CleanedPassage("p1", PassageKind.BODY, "JavaScript 객체 복사와 전개 구문을 설명합니다.", 0)
    concepts = tuple(tuple(float(i == j) for i in range(6)) for j in range(6))
    decision = decide_relevance((passage,), graph, ((0, 1, 0, 0, 0, 0),), concepts, HybridConfig())
    assert decision.label == "RELATED"
    assert decision.roles == (ContentRole.PREREQUISITE,)
    assert decision.evidence_ids == ("p1",)


def test_title_only_and_low_cosine_do_not_prove_unrelated():
    graph = build_goal_graph("React useState", "react-object-state")
    concepts = tuple(tuple(float(i == j) for i in range(6)) for j in range(6))
    title = CleanedPassage("t", PassageKind.TITLE, "useState 상태 업데이트", 0)
    assert decide_relevance((title,), graph, ((1, 0, 0, 0, 0, 0),), concepts, HybridConfig()).label == "UNCERTAIN"
    body = CleanedPassage("b", PassageKind.BODY, "새로운 표현으로 된 알 수 없는 기술 문서입니다.", 1)
    assert decide_relevance((body,), graph, ((0, 0, 0, 0, 0, 1),), concepts, HybridConfig()).label == "UNCERTAIN"


def test_template_does_not_expand_unknown_goal():
    assert build_goal_graph("수학 공부", "react-object-state") is None
    assert build_goal_graph("Python 객체 상태 변경하기", "react-object-state") is None
    assert build_goal_graph("React 전체 기초 학습", "react-basics") is not None


@pytest.mark.parametrize("changes", [{"keyword_weight": 0.8}, {"min_cosine": float("nan")}, {"related_coverage": 0}, {"vector_weight": True}])
def test_calibration_validation(changes):
    with pytest.raises(ValueError):
        HybridConfig(**changes)


@pytest.fixture
def react_vectors():
    graph = build_goal_graph("React useState로 객체 상태 변경하기", "react-object-state")
    vectors = tuple(tuple(float(i == j) for i in range(6)) for j in range(6))
    return graph, vectors


@pytest.mark.parametrize("kind", [PassageKind.TITLE, PassageKind.HEADING])
@pytest.mark.parametrize("text,node,label", [
    ("useState 상태 업데이트", 0, "RELATED"),
    ("노래 가사 앨범", 4, "UNRELATED"),
])
def test_context_is_scored_and_returned_with_matching_body(react_vectors, kind, text, node, label):
    graph, vectors = react_vectors
    passages = (CleanedPassage("context", kind, text, 0),
                CleanedPassage("body", PassageKind.BODY, text + "에 대한 자세한 설명입니다.", 1))
    decision = decide_relevance(passages, graph, (vectors[node], vectors[node]), vectors, HybridConfig())
    assert decision.label == label
    assert decision.evidence_ids == ("context", "body")
    match = next(m for m in decision.matches if m.passage_id == "context" and m.concept_id == ("state-update" if node == 0 else "music"))
    assert match.keyword_score == 1 and match.cosine == pytest.approx(1)
    assert match.hybrid_score == pytest.approx(1)


@pytest.mark.parametrize("kind", [PassageKind.TITLE, PassageKind.HEADING])
@pytest.mark.parametrize("context_related", [True, False])
def test_context_and_body_with_opposite_evidence_are_uncertain(react_vectors, kind, context_related):
    graph, vectors = react_vectors
    texts = ("useState 상태 업데이트", "노래 가사 앨범")
    context_index, body_index = (0, 1) if context_related else (1, 0)
    nodes = (0, 4)
    passages = (CleanedPassage("context", kind, texts[context_index], 0),
                CleanedPassage("body", PassageKind.BODY, texts[body_index], 1))
    decision = decide_relevance(passages, graph, (vectors[nodes[context_index]], vectors[nodes[body_index]]), vectors, HybridConfig())
    assert decision.label == "UNCERTAIN" and decision.reason_code == "CONFLICTING_EVIDENCE"
    assert not decision.evidence_ids


@pytest.mark.parametrize("kind", [PassageKind.TITLE, PassageKind.HEADING])
def test_long_context_neither_inflates_nor_dilutes_body_coverage(react_vectors, kind):
    graph, vectors = react_vectors
    related = CleanedPassage("related", PassageKind.BODY, "useState 상태 업데이트", 1)
    neutral = CleanedPassage("neutral", PassageKind.BODY, "일반적인 배경 설명 " * 20, 2)
    long_title = CleanedPassage("context", kind, "useState 상태 업데이트 " * 40, 0)
    insufficient = decide_relevance((long_title, related, neutral), graph,
                                     (vectors[0], vectors[0], vectors[5]), vectors, HybridConfig())
    assert insufficient.label == "UNCERTAIN" and insufficient.reason_code == "INSUFFICIENT_EVIDENCE"
    neutral_title = CleanedPassage("context", kind, "자료 제목 " * 100, 0)
    sufficient = decide_relevance((neutral_title, related), graph,
                                   (vectors[5], vectors[0]), vectors, HybridConfig())
    assert sufficient.label == "RELATED" and sufficient.evidence_ids == ("related",)


def test_title_keywords_without_semantic_match_do_not_create_conflict(react_vectors):
    graph, vectors = react_vectors
    passages = (CleanedPassage("title", PassageKind.TITLE, "노래 가사", 0),
                CleanedPassage("body", PassageKind.BODY, "useState 상태 업데이트", 1))
    decision = decide_relevance(passages, graph, (vectors[5], vectors[0]), vectors, HybridConfig())
    assert decision.label == "RELATED" and decision.evidence_ids == ("body",)

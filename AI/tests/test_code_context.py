import pytest

from src.analysis.goal_graph import Concept, ContentRole, GoalGraph
from src.analysis.hybrid_scoring import HybridConfig, VectorError, decide_relevance
from src.analysis.passage_grouping import build_code_context_groups
from src.preprocessing.models import CleanedPassage, PassageKind


def passage(identifier, kind, text, order):
    return CleanedPassage(identifier, kind, text, order)


@pytest.mark.parametrize("kind", [PassageKind.BODY, PassageKind.OCR])
def test_only_adjacent_explanation_and_explicit_code_are_grouped(kind):
    explanation = passage("body", kind, "causal mask explains masked self-attention", 0)
    code = passage("code", PassageKind.CODE, "output = values", 1)
    groups = build_code_context_groups((explanation, code))
    assert len(groups) == 1
    assert groups[0].source_ids == ("body", "code")
    assert groups[0].passage.text == explanation.text + "\n\n" + code.text


@pytest.mark.parametrize("barrier", [PassageKind.TITLE, PassageKind.HEADING, PassageKind.TABLE])
def test_groups_do_not_cross_structural_boundaries(barrier):
    passages = (passage("b", PassageKind.BODY, "explanation", 0),
                passage("h", barrier, "other section", 1),
                passage("c", PassageKind.CODE, "output = values", 2))
    assert build_code_context_groups(passages) == ()


def test_plain_text_standalone_code_and_order_gaps_are_not_inferred():
    body = passage("b", PassageKind.BODY, "print(1) may be plain text", 0)
    code = passage("c", PassageKind.CODE, "output = values", 2)
    assert build_code_context_groups((body,)) == ()
    assert build_code_context_groups((code,)) == ()
    assert build_code_context_groups((body, code)) == ()


def test_group_limit_counts_utf8_bytes_including_separator():
    passages = (passage("b", PassageKind.BODY, "설명", 0),
                passage("c", PassageKind.CODE, "x=1", 1))
    assert len("설명\n\nx=1".encode()) == 11
    assert build_code_context_groups(passages, max_bytes=10) == ()
    assert len(build_code_context_groups(passages, max_bytes=11)) == 1


def test_removed_source_boundary_does_not_create_an_adjacent_pair():
    passages = (passage("b", PassageKind.BODY, "explanation", 0),
                passage("c", PassageKind.CODE, "output = values", 1))
    assert build_code_context_groups(passages, source_pairs=()) == ()


def graph():
    return GoalGraph("test", "v1", "attention", (
        Concept("attention", "causal attention", ("causal mask", "masked self-attention"),
                ContentRole.DIRECT, "goal"),
    ), (Concept("game", "playing games", ("game", "ranked match"), None, "outside goal"),))


def test_group_evidence_maps_to_original_ids_and_counts_code_once():
    passages = (passage("body", PassageKind.BODY, "causal mask and masked self-attention", 0),
                passage("code", PassageKind.CODE, "output = values\n" * 8, 1))
    vectors = ((1, 0), (0, 1))
    assert decide_relevance(passages, graph(), vectors, ((1, 0), (0, 1)), HybridConfig()).label == "UNCERTAIN"
    groups = build_code_context_groups(passages)
    result = decide_relevance(passages, graph(), vectors, ((1, 0), (0, 1)), HybridConfig(),
                              context_groups=groups, context_vectors=((1, 0),))
    assert result.label == "RELATED"
    assert result.evidence_ids == ("body", "code")
    assert all(not identifier.startswith("code-context:") for identifier in result.evidence_ids)


def test_group_text_never_inflates_original_body_coverage():
    passages = (passage("body", PassageKind.BODY, "causal mask and masked self-attention", 0),
                passage("code", PassageKind.CODE, "output = values", 1),
                passage("neutral", PassageKind.BODY, "neutral content " * 6, 2))
    result = decide_relevance(passages, graph(), ((1, 0), (0, 1), (0, 1)),
                              ((1, 0), (0, 1)), HybridConfig(),
                              context_groups=build_code_context_groups(passages), context_vectors=((1, 0),))
    assert result.label == "UNCERTAIN" and result.reason_code == "INSUFFICIENT_EVIDENCE"


@pytest.mark.parametrize("reverse", [False, True])
def test_group_cannot_hide_opposing_original_evidence(reverse):
    related, unrelated = "causal mask masked self-attention", "game ranked match"
    passages = (passage("body", PassageKind.BODY, unrelated if reverse else related, 0),
                passage("code", PassageKind.CODE, related if reverse else unrelated, 1))
    vectors = ((0, 1), (1, 0)) if reverse else ((1, 0), (0, 1))
    result = decide_relevance(passages, graph(), vectors, ((1, 0), (0, 1)), HybridConfig(),
                              context_groups=build_code_context_groups(passages), context_vectors=((1, 0),))
    assert result.label == "UNCERTAIN" and result.reason_code == "CONFLICTING_EVIDENCE"


def test_group_does_not_pass_just_because_explanation_passes():
    passages = (passage("body", PassageKind.BODY, "causal mask masked self-attention", 0),
                passage("code", PassageKind.CODE, "output = values\n" * 20, 1))
    result = decide_relevance(passages, graph(), ((1, 0), (0, 1)), ((1, 0), (0, 1)), HybridConfig(),
                              context_groups=build_code_context_groups(passages), context_vectors=((0, 1),))
    assert result.label == "UNCERTAIN"


def test_missing_group_vector_is_rejected():
    passages = (passage("b", PassageKind.BODY, "causal mask", 0),
                passage("c", PassageKind.CODE, "output = values", 1))
    with pytest.raises(VectorError, match="VECTOR_COUNT_INVALID"):
        decide_relevance(passages, graph(), ((1, 0), (1, 0)), ((1, 0), (0, 1)), HybridConfig(),
                         context_groups=build_code_context_groups(passages))

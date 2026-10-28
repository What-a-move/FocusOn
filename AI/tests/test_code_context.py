import pytest

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

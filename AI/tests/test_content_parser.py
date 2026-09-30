import pytest

from AI.src.preprocessing.content_parser import InvalidStructure, parse_passages


def test_orders_are_sorted_and_ids_preserved():
    passages = parse_passages([
        {"id": "second", "kind": "CODE", "order": 2, "text": "if x:\n    pass"},
        {"id": "first", "kind": "BODY", "order": 1, "text": "본문"},
    ], "DOM")
    assert [(p.id, p.kind, p.order) for p in passages] == [
        ("first", "BODY", 1), ("second", "CODE", 2)
    ]


@pytest.mark.parametrize("items", [
    [{"id": "a", "kind": "BODY", "order": 1, "text": "a"},
     {"id": "a", "kind": "BODY", "order": 2, "text": "b"}],
    [{"id": "a", "kind": "BODY", "order": 1, "text": "a"},
     {"id": "b", "kind": "BODY", "order": 1, "text": "b"}],
    [{"id": "a", "kind": ["BODY"], "order": 1, "text": "a"}],
    [{"id": "a", "kind": "BODY", "order": True, "text": "a"}],
    [{"id": "a", "kind": "BODY", "order": 1, "text": "a", "url": "private"}],
])
def test_bad_structure_has_fixed_error(items):
    with pytest.raises(InvalidStructure, match="^INVALID_INPUT$") as exc:
        parse_passages(items, "DOM")
    assert "private" not in repr(exc.value)


def test_ocr_confidence_is_bounded_and_ocr_only():
    item = {"id": "a", "kind": "OCR", "order": 1, "text": "본문", "recognitionConfidence": 0.4}
    assert parse_passages([item], "OCR")[0].recognition_confidence == 0.4
    with pytest.raises(InvalidStructure):
        parse_passages([item], "DOM")
    with pytest.raises(InvalidStructure):
        parse_passages([{**item, "recognitionConfidence": float("nan")}], "OCR")
    with pytest.raises(InvalidStructure):
        parse_passages([{key: value for key, value in item.items() if key != "recognitionConfidence"}], "DOM")

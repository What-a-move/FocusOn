from AI.src.preprocessing.models import Passage
from AI.src.preprocessing.text_cleaner import clean_text, content_fingerprint, deduplicate


def test_plain_text_cleaning_and_code_preservation():
    assert clean_text(" <p> 첫째&nbsp;  둘째 </p>\n\n\n끝 ", "BODY") == "첫째 둘째\n\n끝"
    assert clean_text("if x:\n    return False  \n", "CODE") == "if x:\n    return False"
    assert clean_text("A  | B\n1  | 2", "TABLE") == "A  | B\n1  | 2"


def test_only_exact_same_kind_duplicate_is_removed():
    passages = (
        Passage("a", "BODY", 1, "not true"),
        Passage("b", "BODY", 2, "not true"),
        Passage("c", "CODE", 3, "not true"),
        Passage("d", "BODY", 4, "true"),
    )
    assert [p.id for p in deduplicate(passages)] == ["a", "c", "d"]


def test_fingerprint_uses_cleaned_ordered_content_not_event_or_passage_ids():
    left = (Passage("a", "BODY", 1, "first"), Passage("b", "CODE", 2, "second"))
    equivalent = (Passage("x", "BODY", 10, "first"), Passage("y", "CODE", 20, "second"))
    reversed_content = tuple(reversed(equivalent))
    assert content_fingerprint("title", left) == content_fingerprint("title", equivalent)
    assert content_fingerprint("title", left) != content_fingerprint("title", reversed_content)

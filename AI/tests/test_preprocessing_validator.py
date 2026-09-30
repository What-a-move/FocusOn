from dataclasses import replace

import pytest

from AI.src.preprocessing.models import EventContext
from AI.src.preprocessing.validator import PreprocessingPolicy, preprocess


CURRENT = EventContext(
    "550e8400-e29b-41d4-a716-446655440001",
    "550e8400-e29b-41d4-a716-446655440002",
    "550e8400-e29b-41d4-a716-446655440003",
    1,
    "navigation-003",
)


def payload(**overrides):
    item = {
        "eventId": CURRENT.event_id,
        "sessionId": CURRENT.session_id,
        "runId": CURRENT.run_id,
        "goalVersion": CURRENT.goal_version,
        "navigationId": CURRENT.navigation_id,
        "extractionMethod": "DOM",
        "extractionStatus": "SUCCESS",
        "title": "JWT 인증 오류",
        "passages": [{"id": "p1", "kind": "BODY", "order": 1, "text": "필터 순서와 403 오류 해결"}],
    }
    item.update(overrides)
    return item


def test_valid_dom_content_is_ready_and_hash_is_stable():
    first = preprocess(payload(), CURRENT)
    changed_event = replace(CURRENT, event_id="550e8400-e29b-41d4-a716-446655440004")
    second = preprocess(payload(eventId=changed_event.event_id), changed_event)
    assert first.ready_for_analysis
    assert first.title == "JWT 인증 오류"
    assert [(p.id, p.kind, p.order) for p in first.passages] == [("p1", "BODY", 1)]
    assert first.content_hash == second.content_hash
    assert "필터 순서" not in repr(first)
    assert first.content_hash != preprocess(payload(title="다른 주제"), CURRENT).content_hash


@pytest.mark.parametrize("status,expected", [
    ("EXCLUDED", "PRIVACY_EXCLUDED"),
    ("FAILED", "EXTRACTION_FAILED"),
    ("UNSUPPORTED", "UNSUPPORTED_EXTRACTION"),
])
def test_terminal_extraction_drops_content(status, expected):
    result = preprocess(payload(extractionStatus=status, passages="raw secret"), CURRENT)
    assert not result.ready_for_analysis
    assert result.reason_code == expected
    assert result.passages == () and result.content_hash is None


@pytest.mark.parametrize("current", [
    replace(CURRENT, event_id="550e8400-e29b-41d4-a716-446655440004"),
    replace(CURRENT, run_id="550e8400-e29b-41d4-a716-446655440004"),
    replace(CURRENT, goal_version=2),
    replace(CURRENT, navigation_id="navigation-004"),
])
def test_stale_event_is_discarded(current):
    result = preprocess(payload(), current)
    assert result.preprocessing_status == "STALE_EVENT"
    assert not result.ready_for_analysis
    assert result.passages == ()


@pytest.mark.parametrize("bad", [
    {"passages": []},
    {"passages": [{"id": "p1", "kind": "BODY", "order": 1, "text": "   "}]},
    {"passages": [{"id": "p1", "kind": "BODY", "order": 1, "text": "깨진\ufffd본문"}]},
    {"passages": [{"id": "p1", "kind": "BODY", "order": 1, "text": "깨진\ud800본문"}]},
    {"passages": [{"id": "p1", "kind": "HEADING", "order": 1, "text": "제목만"}]},
])
def test_empty_or_insufficient_input_cannot_be_analyzed(bad):
    result = preprocess(payload(**bad), CURRENT)
    assert not result.ready_for_analysis
    assert result.content_hash is None


def test_duplicate_passage_preserves_first_identity_and_order():
    result = preprocess(payload(passages=[
        {"id": "first", "kind": "BODY", "order": 1, "text": "같은 문단"},
        {"id": "repeat", "kind": "BODY", "order": 2, "text": "같은 문단"},
        {"id": "code", "kind": "CODE", "order": 3, "text": "같은 문단"},
    ]), CURRENT)
    assert result.ready_for_analysis
    assert [p.id for p in result.passages] == ["first", "code"]


@pytest.mark.parametrize("secret", [
    "user@example.com", "010-1234-5678", "+1 415 555 2671", "Bearer hidden-token",
    "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature",
    "sk-abcdefghijklmnopqrst", "4111 1111 1111 1111",
    "https://example.com/path?token=private", "user&#64;example.com",
    "<input value='private'>",
])
def test_sensitive_candidates_are_blocked_without_echo(secret):
    result = preprocess(payload(passages=[
        {"id": "p1", "kind": "BODY", "order": 1, "text": f"본문 {secret}"}
    ]), CURRENT)
    assert result.reason_code == "PRIVACY_BLOCKED"
    assert result.preprocessing_status == "BLOCKED"
    assert result.passages == () and result.content_hash is None
    assert secret not in repr(result)


def test_ocr_quality_uses_injected_threshold_and_does_not_assume_probability():
    request = payload(
        extractionMethod="OCR",
        passages=[{"id": "ocr1", "kind": "OCR", "order": 1,
                   "text": "인식된 본문", "recognitionConfidence": 0.4}],
    )
    result = preprocess(request, CURRENT, PreprocessingPolicy(min_ocr_confidence=0.7))
    assert result.reason_code == "LOW_OCR_CONFIDENCE"
    assert not result.ready_for_analysis


def test_partial_extraction_stays_non_analysis():
    result = preprocess(payload(extractionStatus="PARTIAL"), CURRENT)
    assert result.reason_code == "PARTIAL_EXTRACTION"
    assert not result.ready_for_analysis


def test_invalid_field_and_text_are_not_reflected_in_result():
    result = preprocess(payload(url="private?token=hidden"), CURRENT)
    assert result.reason_code == "INVALID_INPUT"
    assert "private" not in repr(result)

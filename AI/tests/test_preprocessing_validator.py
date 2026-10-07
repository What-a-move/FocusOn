import unittest

from src.preprocessing.content_parser import build_content_hash, parse_content
from src.preprocessing.models import (
    ContentSource,
    CurrentAnalysisContext,
    ExtractionStatus,
    PassageKind,
    PreprocessingRequest,
    PreprocessingStatus,
    RawPassage,
    RejectionReason,
)
from src.preprocessing.validator import validate_and_preprocess


def make_request(**overrides: object) -> PreprocessingRequest:
    values: dict[str, object] = {
        "session_id": "session-1",
        "run_id": "run-1",
        "goal_id": "goal-1",
        "goal_version": 1,
        "event_id": "event-1",
        "navigation_id": "navigation-1",
        "content_source": ContentSource.CHROME_DOM,
        "extraction_status": ExtractionStatus.SUCCESS,
        "quality_score": 0.9,
        "text": "Spring Security JWT 인증 필터를 구현하는 충분한 길이의 설명입니다.",
    }
    values.update(overrides)
    return PreprocessingRequest(**values)


def make_context(**overrides: object) -> CurrentAnalysisContext:
    values: dict[str, object] = {
        "run_id": "run-1",
        "goal_version": 1,
        "navigation_id": "navigation-1",
    }
    values.update(overrides)
    return CurrentAnalysisContext(**values)


class PreprocessingValidatorTest(unittest.TestCase):
    def test_returns_ready_content_and_deterministic_hash(self) -> None:
        first = validate_and_preprocess(make_request(), make_context())
        second = validate_and_preprocess(make_request(event_id="event-2"), make_context())

        self.assertEqual(first.status, PreprocessingStatus.READY)
        self.assertTrue(first.should_analyze)
        self.assertEqual(first.content_hash, second.content_hash)
        self.assertTrue(first.passages)

    def test_partial_input_remains_separate_from_failure(self) -> None:
        result = validate_and_preprocess(
            make_request(extraction_status=ExtractionStatus.PARTIAL), make_context()
        )

        self.assertEqual(result.status, PreprocessingStatus.PARTIAL)
        self.assertTrue(result.should_analyze)

    def test_excluded_failed_and_unsupported_inputs_do_not_continue(self) -> None:
        cases = (
            (ExtractionStatus.EXCLUDED, RejectionReason.EXCLUDED),
            (ExtractionStatus.FAILED, RejectionReason.EXTRACTION_FAILED),
            (ExtractionStatus.UNSUPPORTED, RejectionReason.UNSUPPORTED),
        )
        for extraction_status, expected_reason in cases:
            with self.subTest(extraction_status=extraction_status):
                result = validate_and_preprocess(
                    make_request(extraction_status=extraction_status), make_context()
                )
                self.assertFalse(result.should_analyze)
                self.assertEqual(result.reasons, (expected_reason,))
                self.assertFalse(result.passages)

    def test_sensitive_candidate_discards_text_and_does_not_echo_secret(self) -> None:
        secret = "Bearer abcdefghijklmnopqrstuvwxyz"
        request = make_request(text=secret)
        result = validate_and_preprocess(request, make_context())

        self.assertEqual(result.status, PreprocessingStatus.BLOCKED)
        self.assertEqual(result.reasons, (RejectionReason.PRIVACY_BLOCKED,))
        self.assertFalse(result.passages)
        self.assertNotIn(secret, repr(request))
        self.assertNotIn(secret, repr(result))

    def test_valid_payment_card_candidate_is_blocked(self) -> None:
        result = validate_and_preprocess(
            make_request(text="테스트 카드 4111 1111 1111 1111"), make_context()
        )

        self.assertEqual(result.reasons, (RejectionReason.PRIVACY_BLOCKED,))

    def test_html_encoded_sensitive_candidate_is_blocked(self) -> None:
        result = validate_and_preprocess(
            make_request(text="연락처 user&#64;example.com"), make_context()
        )

        self.assertEqual(result.reasons, (RejectionReason.PRIVACY_BLOCKED,))

    def test_low_quality_never_becomes_relevance_or_drift_result(self) -> None:
        result = validate_and_preprocess(
            make_request(text="짧음", quality_score=0.2), make_context()
        )

        self.assertEqual(result.status, PreprocessingStatus.LOW_QUALITY)
        self.assertFalse(result.should_analyze)
        self.assertIn(RejectionReason.TOO_SHORT, result.reasons)
        self.assertIn(RejectionReason.LOW_CONFIDENCE, result.reasons)

    def test_empty_input_is_returned_as_quality_failure(self) -> None:
        result = validate_and_preprocess(make_request(text=""), make_context())

        self.assertEqual(result.status, PreprocessingStatus.LOW_QUALITY)
        self.assertIn(RejectionReason.EMPTY_CONTENT, result.reasons)
        self.assertFalse(result.should_analyze)

    def test_low_confidence_ocr_does_not_continue_to_analysis(self) -> None:
        result = validate_and_preprocess(
            make_request(
                content_source=ContentSource.CHROME_VIEWPORT_OCR,
                quality_score=0.4,
                text="OCR로 인식했지만 신뢰도가 낮은 충분한 길이의 입력 텍스트입니다.",
            ),
            make_context(),
        )

        self.assertEqual(result.status, PreprocessingStatus.LOW_QUALITY)
        self.assertIn(RejectionReason.LOW_CONFIDENCE, result.reasons)
        self.assertFalse(result.should_analyze)

    def test_short_code_is_not_rejected_only_for_length(self) -> None:
        request = make_request(
            text="",
            passages=(RawPassage("code", PassageKind.CODE, "x = 1", 0),),
        )

        result = validate_and_preprocess(request, make_context())

        self.assertEqual(result.status, PreprocessingStatus.READY)
        self.assertTrue(result.should_analyze)

    def test_duplicate_event_is_rejected_before_processing(self) -> None:
        result = validate_and_preprocess(
            make_request(), make_context(processed_event_ids=frozenset({"event-1"}))
        )

        self.assertEqual(result.status, PreprocessingStatus.DUPLICATE)
        self.assertEqual(result.reasons, (RejectionReason.DUPLICATE_EVENT,))
        self.assertFalse(result.passages)

    def test_duplicate_sanitized_content_is_identified_by_hash(self) -> None:
        request = make_request()
        content_hash = build_content_hash(parse_content(request).passages)
        result = validate_and_preprocess(
            request, make_context(seen_content_hashes=frozenset({content_hash}))
        )

        self.assertEqual(result.status, PreprocessingStatus.DUPLICATE)
        self.assertEqual(result.reasons, (RejectionReason.DUPLICATE_CONTENT,))
        self.assertEqual(result.content_hash, content_hash)
        self.assertFalse(result.passages)

    def test_stale_run_goal_or_navigation_is_rejected(self) -> None:
        contexts = (
            make_context(run_id="run-2"),
            make_context(goal_version=2),
            make_context(navigation_id="navigation-2"),
        )
        for context in contexts:
            with self.subTest(context=context):
                result = validate_and_preprocess(make_request(), context)
                self.assertEqual(result.status, PreprocessingStatus.STALE)
                self.assertEqual(result.reasons, (RejectionReason.STALE_EVENT,))
                self.assertFalse(result.should_analyze)

    def test_invalid_identifier_error_does_not_include_input(self) -> None:
        invalid_identifier = "invalid identifier with spaces"

        with self.assertRaises(ValueError) as raised:
            make_request(event_id=invalid_identifier)

        self.assertNotIn(invalid_identifier, str(raised.exception))

    def test_rejects_ambiguous_flat_text_and_passages(self) -> None:
        with self.assertRaisesRegex(ValueError, "flat text or passages"):
            make_request(
                text="본문",
                passages=(RawPassage("body", PassageKind.BODY, "다른 본문", 0),),
            )

    def test_broken_character_ratio_blocks_analysis(self) -> None:
        result = validate_and_preprocess(
            make_request(text="\ufffd" * 10 + "정상 텍스트" * 5), make_context()
        )

        self.assertEqual(result.status, PreprocessingStatus.LOW_QUALITY)
        self.assertIn(RejectionReason.BROKEN_CONTENT, result.reasons)
        self.assertFalse(result.should_analyze)


if __name__ == "__main__":
    unittest.main()

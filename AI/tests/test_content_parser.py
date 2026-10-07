import unittest

from src.preprocessing.content_parser import build_content_hash, parse_content
from src.preprocessing.models import (
    ContentSource,
    ExtractionStatus,
    PassageKind,
    PreprocessingConfig,
    PreprocessingRequest,
    RawPassage,
)


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
    }
    values.update(overrides)
    return PreprocessingRequest(**values)


class ContentParserTest(unittest.TestCase):
    def test_preserves_title_body_code_table_kinds_and_order(self) -> None:
        request = make_request(
            page_title="JWT 오류 해결",
            passages=(
                RawPassage("body", PassageKind.BODY, "인증 필터 설명입니다.", 2),
                RawPassage("code", PassageKind.CODE, "if token:\n    verify(token)", 3),
                RawPassage("table", PassageKind.TABLE, "항목 | 값\n상태 | 성공", 4),
            ),
        )

        result = parse_content(request)

        self.assertEqual(
            [passage.kind for passage in result.passages],
            [PassageKind.TITLE, PassageKind.BODY, PassageKind.CODE, PassageKind.TABLE],
        )
        self.assertEqual([passage.order for passage in result.passages], [0, 1, 2, 3])
        self.assertIn("    verify(token)", result.passages[2].text)

    def test_maps_flat_ocr_text_to_ocr_passage(self) -> None:
        request = make_request(
            content_source=ContentSource.CHROME_VIEWPORT_OCR,
            text="화면에서 인식한 충분한 길이의 OCR 텍스트입니다.",
        )

        result = parse_content(request)

        self.assertEqual(len(result.passages), 1)
        self.assertEqual(result.passages[0].kind, PassageKind.OCR)

    def test_respects_passage_and_total_character_limits(self) -> None:
        repeated = " ".join(f"고유 문장 {index}입니다." for index in range(80))
        request = make_request(
            passages=(RawPassage("body-1", PassageKind.BODY, repeated, 1),)
        )
        config = PreprocessingConfig(
            max_passages=3,
            max_passage_chars=100,
            max_total_chars=250,
            overlap_chars=10,
        )

        result = parse_content(request, config)

        self.assertLessEqual(len(result.passages), 3)
        self.assertTrue(all(len(passage.text) <= 100 for passage in result.passages))
        self.assertLessEqual(sum(len(passage.text) for passage in result.passages), 250)
        self.assertEqual(
            len({passage.id for passage in result.passages}), len(result.passages)
        )

    def test_removes_exact_duplicate_passages(self) -> None:
        request = make_request(
            passages=(
                RawPassage("body-1", PassageKind.BODY, "동일한 문단입니다.", 1),
                RawPassage("body-2", PassageKind.BODY, "동일한 문단입니다.", 2),
            )
        )

        result = parse_content(request)

        self.assertEqual(len(result.passages), 1)
        self.assertEqual(result.removed_duplicate_count, 1)

    def test_content_hash_depends_on_sanitized_content_not_passage_id(self) -> None:
        first = parse_content(
            make_request(passages=(RawPassage("first", PassageKind.BODY, "동일한 본문입니다.", 1),))
        )
        second = parse_content(
            make_request(passages=(RawPassage("second", PassageKind.BODY, "동일한 본문입니다.", 1),))
        )

        self.assertEqual(
            build_content_hash(first.passages), build_content_hash(second.passages)
        )


if __name__ == "__main__":
    unittest.main()

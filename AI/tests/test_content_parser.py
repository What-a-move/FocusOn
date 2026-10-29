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
        "sequence": 1,
        "navigation_id": "navigation-1",
        "content_source": ContentSource.CHROME_DOM,
        "extraction_status": ExtractionStatus.SUCCESS,
        "quality_score": 0.9,
    }
    values.update(overrides)
    return PreprocessingRequest(**values)


class ContentParserTest(unittest.TestCase):
    def test_extracted_mode_keeps_repeated_headings_as_structural_boundaries(self) -> None:
        request = make_request(passages=(
            RawPassage("h-a", PassageKind.HEADING, "# React 상태", 0),
            RawPassage("a", PassageKind.BODY, "상태 변경 설명 A.", 1),
            RawPassage("h-b", PassageKind.HEADING, "# 요리", 2),
            RawPassage("b", PassageKind.BODY, "조리 순서 설명 B.", 3),
            RawPassage("h-c", PassageKind.HEADING, "# React 상태", 4),
            RawPassage("c", PassageKind.BODY, "새 객체 복사 설명 C.", 5),
        ))
        result = parse_content(request, PreprocessingConfig(preserve_extracted_markup=True))
        self.assertEqual(
            [p.text for p in result.passages if p.kind is PassageKind.HEADING],
            ["# React 상태", "# 요리", "# React 상태"],
        )
        self.assertEqual(result.removed_duplicate_count, 0)
        legacy = parse_content(request)
        self.assertEqual(len(legacy.passages), 5)
        self.assertEqual(legacy.removed_duplicate_count, 1)

    def test_repeated_extracted_heading_resets_graphless_body_context(self) -> None:
        import json
        from AI.evaluation.streamlit_runner import parse_inputs
        from AI.evaluation.graphless_hybrid import build_screen_contexts
        from src.preprocessing.adapter import adapt_content

        text = ("# React 상태\n\nReact 상태 변경 설명 A.\n\n"
                "# 요리\n\n양파를 익히는 설명 B.\n\n"
                "# React 상태\n\n이전 객체를 복사하는 설명 C.")
        _, cases = parse_inputs("React 객체 상태 변경", json.dumps({"text": text}), "CHROME_DOM")
        _, content, context, _ = cases[0]
        parsed = parse_content(
            adapt_content(content, context), PreprocessingConfig(preserve_extracted_markup=True)
        )
        groups = build_screen_contexts(parsed.passages)
        self.assertEqual(len(groups), 3)
        self.assertIn("[heading]\nReact 상태", groups[0].passage.text)
        self.assertIn("[heading]\n요리", groups[1].passage.text)
        self.assertIn("[heading]\nReact 상태", groups[2].passage.text)
        self.assertNotIn("요리", groups[2].passage.text)
        self.assertEqual(groups[2].source_ids, (parsed.passages[-1].id,))

    def test_extracted_text_mode_preserves_jsx_generics_and_literal_entities(self) -> None:
        texts = (
            "return <Counter count={state.count}>값</Counter>;",
            "List<T> values = new List<T>();",
            'const label = "&lt;Counter&gt; &amp; 상태";',
        )
        config = PreprocessingConfig(preserve_extracted_markup=True)
        for source in (ContentSource.CHROME_DOM, ContentSource.DESKTOP_APP_OCR):
            for text in texts:
                with self.subTest(source=source, text=text):
                    result = parse_content(make_request(content_source=source, text=text), config)
                    self.assertEqual(len(result.passages), 1)
                    self.assertEqual(result.passages[0].text, text)

    def test_legacy_mode_still_extracts_text_from_html(self) -> None:
        result = parse_content(make_request(text="<p>React &amp; 상태</p><div>새 객체</div>"))
        self.assertEqual(result.passages[0].text, "React & 상태\n새 객체")

    def test_extracted_mode_keeps_existing_code_and_table_rules(self) -> None:
        request = make_request(passages=(
            RawPassage("code", PassageKind.CODE, 'const x = "&lt;T&gt;";', 0),
            RawPassage("table", PassageKind.TABLE, "용어 | 의미\nA &amp; B | 값", 1),
        ))
        default = parse_content(request)
        extracted = parse_content(request, PreprocessingConfig(preserve_extracted_markup=True))
        self.assertEqual(extracted.passages, default.passages)

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

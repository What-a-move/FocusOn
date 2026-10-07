import unittest

from src.preprocessing.models import PassageKind
from src.preprocessing.text_cleaner import clean_text, split_text


class TextCleanerTest(unittest.TestCase):
    def test_cleans_html_whitespace_ui_fragments_and_exact_duplicates(self) -> None:
        result = clean_text(
            "<p>  첫 문장입니다.   첫 문장입니다. </p>\n확인\n공유\n확인 방법",
            PassageKind.BODY,
            frozenset({"닫기", "확인", "공유"}),
        )

        self.assertEqual(result.text, "첫 문장입니다.\n확인 방법")
        self.assertEqual(result.removed_duplicate_count, 1)
        self.assertEqual(result.removed_ui_fragment_count, 2)

    def test_preserves_code_indentation_and_line_order(self) -> None:
        result = clean_text(
            "if value &lt; limit:\n    run()\n\n\n    finish()  ",
            PassageKind.CODE,
            frozenset(),
        )

        self.assertEqual(result.text, "if value < limit:\n    run()\n\n    finish()")

    def test_chunks_text_with_hard_size_limit(self) -> None:
        text = " ".join(f"token-{index}" for index in range(100))

        chunks = split_text(text, max_chars=120, overlap_chars=20, preserve_lines=False)

        self.assertGreater(len(chunks), 1)
        self.assertTrue(all(0 < len(chunk) <= 120 for chunk in chunks))

    def test_removes_exact_sentence_duplicates_across_lines(self) -> None:
        result = clean_text(
            "첫 문장입니다. 둘째 문장입니다.\n첫 문장입니다. 셋째 문장입니다.",
            PassageKind.BODY,
            frozenset(),
        )

        self.assertEqual(
            result.text,
            "첫 문장입니다. 둘째 문장입니다.\n셋째 문장입니다.",
        )
        self.assertEqual(result.removed_duplicate_count, 1)


if __name__ == "__main__":
    unittest.main()

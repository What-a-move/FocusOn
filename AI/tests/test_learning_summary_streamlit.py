import unittest
import os
from pathlib import Path
from unittest.mock import patch

from src.learning_summary_demo import DemoOutcome

try:
    from streamlit.testing.v1 import AppTest
except ImportError:
    AppTest = None


@unittest.skipIf(AppTest is None, "optional Streamlit demo dependency is not installed")
class LearningSummaryStreamlitTests(unittest.TestCase):
    def setUp(self):
        environment = patch.dict(os.environ)
        environment.start()
        self.addCleanup(environment.stop)
        os.environ.pop("OPENAI_API_KEY", None)
        values = patch("dotenv.dotenv_values", return_value={})
        self.values = values.start()
        self.addCleanup(values.stop)
        script = Path(__file__).resolve().parents[1] / "demos" / "learning_summary_streamlit.py"
        self.app = AppTest.from_file(str(script)).run()
        self.assertEqual(len(self.app.exception), 0)

    def test_ready_then_insufficient(self):
        self.app.button[0].click().run()
        self.assertEqual(self.app.session_state["demo_outcome"].status, "READY")
        self.app.selectbox[0].set_value("근거 1개").run()
        self.assertNotIn("demo_outcome", self.app.session_state)
        self.app.button[0].click().run()
        self.assertEqual(self.app.session_state["demo_outcome"].status, "INSUFFICIENT_DATA")

    def test_failure_and_consent_withdrawal(self):
        self.app.selectbox[0].set_value("생성 실패").run()
        self.app.button[0].click().run()
        self.assertEqual(self.app.session_state["demo_outcome"].status, "FAILED")
        self.app.checkbox[0].set_value(False).run()
        self.app.button[0].click().run()
        self.assertEqual(self.app.session_state["demo_outcome"].status, "INSUFFICIENT_DATA")

    def test_openai_button_is_disabled_without_key(self):
        self.assertEqual(len(self.app.button), 2)
        self.assertTrue(self.app.button[1].disabled)

    def test_primary_checkout_key_is_loaded_without_copying_other_settings(self):
        self.values.return_value = {"OPENAI_API_KEY": "synthetic-key", "UNRELATED_SETTING": "ignored"}
        self.app.run()
        self.assertFalse(self.app.button[1].disabled)
        self.assertEqual(os.environ["OPENAI_API_KEY"], "synthetic-key")
        self.assertNotIn("UNRELATED_SETTING", os.environ)

    def test_exported_key_has_priority(self):
        os.environ["OPENAI_API_KEY"] = "synthetic-exported-key"
        self.values.reset_mock()
        self.app.run()
        self.values.assert_not_called()
        self.assertFalse(self.app.button[1].disabled)

    def test_mvp_output_has_only_three_sections_and_exact_json_fields(self):
        os.environ["OPENAI_API_KEY"] = "synthetic-key"
        result = DemoOutcome("READY", {
            "overview": "캐시의 저장 기준을 살펴봤습니다. 데이터 신선도 기간도 정리했습니다.",
            "keyConcepts": [{"name": "queryKey", "briefExplanation": "캐시를 구분하는 기준입니다.",
                             "sessionEvidence": "queryKey에 따른 캐시 저장 방식 확인"}],
            "nextStartPrompt": "다음에는 queryKey에서 확인한 내용을 이어서 살펴보세요.",
        }, 2)
        self.app.run()
        with patch("src.learning_summary_openai.generate_synthetic_dom_summary", return_value=result) as generate:
            self.app.button[1].click().run()
        self.assertEqual(len(self.app.exception), 0)
        generate.assert_called_once()
        self.assertTrue(generate.call_args.kwargs["record_consent"])
        text = "\n".join(item.value for item in self.app.markdown)
        for section in ("학습 내용 한눈에 보기", "핵심 개념", "다음 시작 지점"):
            self.assertIn(section, text)
        for forbidden in ("복습 퀴즈", "추천 개념", "reviewQuestions", "recommendedConcepts"):
            self.assertNotIn(forbidden, text)
        self.assertEqual(set(self.app.session_state["openai_outcome"].summary),
                         {"overview", "keyConcepts", "nextStartPrompt"})


if __name__ == "__main__":
    unittest.main()

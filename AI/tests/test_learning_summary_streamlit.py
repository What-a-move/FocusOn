import unittest
from pathlib import Path

try:
    from streamlit.testing.v1 import AppTest
except ImportError:
    AppTest = None


@unittest.skipIf(AppTest is None, "optional Streamlit demo dependency is not installed")
class LearningSummaryStreamlitTests(unittest.TestCase):
    def setUp(self):
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


if __name__ == "__main__":
    unittest.main()

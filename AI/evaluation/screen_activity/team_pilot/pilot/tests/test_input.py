import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from pilot.model import verified_payload


class PilotInputTests(unittest.TestCase):
    def payload(self, source, metadata, body=None):
        return verified_payload("test-session", "test-context", 1, source, metadata, body)

    def test_only_the_active_tab_fields_survive_the_contract(self):
        row = self.payload("CHROME_DOM", {"appName": "Google Chrome", "pageTitle": "예제"}, "행렬 곱")
        self.assertEqual(row["contentPayload"]["text"], "행렬 곱")
        self.assertEqual(row["metadata"]["pageTitle"], "예제")
        with self.assertRaises(ValueError):
            self.payload("TAB_META", {"appName": "Google Chrome", "pageTitle": "예제"}, "본문")

    def test_desktop_route_rejects_a_browser_window_and_sensitive_text(self):
        with self.assertRaises(ValueError):
            self.payload("DESKTOP_APP_OCR", {"appName": "Google Chrome"}, "본문")
        with self.assertRaises(ValueError):
            self.payload("DESKTOP_APP_OCR", {"appName": "Preview"}, "user@example.org")

    def test_oversize_and_empty_content_fail_closed(self):
        for text in ("", "x" * 2001):
            with self.subTest(length=len(text)), self.assertRaises(ValueError):
                self.payload("DESKTOP_APP_OCR", {"appName": "Preview"}, text)

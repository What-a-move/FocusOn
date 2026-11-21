import unittest

import numpy as np

from activity import ActivityClassifier, model_result
from beta_decision import beta_decision


class BetaDecisionTests(unittest.TestCase):
    def test_metadata_needs_body_for_off_task(self):
        result = model_result(np.array([0.04, 0.95, 0.01]))
        for source in ("APP_META", "TAB_META"):
            decision = beta_decision({"contentSource": source}, result)
            self.assertEqual((decision["label"], decision["confidence"]), ("UNCERTAIN", None))
        decision = beta_decision({"contentSource": "CHROME_DOM"}, result)
        self.assertEqual((decision["label"], decision["confidence"]), ("OFF_TASK", 0.95))

    def test_margin_and_confidence_gates(self):
        payload = {"contentSource": "CHROME_DOM"}
        for scores, expected in (
            ([0.502, 0.498, 0.0], "AMBIGUOUS_CONTEXT"),
            ([0.59, 0.35, 0.06], "INSUFFICIENT_CONTEXT"),
            ([0.60, 0.35, 0.05], "SEMANTIC_GOAL_MATCH"),
        ):
            self.assertEqual(
                beta_decision(payload, model_result(np.array(scores)))["reasonCode"], expected
            )

    def test_native_editor_shortcut_after_guard(self):
        # These paths must return without touching the encoder.
        model = ActivityClassifier.__new__(ActivityClassifier)
        data = {"contentSource": "APP_META", "metadata": {"appName": "Code"}}
        result = model.predict([data])[0]
        self.assertEqual(result["decisionSource"], "native_editor_policy")
        decision = beta_decision(data, result)
        self.assertEqual((decision["label"], decision["confidence"]), ("STUDY", None))
        for blocked in (
            dict(data, guardStatus="PRIVACY_BLOCKED"),
            {"contentSource": "CHROME_VIEWPORT_OCR"},
            {"contentSource": "DESKTOP_APP_OCR", "metadata": {"appName": "Google Chrome"}},
        ):
            with self.assertRaises(ValueError):
                model.predict([blocked])
            with self.assertRaises(ValueError):
                beta_decision(blocked, result)
        self.assertEqual(beta_decision(data, None, "FAILED")["reasonCode"], "ANALYSIS_UNAVAILABLE")
        with self.assertRaises(ValueError):
            beta_decision(data, None, "EXCLUDED")


if __name__ == "__main__":
    unittest.main()

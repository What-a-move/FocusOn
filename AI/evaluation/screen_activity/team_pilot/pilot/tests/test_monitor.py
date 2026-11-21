import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from pilot.monitor import Monitor


class Store:
    def __init__(self):
        self.calls = []

    def observe(self, *args):
        self.calls.append(args)


class Reader:
    def __init__(self):
        self.calls = []

    def __call__(self, method, *args):
        self.calls.append(method)
        if method == "ocr":
            return {"state": "guard", "reason": "protected"}
        return {
            "state": "desktop",
            "context": "one",
            "metadata": {"appName": "Preview", "windowTitle": "example.pdf"},
        }


class Model:
    def __init__(self, hook=None):
        self.hook = hook

    def classify(self, *args):
        if self.hook:
            self.hook()
        return "UNCERTAIN"


class MonitorTests(unittest.TestCase):
    def setUp(self):
        self.sec = 1
        self.reader = Reader()
        self.store = Store()
        self.m = Monitor(self.store, Model(), self.reader, lambda: self.sec)
        self.m.activate("one")
        self.m.tick()

    def tick(self, t):
        self.sec = t
        self.m.tick()

    def test_guard_does_not_reopen_same_window_for_ocr(self):
        for t in (17, 28, 59, 80):
            self.tick(t)
        self.assertEqual(self.reader.calls.count("ocr"), 1)
        self.assertEqual(self.store.calls[-1][2], "GUARD")

    def test_end_during_metadata_inference_suppresses_fallback_capture(self):
        self.m.model = Model(lambda: self.m.activate(None))
        self.tick(17)
        self.assertNotIn("ocr", self.reader.calls)
        self.assertEqual(self.reader.calls, ["meta", "meta"])
        self.assertFalse(self.m.poll()["active"])


if __name__ == "__main__":
    unittest.main()

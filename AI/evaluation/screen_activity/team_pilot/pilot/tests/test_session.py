import json
import sqlite3
import sys
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from session import Sessions, Timeline


class Clock:
    def __init__(self):
        self.t = 0

    def __call__(self):
        return self.t


class PolicyTests(unittest.TestCase):
    def test_previous_frozen_java_replay_matches_all_four_hours(self):
        fixture = json.loads(Path(__file__).with_name("policy-trace.json").read_text())
        for case in fixture["sessions"]:
            timeline = Timeline()
            times = []
            for index, (start, end, stages) in enumerate(case["segments"]):
                context = str(index)
                timeline.observe(start, context, "PENDING", "SYSTEM")
                if stages[1] == "PRIVACY_BLOCKED":
                    timeline.observe(start, context, "GUARD", "SYSTEM")
                    continue
                changes = {start + 15: stages[:2]}
                if len(stages) == 4:
                    changes[start + 16] = stages[2:]
                ticks = set(range((start // 10 + 1) * 10, end, 10)) | changes.keys()
                for t in sorted(t for t in ticks if t < end):
                    source, label = changes.get(t, ("SYSTEM", "HEARTBEAT"))
                    label = {"RELATED": "STUDY", "UNRELATED": "OFF_TASK"}.get(label, label)
                    if timeline.observe(t, context, label, source):
                        times.append(t)
            self.assertEqual(case["expectedSeconds"], times)

    def test_strict_continuous_only_confirmed_content(self):
        p = Timeline()
        for t in range(0, 101, 10):
            self.assertIsNone(p.observe(t, "window", "OFF_TASK", "TAB_META"))
        for t in (100, 120, 145, 160):
            self.assertIsNone(p.observe(t, "window", "OFF_TASK", "CHROME_DOM"))
        self.assertEqual(
            p.observe(160.01, "window", "HEARTBEAT", "SYSTEM"), ("CHROME_DOM", "continuous")
        )
        self.assertIsNone(p.observe(170, "window", "OFF_TASK", "CHROME_DOM"))

    def test_guard_gap_and_delayed_followup(self):
        for interruption in ("GUARD", "UNAVAILABLE", "switch", "gap"):
            p = Timeline()
            for t in (0, 20, 40):
                self.assertIsNone(p.observe(t, "a", "OFF_TASK", "DESKTOP_APP_OCR"))
            if interruption == "gap":
                self.assertIsNone(p.observe(78, "a", "OFF_TASK", "DESKTOP_APP_OCR"))
            else:
                self.assertIsNone(
                    p.observe(
                        48,
                        "b" if interruption == "switch" else None,
                        "PENDING" if interruption == "switch" else interruption,
                        "SYSTEM",
                    )
                )
                self.assertIsNone(p.observe(50, "a", "OFF_TASK", "DESKTOP_APP_OCR"))
            self.assertIsNone(p.observe(79, "a", "HEARTBEAT", "SYSTEM"))

    def test_repeats_and_pending_window(self):
        p = Timeline()
        for t in (0, 20, 31):
            p.observe(t, "a", "OFF_TASK", "CHROME_DOM")
        p.observe(32, "a", "PENDING", "SYSTEM")
        p.observe(36, "a", "PENDING", "SYSTEM")
        p.observe(42, "a", "UNCERTAIN", "CHROME_DOM")
        p.observe(50, "b", "OFF_TASK", "CHROME_DOM")
        self.assertIsNone(p.observe(80, "b", "HEARTBEAT", "SYSTEM"))
        self.assertEqual(p.observe(81, "b", "HEARTBEAT", "SYSTEM"), ("CHROME_DOM", "repeated"))

    def test_cooldown_and_fresh_result(self):
        p = Timeline()
        for t in range(0, 71, 10):
            result = p.observe(t, "a", "OFF_TASK", "CHROME_DOM")
        self.assertEqual(result, ("CHROME_DOM", "continuous"))
        for t in range(80, 371, 10):
            result = p.observe(t, "b", "OFF_TASK" if t == 80 else "HEARTBEAT", "CHROME_DOM")
            self.assertIsNone(result)
        self.assertEqual(
            p.observe(371, "b", "OFF_TASK", "CHROME_DOM"), ("CHROME_DOM", "continuous")
        )

    def test_pending_does_not_count_unconfirmed_seconds(self):
        p = Timeline()
        for t in (0, 15, 30):
            p.observe(t, "a", "OFF_TASK", "DESKTOP_APP_OCR")
        p.observe(32, "a", "PENDING", "SYSTEM")
        p.observe(55, "a", "HEARTBEAT", "SYSTEM")
        p.observe(56, "a", "OFF_TASK", "DESKTOP_APP_OCR")
        self.assertIsNone(p.observe(84, "a", "HEARTBEAT", "SYSTEM"))
        self.assertEqual(
            p.observe(85, "a", "HEARTBEAT", "SYSTEM"), ("DESKTOP_APP_OCR", "continuous")
        )


class SessionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "history.sqlite3"
        self.clock = Clock()
        self.sessions = Sessions(self.path, clock=self.clock, wall_clock=lambda: 0)
        self.addCleanup(lambda: self.sessions.close())
        self.sid = self.sessions.start(10_168)["id"]

    def event(self):
        event = None
        for t in range(0, 71, 10):
            self.clock.t = t
            event = self.sessions.observe(
                self.sid, "private title must never be persisted", "UNRELATED", "CHROME_DOM"
            )
        self.assertIsNotNone(event)
        return event

    def test_human_denominator_edit_conflicts_and_zero(self):
        event = self.event()
        self.clock.t = 3600
        self.sessions.finish(self.sid)
        first = self.sessions.review(self.sid, event, "STUDY")
        self.assertIsNone(first["metrics"]["falsePerStudyHour"])
        m = self.sessions.set_interval(self.sid, 0, 3600, "STUDY")["metrics"]
        self.assertEqual((m["falsePerStudyHour"], m["complete"]), (1, True))
        m = self.sessions.set_interval(self.sid, 1800, 3600, "OFF_TASK")["metrics"]
        self.assertEqual((m["studySec"], m["falsePerStudyHour"]), (1800, 2))
        changed = self.sessions.review(self.sid, event, "OTHER")["metrics"]
        self.assertEqual((changed["conflicts"], changed["complete"]), (1, False))
        self.sessions.set_interval(self.sid, 60, 80, "OTHER")
        known = self.sessions.snapshot(self.sid)
        self.assertEqual(
            (
                known["metrics"]["complete"],
                known["metrics"]["falseStudy"],
                known["metrics"]["otherFalse"],
            ),
            (True, 0, 1),
        )
        partial = self.sessions.set_interval(self.sid, 20, 30, "UNKNOWN")
        self.assertEqual(
            (partial["metrics"]["unknownSec"], partial["metrics"]["complete"]), (10, False)
        )
        self.assertIsNone(self.sessions.observe(self.sid, "a", "OFF_TASK", "CHROME_DOM"))
        self.assertNotIn(b"private title", self.path.read_bytes())

    def test_no_capture_is_never_a_zero_false_alarm_rate(self):
        self.sessions.observe(self.sid, None, "UNAVAILABLE", "SYSTEM")
        for t in range(10, 3610, 10):
            self.clock.t = t
            self.sessions.observe(self.sid, None, "UNAVAILABLE", "SYSTEM")
        self.sessions.finish(self.sid)
        report = self.sessions.set_interval(self.sid, 0, 3600, "STUDY")
        m = report["metrics"]
        self.assertIsNone(m["falsePerStudyHour"])
        self.assertEqual(
            (m["observedSec"], m["studyCoverageByState"]["UNAVAILABLE"], m["rateProvisional"]),
            (0, 3600, True),
        )

    def test_coverage_excludes_dwell_guard_pending_and_stale(self):
        sequence = [
            (0, "a", "PENDING"),
            (15, "a", "UNCERTAIN"),
            (25, "a", "HEARTBEAT"),
            (30, "a", "PENDING"),
            (34, "a", "STUDY"),
            (40, None, "GUARD"),
            (50, None, "GUARD"),
            (100, "a", "STUDY"),
        ]
        for sec, ctx, label in sequence:
            self.clock.t = sec
            self.sessions.observe(self.sid, ctx, label, "APP_META")
        self.clock.t = 110
        self.sessions.finish(self.sid)
        snapshot = self.sessions.set_interval(self.sid, 12, 105, "STUDY")
        m = snapshot["metrics"]
        self.assertEqual(
            m["coverageByState"],
            {"DWELL": 15, "PENDING": 4, "OBSERVED": 31, "GUARD": 10, "UNAVAILABLE": 50},
        )
        self.assertEqual(
            m["studyCoverageByState"],
            {"DWELL": 3, "PENDING": 4, "OBSERVED": 26, "GUARD": 10, "UNAVAILABLE": 50},
        )
        self.assertTrue(m["rateProvisional"])
        self.assertEqual(m["falsePerStudyHour"], 0)

    def test_migrate_legacy_history_and_keep_each_sessions_model(self):
        path = self.path.with_name("legacy.sqlite3")
        with closing(sqlite3.connect(path)) as old, old:
            old.execute(
                "CREATE TABLE sessions (id TEXT PRIMARY KEY, started_at REAL NOT NULL, "
                "status TEXT NOT NULL, elapsed REAL NOT NULL DEFAULT 0)"
            )
            old.execute("INSERT INTO sessions VALUES ('old', 0, 'ended', 60)")
        sessions = Sessions(path)
        try:
            self.assertEqual(sessions.snapshot("old")["modelRows"], 4743)
            new = sessions.start(10_168)
            self.assertEqual(new["modelRows"], 10_168)
            sessions.finish(new["id"])
        finally:
            sessions.close()
        sessions = Sessions(path)
        try:
            self.assertEqual([s["modelRows"] for s in sessions.list_sessions()], [10_168, 4743])
        finally:
            sessions.close()

    def test_recovery_and_validation(self):
        self.clock.t = 100
        self.sessions.observe(self.sid, "a", "GUARD", "SYSTEM")
        with self.assertRaises(RuntimeError):
            Sessions(self.path)
        self.sessions.close()
        self.clock.t = 10000
        self.sessions = Sessions(self.path, clock=self.clock)
        snap = self.sessions.snapshot(self.sid)
        self.assertEqual((snap["durationSec"], snap["status"]), (100, "interrupted"))
        self.assertEqual(snap["metrics"]["unknownSec"], 100)
        with self.assertRaises(ValueError):
            self.sessions.set_interval(self.sid, 0, 101, "STUDY")
        with self.assertRaises(KeyError):
            self.sessions.review(self.sid, 999, "STUDY")
        new = self.sessions.start(10_168)
        self.assertNotEqual(self.sid, new["id"])
        self.assertEqual(self.sessions.start(10_168)["id"], new["id"])
        self.assertEqual(self.sessions.finish(self.sid)["status"], "interrupted")
        self.assertEqual(self.sessions.snapshot()["id"], new["id"])


if __name__ == "__main__":
    unittest.main()

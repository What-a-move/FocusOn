"""Local, notification-free session timeline and independent ground truth."""

import fcntl
import itertools
import math
import os
import sqlite3
import time
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from threading import RLock

TRUTHS = {"STUDY", "OFF_TASK", "OTHER", "UNKNOWN"}
SOURCES = {"APP_META", "TAB_META", "CHROME_DOM", "DESKTOP_APP_OCR", "SYSTEM"}
GAP_LIMIT = 35.0
COVERAGE_STATES = ("OBSERVED", "DWELL", "PENDING", "GUARD", "UNAVAILABLE")


class Timeline:
    """PR 295 v1 NORMAL. Only an eligible focus visit can yield a shadow event."""

    def __init__(self):
        self.context = None
        self.last_seen = 0.0
        self.evidence_at = None
        self.cooldown_until = None
        self.spans = []
        self.episode = 0
        self.current_episode = None
        self.emitted = False
        self.source = "SYSTEM"

    def stop_span(self, at):
        if self.spans and self.spans[-1][2] is None:
            self.spans[-1][2] = at

    def interrupt(self, at):
        self.stop_span(at)
        self.current_episode = None
        self.evidence_at = None

    def observe(self, now, context, label, source):
        now = max(now, self.last_seen)
        if now - self.last_seen > GAP_LIMIT:
            self.interrupt(self.last_seen)
            self.context = None
        self.last_seen = now
        if self.context != context:
            self.interrupt(now)
            self.context = context
            self.emitted = False
        if label in {"GUARD", "UNAVAILABLE"}:
            self.interrupt(now)
            self.context = None
            return None
        if label == "PENDING":
            self.stop_span(now)
            return None
        if label != "HEARTBEAT":
            if label != "OFF_TASK" or source not in {"CHROME_DOM", "DESKTOP_APP_OCR"}:
                self.interrupt(now)
                return None
            self.source = source
            self.evidence_at = now
            if self.current_episode is None:
                self.episode += 1
                self.current_episode = self.episode
            if not self.spans or self.spans[-1][2] is not None:
                self.spans.append([self.current_episode, now, None])
        if self.current_episode is None or not self.spans or self.spans[-1][2] is not None:
            return None

        left = now - 600
        self.spans = [
            s for s in self.spans if s[2] is None or s[0] == self.current_episode or s[2] > left
        ]
        if self.emitted or (
            self.cooldown_until is not None
            and (now < self.cooldown_until or self.evidence_at < self.cooldown_until)
        ):
            return None
        continuous = 0.0
        rolling = {}
        for number, start, end in self.spans:
            stop = now if end is None else end
            if number == self.current_episode:
                continuous += stop - start
            rolling[number] = rolling.get(number, 0.0) + max(0.0, stop - max(start, left))
        if continuous > 60:
            reason = "continuous"
        elif sum(seconds > 30 for seconds in rolling.values()) >= 2:
            reason = "repeated"
        else:
            return None
        self.emitted = True
        self.cooldown_until = now + 300
        return self.source, reason


def coverage(annotations, duration):
    """Return contiguous, merged, half-open intervals including unlabeled time."""
    intervals = []
    cursor = 0
    for start, end, truth in annotations:
        if start > cursor:
            intervals.append((cursor, start, "UNKNOWN"))
        if end > start:
            intervals.append((start, end, truth))
        cursor = end
    if duration > cursor:
        intervals.append((cursor, duration, "UNKNOWN"))
    return [
        {"startSec": span[0][0], "endSec": span[-1][1], "truth": key}
        for key, rows in itertools.groupby(intervals, lambda r: r[2])
        if (span := list(rows))
    ]


def metrics(intervals, events, status):
    totals = {k: 0 for k in TRUTHS}
    for interval in intervals:
        totals[interval["truth"]] += interval["endSec"] - interval["startSec"]
    for event in events:
        at = event["atSec"]
        activity = "UNKNOWN"
        for span in intervals:
            if span["startSec"] <= at < span["endSec"]:
                activity = span["truth"]
                break
        # A click on Finish can share the event's last displayed second.
        if intervals and at == intervals[-1]["endSec"]:
            activity = intervals[-1]["truth"]
        answer = event["answer"]
        event["intervalTruth"] = activity
        event["conflict"] = (
            answer in {"STUDY", "OFF_TASK", "OTHER"}
            and activity != "UNKNOWN"
            and answer != activity
        )

    study = totals["STUDY"]
    false_study = sum(e["answer"] == e["intervalTruth"] == "STUDY" for e in events)
    unreviewed = sum(e["answer"] is None for e in events)
    unsure = sum(e["answer"] == "UNKNOWN" for e in events)
    conflicts = sum(e["conflict"] for e in events)
    return {
        "studySec": study,
        "offSec": totals["OFF_TASK"],
        "otherSec": totals["OTHER"],
        "unknownSec": totals["UNKNOWN"],
        "falseStudy": false_study,
        "reportedFalseStudy": sum(e["answer"] == "STUDY" for e in events),
        "correctOff": sum(e["answer"] == "OFF_TASK" for e in events),
        "otherFalse": sum(e["answer"] == "OTHER" for e in events),
        "unreviewed": unreviewed,
        "unsure": unsure,
        "conflicts": conflicts,
        "falsePerStudyHour": (false_study * 3600 / study) if study else None,
        "complete": status != "active"
        and totals["UNKNOWN"] == unreviewed == unsure == conflicts == 0,
    }


def observed_metrics(result, measured):
    """Intersect independently labeled study with time the collector actually classified."""
    duration = result["durationSec"]
    counts = {key: 0 for key in COVERAGE_STATES}
    in_study = counts.copy()
    study = [v for v in result["intervals"] if v["truth"] == "STUDY"]
    cursor = 0
    study_idx = 0
    # Missing spans can remain after an interrupted process or when migrating an old history.
    for start, end, kind in itertools.chain(measured, [(duration, duration, "UNAVAILABLE")]):
        for lo, hi, key in ((cursor, start, "UNAVAILABLE"), (start, end, kind)):
            if hi <= lo:
                continue
            counts[key] += hi - lo
            while study_idx < len(study) and study[study_idx]["endSec"] <= lo:
                study_idx += 1
            idx = study_idx
            while idx < len(study) and study[idx]["startSec"] < hi:
                span = study[idx]
                in_study[key] += min(hi, span["endSec"]) - max(lo, span["startSec"])
                idx += 1
        cursor = end
    m = result["metrics"]
    observed_study = in_study["OBSERVED"]
    m.update(
        observedSec=counts["OBSERVED"],
        observedStudySec=observed_study,
        unobservedStudySec=m["studySec"] - observed_study,
        coverageByState=counts,
        studyCoverageByState=in_study,
        studyCoveragePct=(100 * observed_study / m["studySec"]) if m["studySec"] else None,
        rateProvisional=not m["complete"] or not observed_study or observed_study < m["studySec"],
        falsePerObservedStudyHour=(3600 * m["falseStudy"] / observed_study)
        if observed_study
        else None,
    )
    if not observed_study:
        m["falsePerStudyHour"] = None


class Sessions:
    """One local server owns this database. Persist timing and human decisions only."""

    def __init__(self, db_path, *, clock=time.monotonic, wall_clock=time.time):
        self._clock = clock
        self._wall = wall_clock
        self._origin = 0.0
        self._timeline = Timeline()
        self._coverage_kind = "DWELL"
        self._lock = RLock()
        self._owner = None
        if str(db_path) != ":memory:":
            file = Path(db_path)
            file.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            self._owner = open(str(file) + ".lock", "a+b")
            try:
                fcntl.flock(self._owner, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                self._owner.close()
                raise RuntimeError("Another local pilot already owns this database.") from None
            fd = os.open(file, os.O_CREAT | os.O_RDWR, 0o600)
            os.close(fd)
        self.db = sqlite3.connect(db_path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS sessions(
              id TEXT PRIMARY KEY, started_at REAL NOT NULL,
              status TEXT NOT NULL, elapsed REAL NOT NULL DEFAULT 0);
            CREATE UNIQUE INDEX IF NOT EXISTS single_active ON sessions(status) WHERE status='active';
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id),
              at_sec INTEGER NOT NULL, source TEXT NOT NULL, reason TEXT NOT NULL, answer TEXT);
            CREATE INDEX IF NOT EXISTS events_session ON events(session_id, at_sec);
            CREATE TABLE IF NOT EXISTS coverage_spans(
              session_id TEXT NOT NULL REFERENCES sessions(id), start_sec INTEGER NOT NULL,
              end_sec INTEGER NOT NULL, kind TEXT NOT NULL,
              PRIMARY KEY(session_id, start_sec), CHECK(end_sec > start_sec));
            CREATE TABLE IF NOT EXISTS annotations(
              session_id TEXT NOT NULL REFERENCES sessions(id), start_sec INTEGER NOT NULL,
              end_sec INTEGER NOT NULL, truth TEXT NOT NULL,
              PRIMARY KEY(session_id, start_sec), CHECK(end_sec > start_sec));
        """)
        with self.db:
            self.db.execute("UPDATE sessions SET status='interrupted' WHERE status='active'")

    def close(self):
        with self._lock:
            self.db.close()
            if self._owner:
                self._owner.close()

    @contextmanager
    def _transaction(self):
        with self._lock, self.db:
            yield

    def _get(self, sid=None):
        if sid is None:
            return self.db.execute("SELECT * FROM sessions ORDER BY rowid DESC LIMIT 1").fetchone()
        session = self.db.execute("SELECT * FROM sessions WHERE id=?", (sid,)).fetchone()
        if session is None:
            raise KeyError("Unknown session")
        return session

    def _elapsed(self, row):
        return (
            max(row["elapsed"], self._clock() - self._origin)
            if row["status"] == "active"
            else row["elapsed"]
        )

    def _brief(self, row):
        return {
            "id": row["id"],
            "status": row["status"],
            "startedAt": datetime.fromtimestamp(row["started_at"], timezone.utc).isoformat(),
            "durationSec": math.floor(self._elapsed(row)),
        }

    def _tail(self, now):
        start = self._timeline.last_seen
        kind = self._coverage_kind if now - start <= GAP_LIMIT else "UNAVAILABLE"
        return math.floor(start), math.floor(now), kind

    def _seal_coverage(self, sid, now):
        start, end, kind = self._tail(now)
        if end <= start:
            return
        last = self.db.execute(
            "SELECT start_sec,end_sec,kind FROM coverage_spans WHERE session_id=? ORDER BY start_sec DESC LIMIT 1",
            (sid,),
        ).fetchone()
        if last and last["end_sec"] == start and last["kind"] == kind:
            self.db.execute(
                "UPDATE coverage_spans SET end_sec=? WHERE session_id=? AND start_sec=?",
                (end, sid, last["start_sec"]),
            )
        else:
            self.db.execute("INSERT INTO coverage_spans VALUES(?,?,?,?)", (sid, start, end, kind))

    def list_sessions(self):
        with self._lock:
            return [
                self._brief(r)
                for r in self.db.execute("SELECT * FROM sessions ORDER BY rowid DESC")
            ]

    def snapshot(self, session_id=None):
        with self._lock:
            row = self._get(session_id)
            if row is None:
                return None
            result = self._brief(row)
            sid = row["id"]
            result["events"] = [
                dict(e)
                for e in self.db.execute(
                    "SELECT id, at_sec AS atSec, source, reason, answer FROM events WHERE session_id=? ORDER BY at_sec,id",
                    (sid,),
                )
            ]
            result["intervals"] = coverage(
                self.db.execute(
                    "SELECT start_sec,end_sec,truth FROM annotations WHERE session_id=? ORDER BY start_sec",
                    (sid,),
                ),
                result["durationSec"],
            )
            result["metrics"] = metrics(result["intervals"], result["events"], result["status"])
            measured = self.db.execute(
                "SELECT start_sec,end_sec,kind FROM coverage_spans WHERE session_id=? ORDER BY start_sec",
                (sid,),
            )
            tail = [self._tail(result["durationSec"])] if row["status"] == "active" else []
            observed_metrics(result, itertools.chain(measured, tail))
            return result

    def start(self):
        with self._transaction():
            existing = self._get()
            if existing and existing["status"] == "active":
                return self.snapshot(existing["id"])
            sid = uuid.uuid4().hex
            self.db.execute(
                "INSERT INTO sessions(id,started_at,status) VALUES(?,?,'active')",
                (sid, self._wall()),
            )
            self._origin = self._clock()
            self._coverage_kind = "DWELL"
            self._timeline = Timeline()
            return self.snapshot(sid)

    def finish(self, session_id=None):
        with self._transaction():
            row = self._get(session_id)
            if row is None:
                raise KeyError("Unknown session")
            if row["status"] == "active":
                now = self._elapsed(row)
                self._seal_coverage(row["id"], now)
                self.db.execute(
                    "UPDATE sessions SET status='ended', elapsed=? WHERE id=?", (now, row["id"])
                )
                self._timeline = Timeline()
            return self.snapshot(row["id"])

    def observe(self, session_id, context, label, source):
        label = {
            "RELATED": "STUDY",
            "UNRELATED": "OFF_TASK",
            "EXCLUDED": "GUARD",
            "PRIVACY_BLOCKED": "GUARD",
        }.get(label, label)
        if source not in SOURCES or label not in {
            "STUDY",
            "OFF_TASK",
            "UNCERTAIN",
            "PENDING",
            "GUARD",
            "UNAVAILABLE",
            "HEARTBEAT",
        }:
            raise ValueError("Unknown decision or source")
        if label not in {"GUARD", "UNAVAILABLE"} and not context:
            raise ValueError("A current context is required")
        with self._transaction():
            row = self._get(session_id)
            if row["status"] != "active":
                return None
            now = self._elapsed(row)
            self.db.execute("UPDATE sessions SET elapsed=? WHERE id=?", (now, session_id))
            self._seal_coverage(session_id, now)
            if label == "PENDING":
                self._coverage_kind = "DWELL" if self._timeline.context != context else "PENDING"
            elif label in ("GUARD", "UNAVAILABLE"):
                self._coverage_kind = label
            elif label != "HEARTBEAT":
                self._coverage_kind = "OBSERVED"
            elif self._timeline.context != context or now - self._timeline.last_seen > GAP_LIMIT:
                self._coverage_kind = "UNAVAILABLE"
            candidate = self._timeline.observe(now, context, label, source)
            if candidate is None:
                return None
            return self.db.execute(
                "INSERT INTO events(session_id,at_sec,source,reason) VALUES(?,?,?,?)",
                (session_id, math.floor(now), *candidate),
            ).lastrowid

    def review(self, session_id, event_id, answer):
        if answer is not None and answer not in TRUTHS:
            raise ValueError("Unknown event answer")
        with self._transaction():
            changed = self.db.execute(
                "UPDATE events SET answer=? WHERE session_id=? AND id=?",
                (answer, session_id, event_id),
            )
            if changed.rowcount != 1:
                raise KeyError("Unknown candidate")
            return self.snapshot(session_id)

    def set_interval(self, session_id, start_sec, end_sec, truth):
        if (
            type(start_sec) is not int
            or type(end_sec) is not int
            or start_sec < 0
            or end_sec <= start_sec
            or truth not in TRUTHS
        ):
            raise ValueError("Invalid time range or activity")
        with self._transaction():
            row = self._get(session_id)
            duration = math.floor(self._elapsed(row))
            if end_sec > duration:
                raise ValueError("Time range extends beyond the session")
            if row["status"] == "active":
                self.db.execute(
                    "UPDATE sessions SET elapsed=MAX(elapsed,?) WHERE id=?", (end_sec, session_id)
                )
            old = self.db.execute(
                "SELECT start_sec,end_sec,truth FROM annotations WHERE session_id=? ORDER BY start_sec",
                (session_id,),
            )
            next_spans = []
            for a, b, prior in old:
                if a < start_sec:
                    next_spans.append((a, min(b, start_sec), prior))
                if b > end_sec:
                    next_spans.append((max(a, end_sec), b, prior))
            if truth != "UNKNOWN":
                next_spans.append((start_sec, end_sec, truth))
            merged = coverage(sorted(next_spans), duration)
            self.db.execute("DELETE FROM annotations WHERE session_id=?", (session_id,))
            self.db.executemany(
                "INSERT INTO annotations(session_id,start_sec,end_sec,truth) VALUES(?,?,?,?)",
                [
                    (session_id, r["startSec"], r["endSec"], r["truth"])
                    for r in merged
                    if r["truth"] != "UNKNOWN"
                ],
            )
            return self.snapshot(session_id)

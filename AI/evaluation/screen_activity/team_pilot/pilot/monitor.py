"""Volatile active-window observation. Only coarse outcomes reach the session store."""

import json
import subprocess
import threading
import time
from pathlib import Path

NATIVE = Path(__file__).resolve().parent / "native" / "observer"
DWELL, HEARTBEAT, REFRESH, BRIDGE_TTL = 15, 10, 30, 12


def native(*args):
    try:
        p = subprocess.run([str(NATIVE), *args], capture_output=True, timeout=12, check=True)
        value = json.loads(p.stdout)
        return value if isinstance(value, dict) else {"state": "unavailable"}
    except (OSError, ValueError, subprocess.SubprocessError):
        return {"state": "unavailable", "reason": "OBSERVER_UNAVAILABLE"}


class Monitor:
    def __init__(self, sessions, model, reader=native, clock=time.monotonic):
        self.sessions, self.model, self.reader, self.clock = sessions, model, reader, clock
        self.lock = threading.RLock()
        self.sid = None
        self.chrome = None
        self.front = None
        self.front_at = 0
        self.want = None
        self.paired_at = None
        self.status = "idle"
        self.context = None
        self.stop = threading.Event()
        self.thread = threading.Thread(target=self.run, name="foreground-observer", daemon=True)
        self.reset_context()

    def reset_context(self):
        self.context = None
        self.entered = self.last_eval = self.last_beat = 0
        self.label = "PENDING"
        self.source = "SYSTEM"
        self.want = None

    def activate(self, sid):
        with self.lock:
            self.sid = sid
            self.chrome = self.front = None
            self.reset_context()
            self.status = "waiting" if sid else "idle"

    def poll(self):
        with self.lock:
            self.paired_at = self.clock()
            active = bool(
                self.sid and self.front == "chrome" and self.clock() - self.front_at < BRIDGE_TTL
            )
            return dict(
                active=active,
                sessionId=self.sid if active else None,
                wantDom=self.want if active else None,
            )

    def receive(self, obs):
        with self.lock:
            if (
                not self.sid
                or self.sid != obs["sessionId"]
                or self.front != "chrome"
                or self.clock() - self.front_at >= BRIDGE_TTL
            ):
                return
            if obs.get("text") is not None and self.want != obs["context"]:
                return
            self.chrome = (self.clock(), obs)

    def diagnostic(self):
        with self.lock:
            return {
                "status": self.status,
                "bridgeConnected": self.paired_at is not None
                and self.clock() - self.paired_at < 40,
            }

    def current(self, sid, ctx=None):
        return sid == self.sid and (not ctx or self.context == ctx)

    def observe(self, sid, ctx, label, source):
        if self.current(sid, ctx):
            self.sessions.observe(sid, ctx, label, source)

    def tick(self):
        with self.lock:
            sid = self.sid
        if not sid:
            return
        front = self.reader("meta")
        now = self.clock()
        with self.lock:
            if not self.current(sid):
                return
            previous_front = self.front
            self.front, self.front_at = front.get("state"), now
            if self.front != "chrome" or previous_front != "chrome":
                self.chrome = None
            is_chrome = self.front == "chrome"
            chrome_frame = self.chrome
            if is_chrome and chrome_frame and now - chrome_frame[0] < BRIDGE_TTL:
                screen = chrome_frame[1]
            elif is_chrome:
                screen = {"state": "unavailable", "reason": "EXTENSION_WAIT"}
            else:
                screen = front
            state = screen.get("state")
            if state not in ("desktop", "chrome") or not screen.get("context"):
                self.reset_context()
                self.status = (
                    "protected"
                    if state == "guard"
                    else "extension_wait"
                    if is_chrome
                    else "unavailable"
                )
                self.sessions.observe(
                    sid,
                    "protected" if state == "guard" else "unavailable",
                    "GUARD" if state == "guard" else "UNAVAILABLE",
                    "SYSTEM",
                )
                return
            ctx = ("tab-" if is_chrome else "app-") + screen["context"]
            if ctx != self.context:
                self.reset_context()
                self.context, self.entered = ctx, now
                self.sessions.observe(sid, ctx, "PENDING", "SYSTEM")
                self.status = "dwell"
            if now - self.entered < DWELL:
                return
            if self.label == "GUARD":
                if now - self.last_beat >= HEARTBEAT:
                    self.observe(sid, ctx, "GUARD", self.source)
                    self.last_beat = now
                return
            if self.last_eval and now - self.last_eval < REFRESH:
                if now - self.last_beat >= HEARTBEAT:
                    self.observe(sid, ctx, "HEARTBEAT", self.source)
                    self.last_beat = now
                return
            waiting = self.want is not None
            if is_chrome and waiting and screen.get("text") is None:
                if now - self.wait_since < BRIDGE_TTL:
                    return
                self.want = None
                self.label, self.source = "UNAVAILABLE", "SYSTEM"
                self.last_eval = self.last_beat = now
                self.observe(sid, ctx, self.label, self.source)
                self.status = "no_content"
                return
        meta = screen["metadata"]
        source = "TAB_META" if is_chrome else "APP_META"
        label = (
            "UNCERTAIN" if is_chrome and waiting else self.model.classify(sid, ctx, source, meta)
        )
        with self.lock:
            if not self.current(sid, ctx):
                return
        if label == "UNCERTAIN":
            if is_chrome and not waiting:
                with self.lock:
                    if self.current(sid, ctx):
                        self.want = screen["context"]
                        self.wait_since = self.clock()
                        self.status = "awaiting_dom"
                        self.observe(sid, ctx, "PENDING", source)
                return
            if is_chrome:
                body = screen.get("text", "")
            else:
                captured = self.reader("ocr", screen["context"])
                if (
                    captured.get("state") == "desktop"
                    and captured.get("context") == screen["context"]
                ):
                    body = captured.get("text", "")
                else:
                    body = ""
                    label = "GUARD" if captured.get("state") == "guard" else "UNAVAILABLE"
            with self.lock:
                if not self.current(sid, ctx):
                    return
            if body:
                source = "CHROME_DOM" if is_chrome else "DESKTOP_APP_OCR"
                label = self.model.classify(sid, ctx, source, meta, body)
        with self.lock:
            if not self.current(sid, ctx):
                return
        check = self.reader("meta")
        with self.lock:
            if not self.current(sid, ctx):
                return
            same_front = front.get("context") == check.get("context") and front.get(
                "state"
            ) == check.get("state")
            if is_chrome:
                same_front = bool(
                    same_front
                    and self.chrome
                    and self.chrome[1].get("state") == "chrome"
                    and self.chrome[1]["context"] == screen["context"]
                    and self.clock() - self.chrome[0] < BRIDGE_TTL
                )
            if not same_front:
                self.sessions.observe(sid, ctx, "UNAVAILABLE", "SYSTEM")
                self.reset_context()
                self.status = "waiting"
                return
            self.label, self.source = label, source
            self.want = None
            self.last_eval = self.last_beat = self.clock()
            self.observe(sid, ctx, label, source)
            self.status = {
                "STUDY": "study",
                "UNCERTAIN": "uncertain",
                "OFF_TASK": "off",
                "GUARD": "protected",
            }.get(label, "unavailable")
            if is_chrome and self.chrome and "text" in self.chrome[1]:
                self.chrome = (
                    self.chrome[0],
                    {k: v for k, v in self.chrome[1].items() if k != "text"},
                )

    def run(self):
        while not self.stop.is_set():
            try:
                self.tick()
            except Exception:
                # Do not log exceptions containing observations or text from third-party libraries.
                with self.lock:
                    if self.sid:
                        self.sessions.observe(self.sid, "unavailable", "UNAVAILABLE", "SYSTEM")
                    self.reset_context()
                    self.status = "unavailable"
            self.stop.wait(1.5)

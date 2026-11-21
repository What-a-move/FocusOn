"""Loopback-only team pilot. Starting the server does not start observation."""

import argparse
import json
import os
from pathlib import Path
import re
import secrets
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs

from pilot.model import LocalModel
from pilot.monitor import Monitor, native
from pilot.session import Sessions

PORT = 18743
ORIGIN = f"http://127.0.0.1:{PORT}"
STORE = (
    Path(
        os.environ.get(
            "FOCUSON_PILOT_DATA_DIR",
            str(Path.home() / "Library/Application Support/FocusOn Local Pilot"),
        )
    )
    .expanduser()
    .resolve()
)
WEB = Path(__file__).parent / "pilot/web"
ASSETS = {
    "/": ("index.html", "text/html"),
    "/pilot.js": ("pilot.js", "text/javascript"),
    "/pilot.css": ("pilot.css", "text/css"),
}
EXT_ORIGIN = re.compile(r"chrome-extension://[a-p]{32}\Z")


def pairing_secret(folder):
    path = folder / "extension.key"
    if not path.exists():
        with path.open("x") as f:
            f.write(secrets.token_urlsafe(32))
    return path.read_text().strip()


def observation(data):
    state = data.get("state")
    sid = data.get("sessionId")
    ctx = data.get("context")
    if state not in {"chrome", "guard", "unavailable"} or not isinstance(sid, str) or len(sid) > 96:
        raise ValueError()
    if state != "chrome":
        return dict(state=state, sessionId=sid, context="guarded")
    if not isinstance(ctx, str) or not re.fullmatch(r"[A-Za-z0-9_-]{12,128}", ctx):
        raise ValueError()
    m = data.get("metadata")
    if not isinstance(m, dict):
        raise ValueError()
    title = m.get("pageTitle")
    host = m.get("urlHost")
    if (
        not isinstance(title, str)
        or len(title) > 1024
        or not isinstance(host, str)
        or not re.fullmatch(r"[a-zA-Z0-9.\-:\[\]]{1,255}", host)
    ):
        raise ValueError()
    result = dict(
        state=state,
        sessionId=sid,
        context=ctx,
        metadata=dict(appName="Google Chrome", pageTitle=title, urlHost=host),
    )
    if "text" in data:
        body = data["text"]
        if not isinstance(body, str) or len(body) > 12000:
            raise ValueError()
        result["text"] = body
    return result


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, status, payload=b"", mime="application/json", extension=False):
        if not isinstance(payload, bytes):
            payload = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", mime + "; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; form-action 'none'; base-uri 'none'; object-src 'none'",
        )
        origin = self.headers.get("Origin", "")
        if extension and EXT_ORIGIN.fullmatch(origin):
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Access-Control-Allow-Headers", "X-Pilot-Token, Content-Type")
            self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
            self.send_header("Vary", "Origin")
        self.end_headers()
        self.wfile.write(payload)

    def valid_host(self):
        return self.headers.get("Host") == self.server.host_header

    def do_OPTIONS(self):
        if not self.valid_host() or self.path not in ("/bridge/poll", "/bridge/observation"):
            return self.send(403, {})
        self.send(204, b"", extension=True)

    def do_GET(self):
        if not self.valid_host():
            return self.send(403, {})
        parts = urlsplit(self.path)
        if parts.path in ASSETS:
            name, mime = ASSETS[parts.path]
            return self.send(200, (WEB / name).read_bytes(), mime)
        if parts.path != "/api/state" or self.headers.get("X-Pilot-UI") != "1":
            return self.send(404, {})
        try:
            sid = parse_qs(parts.query).get("id", [None])[0]
            store = self.server.sessions
            result = dict(
                session=store.snapshot(sid),
                history=store.list_sessions(),
                monitor=self.server.monitor.diagnostic(),
            )
            self.send(200, result)
        except KeyError:
            self.send(404, {"error": "세션을 찾을 수 없습니다."})

    def do_POST(self):
        if not self.valid_host():
            return self.send(403, {})
        path = self.path
        bridge = path in ("/bridge/poll", "/bridge/observation")
        origin = self.headers.get("Origin", "")
        if bridge:
            supplied = self.headers.get("X-Pilot-Token", "")
            if (origin and not EXT_ORIGIN.fullmatch(origin)) or not secrets.compare_digest(
                supplied.encode(), self.server.key.encode()
            ):
                return self.send(403, {})
        elif self.headers.get("X-Pilot-UI") != "1" or (
            origin and origin != "http://" + self.server.host_header
        ):
            return self.send(403, {})
        try:
            size = int(self.headers.get("Content-Length", 0))
            if size < 0 or size > 65536 or self.headers.get_content_type() != "application/json":
                return self.send(400, {})
            data = json.loads(self.rfile.read(size))
            if not isinstance(data, dict):
                raise ValueError()
            monitor, store = self.server.monitor, self.server.sessions
            if path == "/bridge/poll":
                out = monitor.poll()
            elif path == "/bridge/observation":
                monitor.receive(observation(data))
                out = {"ok": True}
            elif path == "/api/start":
                if data.get("consent") is not True:
                    raise ValueError()
                with monitor.lock:
                    out = store.start()
                    if monitor.sid != out["id"]:
                        monitor.activate(out["id"])
            elif path == "/api/end":
                with monitor.lock:
                    sid = data["sessionId"]
                    out = store.finish(sid)
                    if monitor.sid == sid:
                        monitor.activate(None)
            elif path == "/api/review":
                out = store.review(data["sessionId"], data["eventId"], data["answer"])
            elif path == "/api/interval":
                out = store.set_interval(
                    data["sessionId"], data["startSec"], data["endSec"], data["truth"]
                )
            elif path == "/api/pairing":
                out = {"token": self.server.key}
            elif path == "/api/permission":
                out = native("request-permission")
            elif path == "/api/permission-status":
                out = native("status")
            else:
                return self.send(404, {})
            self.send(200, out, extension=bridge)
        except (ValueError, KeyError, TypeError, OverflowError):
            self.send(
                400,
                {"error": "선택한 세션, 시간 구간 또는 응답을 확인해 주세요."},
                extension=bridge,
            )
        except (BrokenPipeError, ConnectionResetError):
            pass


class PilotHTTP(ThreadingHTTPServer):
    daemon_threads = True

    def handle_error(self, request, client_address):
        # Exceptions must never serialize captured text into a log.
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-open", action="store_true", help="시작 시 브라우저 열기 생략")
    args = parser.parse_args()
    os.umask(0o077)
    STORE.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        server = PilotHTTP(("127.0.0.1", PORT), Handler)
    except OSError:
        sys.exit("18743 포트를 사용 중입니다. 이미 실행한 파일의 창을 확인해 주세요.")
    with server:
        server.host_header = f"127.0.0.1:{PORT}"
        server.key = pairing_secret(STORE)
        server.sessions = Sessions(STORE / "sessions.sqlite3")
        try:
            model = LocalModel()
            monitor = server.monitor = Monitor(server.sessions, model)
            monitor.thread.start()
            sys.stdout.write(f"로컬 테스트 화면 {ORIGIN}\n")
            sys.stdout.flush()
            if not args.no_open:
                webbrowser.open(ORIGIN)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
            finally:
                monitor.stop.set()
                monitor.thread.join()
        finally:
            server.sessions.close()


if __name__ == "__main__":
    main()

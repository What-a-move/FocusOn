#!/usr/bin/env python3
"""Prepare an unused Google code + PKCE request for local Swagger; never exchange it."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import sys
import tempfile
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlencode, urlsplit
import uuid
import webbrowser


class PreparationError(Exception):
    """Safe user-facing failure without OAuth response contents."""


def create_pkce():
    verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(verifier.encode("ascii")).digest()
    ).rstrip(b"=").decode("ascii")
    return verifier, challenge


def callback_values(target, expected_state):
    """Reject ambiguous responses and validate state before accepting code or error."""
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc or parsed.path != "/" or parsed.fragment:
        raise ValueError("Invalid callback path")
    values = parse_qs(parsed.query, keep_blank_values=True, strict_parsing=True,
                      max_num_fields=16)
    if any(len(value) != 1 for value in values.values()):
        raise ValueError("Duplicate callback parameter")
    state = values.get("state", [""])[0]
    if not secrets.compare_digest(state.encode("utf-8"), expected_state.encode("utf-8")):
        raise ValueError("State mismatch")
    if "error" in values:
        if "code" in values:
            raise ValueError("Ambiguous OAuth response")
        raise PreparationError("Google 인증이 거절되거나 취소됐습니다. 다시 실행해 주세요.")
    code = values.get("code", [""])[0]
    if not code.strip() or len(code) > 4096:
        raise ValueError("Missing or oversized code")
    return code


def make_handler(expected_state):
    class CallbackHandler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(2)

        def log_message(self, *_args):
            # 기본 HTTP 로그는 query의 code/state를 기록하므로 사용하지 않는다.
            pass

        def do_GET(self):
            if self.headers.get_all("Host") != [f"127.0.0.1:{self.server.server_port}"]:
                self.respond(400, "Invalid callback host.")
                return
            try:
                if len(self.path) > 8192:
                    raise ValueError("Oversized callback")
                code = callback_values(self.path, expected_state)
            except ValueError:
                self.respond(400, "Invalid callback. Return to the Google sign-in window.")
                return
            except PreparationError as error:
                self.server.failure = error
                self.server.completed = True
                self.respond(400, "Sign-in cancelled. You can close this window.")
                return
            self.server.code = code
            self.server.completed = True
            self.respond(200, "Code prepared. Close this window and return to the terminal.")

        def respond(self, status, message):
            body = message.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(body)

    return CallbackHandler


def prepare_request(client_id, installation_id, device_name, timeout):
    verifier, challenge = create_pkce()
    state = secrets.token_urlsafe(32)
    # Google에 전달한 주소와 최종 JSON의 redirectUri를 같은 문자열로 사용한다.
    with HTTPServer(("127.0.0.1", 0), make_handler(state)) as server:
        server.timeout = 0.5
        server.completed = False
        server.failure = None
        redirect_uri = f"http://127.0.0.1:{server.server_port}/"
        url = "https://accounts.google.com/o/oauth2/v2/auth?" + urlencode({
            "client_id": client_id, "redirect_uri": redirect_uri,
            "response_type": "code", "scope": "openid", "state": state,
            "code_challenge": challenge, "code_challenge_method": "S256",
            "prompt": "select_account",
        })
        if not webbrowser.open(url, new=1):
            raise PreparationError("시스템 브라우저를 열 수 없습니다. 브라우저 설정 후 다시 실행해 주세요.")
        print("시스템 브라우저에서 Google 테스트 계정으로 인증해 주세요. 인증 값은 출력하지 않습니다.")
        deadline = time.monotonic() + timeout
        while not server.completed:
            if time.monotonic() >= deadline:
                raise PreparationError("인증 대기 시간이 끝났습니다. 다시 실행해 주세요.")
            server.handle_request()
        if server.failure:
            raise server.failure
        return {
            "authorizationCode": server.code, "codeVerifier": verifier,
            "state": state, "redirectUri": redirect_uri,
            "device": {"installationId": installation_id, "deviceName": device_name,
                       "platform": "MACOS"},
        }


def write_private_request(body):
    fd, name = tempfile.mkstemp(prefix="focuson-swagger-exchange-", suffix=".json")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            os.fchmod(output.fileno(), 0o600)
            json.dump(body, output, ensure_ascii=False, indent=2)
            output.write("\n")
    except BaseException:
        Path(name).unlink(missing_ok=True)
        raise
    return Path(name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installation-id", default="swagger-qa-" + str(uuid.uuid4()),
                        help="실제 Desktop과 다른 QA 전용 식별자 (기본값: 실행마다 무작위)")
    parser.add_argument("--device-name", default="Swagger QA Desktop")
    parser.add_argument("--timeout", type=int, default=300, help="인증 대기 초, 1~600 (기본: 300)")
    args = parser.parse_args()
    args.installation_id = args.installation_id.strip()
    args.device_name = args.device_name.strip()
    client_id = os.environ.get("GOOGLE_DESKTOP_CLIENT_ID", "").strip()
    if not client_id.endswith(".apps.googleusercontent.com"):
        parser.exit(1, "GOOGLE_DESKTOP_CLIENT_ID 환경 변수를 설정해 주세요.\n")
    # Java @Size는 Python 문자 수가 아닌 UTF-16 코드 단위 길이를 검사한다.
    installation_units = len(args.installation_id.encode("utf-16-le")) // 2
    name_units = len(args.device_name.encode("utf-16-le")) // 2
    if not 1 <= args.timeout <= 600 or not 1 <= installation_units <= 128 \
            or not 1 <= name_units <= 80:
        parser.exit(1, "timeout·installation-id·device-name 범위를 확인해 주세요.\n")
    try:
        body = prepare_request(client_id, args.installation_id, args.device_name, args.timeout)
        path = write_private_request(body)
    except PreparationError as error:
        print(str(error), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("취소했습니다. Google 인증을 다시 시작해 주세요.", file=sys.stderr)
        return 1
    except Exception:
        # OS/브라우저 예외에도 URL·인증 값이 들어갈 수 있어 traceback을 출력하지 않는다.
        print("요청 준비에 실패했습니다. 로컬 포트·브라우저·임시 파일 권한을 확인해 주세요.", file=sys.stderr)
        return 1
    print(f"Swagger exchange 요청 파일: {path}")
    print("파일 내용을 요청 body에 붙여 넣어 한 번만 실행하세요. 완료·실패 후 파일을 삭제하세요.")
    print("이 도구는 code 교환이나 FocusOn 토큰 발급을 수행하지 않습니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

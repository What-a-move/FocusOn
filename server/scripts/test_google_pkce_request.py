"""Offline QA of PKCE and real loopback callbacks; Google is never called."""

from contextlib import redirect_stderr, redirect_stdout
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen

import google_pkce_request as helper


class PkceRequestTests(unittest.TestCase):
    def test_pkce_matches_s256_and_has_valid_length(self):
        verifier, challenge = helper.create_pkce()
        self.assertTrue(43 <= len(verifier) <= 128)
        self.assertEqual(helper.base64.urlsafe_b64encode(
            hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode(), challenge)
        self.assertNotEqual(verifier, helper.create_pkce()[0])

    def test_accepts_only_matching_state_and_unambiguous_callback(self):
        self.assertEqual("synthetic-code", helper.callback_values(
            "/?code=synthetic-code&state=expected", "expected"))
        for target in (
            "/?code=x&state=wrong", "/?code=x&state=expected&state=expected",
            "/?code=x&code=y&state=expected", "/?code=&state=expected",
            "/callback?code=x&state=expected", "http://evil/?code=x&state=expected",
            "/?code=x&state=expected#error", "/?code=x&error=no&state=expected",
            "/?code=x&state=%ED%95%9C%EA%B8%80", "/?state=expected",
        ):
            with self.subTest(target=target), self.assertRaises(ValueError):
                helper.callback_values(target, "expected")

    def test_cancel_requires_valid_state(self):
        with self.assertRaises(helper.PreparationError):
            helper.callback_values("/?error=access_denied&state=expected", "expected")
        with self.assertRaises(ValueError):
            helper.callback_values("/?error=access_denied&state=wrong", "expected")

    def drive_browser(self, cancel=False):
        captured, responses, failures = {}, [], []
        threads = []

        def browser(url, new):
            query = parse_qs(urlsplit(url).query)
            captured.update(query)

            def callback():
                try:
                    # Host 검증을 통과하지 않으면 유효 state도 받아들이면 안 된다.
                    address = query["redirect_uri"][0] + "?" + urlencode({
                        "state": query["state"][0], "code": "synthetic-unused-code"})
                    try:
                        urlopen(Request(address, headers={"Host": "invalid.example"}), timeout=3)
                        raise AssertionError("Invalid Host was accepted")
                    except HTTPError as error:
                        self.assertEqual(400, error.code)
                        error.close()
                    # 잘못된 state 뒤에도 정상 callback을 계속 받을 수 있어야 한다.
                    for state in ("wrong", query["state"][0]):
                        params = {"state": state}
                        params.update({"error": "access_denied"} if cancel
                                      else {"code": "synthetic-unused-code"})
                        address = query["redirect_uri"][0] + "?" + urlencode(params)
                        try:
                            with urlopen(address, timeout=3) as response:
                                responses.append((response.status, response.read().decode()))
                                self.assertEqual("no-store", response.headers["Cache-Control"])
                        except HTTPError as error:
                            responses.append((error.code, error.read().decode()))
                except BaseException as error:
                    failures.append(error)

            thread = threading.Thread(target=callback)
            threads.append(thread)
            thread.start()
            return True

        with patch.object(helper.webbrowser, "open", side_effect=browser):
            if cancel:
                with self.assertRaises(helper.PreparationError):
                    helper.prepare_request("synthetic.apps.googleusercontent.com", "qa-id", "QA", 5)
                body = None
            else:
                body = helper.prepare_request(
                    "synthetic.apps.googleusercontent.com", "qa-id", "QA", 5)
        for thread in threads:
            thread.join(5)
            self.assertFalse(thread.is_alive())
        if failures:
            raise failures[0]
        return captured, responses, body

    def test_real_loopback_preserves_redirect_and_does_not_exchange_code(self):
        output = io.StringIO()
        with redirect_stdout(output), redirect_stderr(output):
            query, responses, body = self.drive_browser()
        self.assertEqual([400, 200], [row[0] for row in responses])
        self.assertEqual(query["redirect_uri"][0], body["redirectUri"])
        self.assertEqual("127.0.0.1", urlsplit(body["redirectUri"]).hostname)
        self.assertEqual("S256", query["code_challenge_method"][0])
        self.assertEqual("openid", query["scope"][0])
        self.assertEqual(query["state"][0], body["state"])
        self.assertEqual("synthetic-unused-code", body["authorizationCode"])
        self.assertEqual("MACOS", body["device"]["platform"])
        for secret in (body["authorizationCode"], body["codeVerifier"], body["state"]):
            self.assertNotIn(secret, output.getvalue())
            self.assertTrue(all(secret not in text for _, text in responses))
        self.assertNotIn("client_secret", query)
        self.assertNotIn("accessToken", body)

    def test_real_loopback_cancel_terminates_without_request(self):
        with redirect_stdout(io.StringIO()):
            _, responses, body = self.drive_browser(cancel=True)
        self.assertEqual([400, 400], [row[0] for row in responses])
        self.assertIsNone(body)

    def test_browser_failure_and_timeout_are_safe(self):
        with patch.object(helper.webbrowser, "open", return_value=False):
            with self.assertRaises(helper.PreparationError):
                helper.prepare_request("id", "qa", "QA", 1)
        with patch.object(helper.webbrowser, "open", return_value=True):
            with redirect_stdout(io.StringIO()), self.assertRaises(helper.PreparationError):
                helper.prepare_request("id", "qa", "QA", 0)

    def test_request_file_is_private_and_complete(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(helper.tempfile, "tempdir", directory):
                path = helper.write_private_request({"authorizationCode": "synthetic-code"})
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            self.assertEqual({"authorizationCode": "synthetic-code"}, json.loads(path.read_text()))

    def test_partial_file_is_removed_on_write_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(helper.tempfile, "tempdir", directory), \
                    patch.object(helper.json, "dump", side_effect=OSError("synthetic")):
                with self.assertRaises(OSError):
                    helper.write_private_request({})
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_cli_normalizes_device_fields_and_rejects_oversized_utf16_values(self):
        with patch.dict(os.environ, {"GOOGLE_DESKTOP_CLIENT_ID":
                                    "synthetic.apps.googleusercontent.com"}), \
                patch.object(helper.sys, "argv", ["google_pkce_request.py", "--installation-id",
                                                   " " * 129 + "x", "--device-name", " QA "]), \
                patch.object(helper, "prepare_request", return_value={}) as prepare, \
                patch.object(helper, "write_private_request", return_value=Path("/tmp/synthetic")), \
                redirect_stdout(io.StringIO()):
            self.assertEqual(0, helper.main())
            self.assertEqual(("synthetic.apps.googleusercontent.com", "x", "QA", 300),
                             prepare.call_args.args)
        with patch.dict(os.environ, {"GOOGLE_DESKTOP_CLIENT_ID":
                                    "synthetic.apps.googleusercontent.com"}), \
                patch.object(helper.sys, "argv", ["google_pkce_request.py", "--device-name",
                                                   "😀" * 41]), \
                patch.object(helper, "prepare_request") as prepare, \
                redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            helper.main()
        prepare.assert_not_called()

    def test_cli_rejects_missing_client_id_without_showing_environment(self):
        output = io.StringIO()
        with patch.dict(os.environ, {"GOOGLE_DESKTOP_CLIENT_ID": ""}), \
                patch.object(helper.sys, "argv", ["google_pkce_request.py"]), \
                redirect_stderr(output), self.assertRaises(SystemExit) as failure:
            helper.main()
        self.assertEqual(1, failure.exception.code)
        self.assertIn("GOOGLE_DESKTOP_CLIENT_ID", output.getvalue())


if __name__ == "__main__":
    unittest.main()

"""Exercise real loopback HTTP boundaries and sanitize failure output."""

from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import tempfile
import socketserver
import threading
import unittest
from unittest.mock import patch

import verify_analysis_ai as helper

KEY = "synthetic-service-key-" + "x" * 32
HEALTH = {"status": "ready", "analysisEnabled": True,
          "modelConnected": True, "decisionMode": "candidate"}


def decision(label, score=0.6):
    return {"decision": label, "confidence": score,
            "reasonCode": {"RELATED": "SEMANTIC_GOAL_MATCH",
                           "UNRELATED": "SEMANTIC_GOAL_MISMATCH",
                           "UNCERTAIN": "INSUFFICIENT_CONTEXT"}[label],
            "modelVersion": "qa-model", "promptVersion": "qa-prompt-uncalibrated"}


@contextmanager
def stub(responder):
    requests = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_GET(self):
            self.respond(None)

        def do_POST(self):
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            self.respond(payload)

        def respond(self, payload):
            requests.append((self.path, dict(self.headers), payload))
            status, value, headers = responder(self.path, payload)
            body = value if isinstance(value, bytes) else json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Length", str(len(body)))
            for key, value in headers.items():
                self.send_header(key, value)
            self.end_headers()
            self.wfile.write(body)

    with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield f"http://127.0.0.1:{server.server_port}", requests
        finally:
            server.shutdown()
            thread.join(timeout=2)


def successful(path, payload):
    if path == "/health":
        return 200, HEALTH, {}
    label = "RELATED" if "Spring Security JWT" in payload["request"]["contentPayload"]["text"] else "UNRELATED"
    result = decision(label)
    result["providerRawText"] = "must-never-appear-in-output"
    return 200, result, {}


class AnalysisAiProbeTests(unittest.TestCase):
    def test_real_http_auth_path_trace_and_allowlisted_result(self):
        with stub(successful) as (origin, requests):
            result = helper.probe(origin, KEY)
        self.assertEqual("AI_SCENARIOS_VERIFIED", result["status"])
        self.assertFalse(result["appInterventionVerified"])
        self.assertEqual(["/health", "/internal/v1/analysis-runs", "/internal/v1/analysis-runs"],
                         [row[0] for row in requests])
        self.assertNotIn("Authorization", requests[0][1])
        for _, headers, payload in requests[1:]:
            self.assertEqual("Bearer " + KEY, headers["Authorization"])
            self.assertEqual(payload["request"]["clientRunId"], headers["X-Trace-Id"])
            self.assertEqual(payload["request"]["sessionId"], payload["context"]["sessionId"])
        output = json.dumps(result)
        self.assertNotIn(KEY, output)
        self.assertNotIn("must-never-appear-in-output", output)
        self.assertNotIn("contentPayload", output)

    def test_fixed_or_failed_health_never_submits_analysis(self):
        for health in ({"status": "ready", "analysisEnabled": True},
                       {**HEALTH, "modelConnected": False},
                       {**HEALTH, "analysisEnabled": False}):
            with self.subTest(health=health):
                with stub(lambda *_: (200, health, {})) as (origin, requests):
                    result = helper.probe(origin, KEY)
                self.assertEqual("AI_MODEL_READINESS_UNCONFIRMED", result["status"])
                self.assertEqual(1, len(requests))

    def test_receipt_is_not_a_decision(self):
        with stub(lambda path, _: (200, HEALTH if path == "/health"
                                   else {"status": "RECEIVED"}, {})) as (origin, _):
            result = helper.probe(origin, KEY)
        self.assertEqual("AI_DECISION_CONTRACT_INVALID", result["status"])

    def test_uncertain_is_safe_but_does_not_complete_scenarios(self):
        with stub(lambda path, _: (200, HEALTH if path == "/health"
                                   else decision("UNCERTAIN", None), {})) as (origin, _):
            result = helper.probe(origin, KEY)
        self.assertEqual("AI_SCENARIOS_UNCONFIRMED", result["status"])
        self.assertTrue(result["decisionContractVerified"])
        self.assertFalse(result["scenariosMatched"])
        self.assertFalse(result["appInterventionVerified"])

    def test_error_body_and_key_never_enter_output(self):
        with stub(lambda *_: (401, {"secret": KEY, "detail": "provider-private-body"}, {})) as (origin, _):
            result = helper.probe(origin, KEY)
        self.assertEqual("AI_HTTP_401", result["status"])
        self.assertNotIn(KEY, json.dumps(result))
        self.assertNotIn("provider-private-body", json.dumps(result))

    def test_key_echo_in_an_allowed_version_field_is_rejected(self):
        def responder(path, payload):
            if path == "/health":
                return 200, HEALTH, {}
            return 200, {**decision("RELATED"), "modelVersion": KEY}, {}
        with stub(responder) as (origin, _):
            result = helper.probe(origin, KEY)
        self.assertEqual("AI_RESPONSE_CONTAINS_CREDENTIAL", result["status"])
        self.assertNotIn(KEY, json.dumps(result))

    def test_redirect_does_not_forward_service_auth(self):
        with stub(successful) as (target, target_requests):
            def redirect(path, payload):
                return (200, HEALTH, {}) if path == "/health" else (302, {}, {"Location": target})
            with stub(redirect) as (origin, _):
                result = helper.probe(origin, KEY)
        self.assertEqual("AI_HTTP_302", result["status"])
        self.assertEqual([], target_requests)

    def test_large_and_malformed_responses_are_not_logged(self):
        for body, code in ((b"x" * 9000, "AI_RESPONSE_TOO_LARGE"),
                           (b"private-not-json", "AI_RESPONSE_INVALID"),
                           (b"[]", "AI_RESPONSE_INVALID")):
            with self.subTest(code=code):
                with stub(lambda *_: (200, body, {})) as (origin, _):
                    result = helper.probe(origin, KEY)
                self.assertEqual(code, result["status"])
                self.assertNotIn("private-not-json", json.dumps(result))

    def test_normal_and_numeric_beta_thresholds_are_point_six(self):
        for prompt in ("qa-prompt", "qa-prompt-uncalibrated"):
            for score in (0.6, 0.65, 0.749, 0.75, 1):
                value = {**decision("UNRELATED", score), "promptVersion": prompt}
                self.assertEqual("UNRELATED", helper.decision_summary(value)["decision"])
            with self.assertRaisesRegex(helper.ProbeError, "AI_DECISION_BELOW_THRESHOLD"):
                helper.decision_summary({**decision("UNRELATED", 0.599), "promptVersion": prompt})
        self.assertIsNone(helper.decision_summary(decision("UNRELATED", None))["confidence"])

    def test_invalid_scores_and_contradictory_reasons_fail_closed(self):
        for value in (decision("RELATED", True), decision("RELATED", float("nan")),
                      decision("RELATED", 2), decision("RELATED", "0.9"),
                      {**decision("UNCERTAIN", None), "reasonCode": "SEMANTIC_GOAL_MATCH"}):
            with self.subTest(value=value), self.assertRaises(helper.ProbeError):
                helper.decision_summary(value)

    def test_malformed_http_status_never_leaks_provider_text(self):
        private = "synthetic-provider-private-status"

        class BrokenHandler(socketserver.BaseRequestHandler):
            def handle(self):
                self.request.settimeout(2)
                self.request.recv(8192)
                self.request.sendall((private + "\r\n\r\n").encode())

        with socketserver.ThreadingTCPServer(("127.0.0.1", 0), BrokenHandler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                result = helper.probe(f"http://127.0.0.1:{server.server_address[1]}", KEY)
                self.assertEqual("AI_NETWORK_UNAVAILABLE", result["status"])
                self.assertNotIn(private, json.dumps(result))
            finally:
                server.shutdown()
                worker.join(timeout=2)

    def test_container_values_are_rejected_without_provider_exception_text(self):
        for field in ("decision", "reasonCode"):
            with self.subTest(field=field), self.assertRaises(helper.ProbeError):
                helper.decision_summary({**decision("RELATED"), field: ["private-provider-body"]})
        with self.assertRaises(helper.ProbeError):
            helper.validate_health({**HEALTH, "decisionMode": ["private-provider-body"]})

    def test_bad_origins_and_credentials_do_not_make_a_request(self):
        for origin in ("", "file:///tmp/test", "http://user:private@example.com",
                       "http://example.com/path", "http://example.com?key=private"):
            with self.subTest(origin=origin):
                self.assertEqual("AI_ORIGIN_INVALID", helper.probe(origin, KEY)["status"])
        self.assertEqual("AI_SERVICE_KEY_MISSING_OR_INVALID",
                         helper.probe("http://127.0.0.1:1", "")["status"])

    def test_env_is_literal_and_process_environment_has_precedence(self):
        directory = Path(__file__).resolve().parents[1] / "build"
        directory.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=directory) as temp:
            env = Path(temp) / ".env"
            env.write_text('AI_ANALYSIS_URL="http://127.0.0.1:1"\n'
                           "export FOCUSON_AI_API_KEY='$(must-not-execute)'\nOTHER=private\n")
            with patch.dict(os.environ, {}, clear=True):
                config = helper.load_configuration(env)
                self.assertEqual("$(must-not-execute)", config["FOCUSON_AI_API_KEY"])
                self.assertNotIn("OTHER", config)
            with patch.dict(os.environ, {"AI_ANALYSIS_URL": "http://127.0.0.1:2"}):
                self.assertEqual("http://127.0.0.1:2", helper.load_configuration(env)["AI_ANALYSIS_URL"])


if __name__ == "__main__":
    unittest.main()

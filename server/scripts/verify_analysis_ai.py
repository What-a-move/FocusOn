#!/usr/bin/env python3
"""Probe the configured AI service with synthetic input, without saving raw responses or keys."""

import argparse
from datetime import datetime, timezone
import http.client
import json
import math
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit
import uuid


class ProbeError(Exception):
    """Only fixed, safe reason codes may leave the network/configuration boundary."""


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def load_configuration(env_file):
    """Read only two literal values; never execute shell syntax from an env file."""
    values = {}
    if env_file.is_file():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("export "):
                line = line[7:]
            if line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            if key.strip() in {"AI_ANALYSIS_URL", "FOCUSON_AI_API_KEY"}:
                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                    value = value[1:-1]
                values[key.strip()] = value
    for key in ("AI_ANALYSIS_URL", "FOCUSON_AI_API_KEY"):
        if key in os.environ:
            values[key] = os.environ[key]
    return values


def validate_origin(value):
    try:
        origin = urlsplit(value)
        if (origin.scheme not in {"http", "https"} or not origin.hostname
                or origin.username is not None or origin.password is not None
                or origin.query or origin.fragment or origin.path not in {"", "/"}
                or any(character.isspace() for character in value)):
            raise ValueError()
        origin.port
    except (ValueError, TypeError):
        raise ProbeError("AI_ORIGIN_INVALID") from None
    return value.rstrip("/")


def request_json(origin, path, key, timeout, payload=None):
    trace = payload["request"]["clientRunId"] if payload is not None else str(uuid.uuid4())
    headers = {"Accept": "application/json", "X-Trace-Id": trace}
    if payload is not None:
        headers.update({"Authorization": "Bearer " + key, "Content-Type": "application/json"})
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(origin + path, data=body, headers=headers)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            raw = response.read(8193)
            if len(raw) > 8192:
                raise ProbeError("AI_RESPONSE_TOO_LARGE")
            value = json.loads(raw)
            if not isinstance(value, dict):
                raise ProbeError("AI_RESPONSE_INVALID")
            return value
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        raise ProbeError("AI_HTTP_" + str(status)) from None
    except (urllib.error.URLError, OSError, http.client.HTTPException):
        raise ProbeError("AI_NETWORK_UNAVAILABLE") from None
    except (ValueError, UnicodeError):
        raise ProbeError("AI_RESPONSE_INVALID") from None


def validate_health(value):
    if (value.get("status") != "ready" or value.get("analysisEnabled") is not True
            or value.get("modelConnected") is not True):
        # The old fixed /health lacks modelConnected and is deliberately insufficient.
        raise ProbeError("AI_MODEL_READINESS_UNCONFIRMED")
    if not isinstance(value.get("decisionMode"), str) or value["decisionMode"] not in {"safe", "candidate"}:
        raise ProbeError("AI_DECISION_MODE_UNKNOWN")
    return {"modelConnected": True, "decisionMode": value["decisionMode"]}


def decision_summary(value):
    decision = value.get("decision")
    reason = value.get("reasonCode")
    confidence = value.get("confidence")
    if (not isinstance(decision, str) or decision not in {"RELATED", "UNRELATED", "UNCERTAIN"}
            or not isinstance(reason, str) or "confidence" not in value
            or reason not in {"SEMANTIC_GOAL_MATCH", "SEMANTIC_GOAL_MISMATCH",
                              "INSUFFICIENT_CONTEXT", "AMBIGUOUS_CONTEXT", "ANALYSIS_UNAVAILABLE"}):
        raise ProbeError("AI_DECISION_CONTRACT_INVALID")
    for name in ("modelVersion", "promptVersion"):
        if not isinstance(value.get(name), str) or not re.fullmatch(
                r"[A-Za-z0-9][A-Za-z0-9._:-]{0,63}", value[name]):
            raise ProbeError("AI_VERSION_INVALID")
    if confidence is not None and (isinstance(confidence, bool)
            or not isinstance(confidence, (int, float)) or not math.isfinite(confidence)
            or not 0 <= confidence <= 1):
        raise ProbeError("AI_CONFIDENCE_INVALID")
    if decision in {"RELATED", "UNRELATED"}:
        expected = "SEMANTIC_GOAL_MATCH" if decision == "RELATED" else "SEMANTIC_GOAL_MISMATCH"
        if reason != expected:
            raise ProbeError("AI_REASON_MISMATCH")
        null_beta = confidence is None and value["promptVersion"].endswith("-uncalibrated")
        if not null_beta and (confidence is None or confidence < 0.6):
            raise ProbeError("AI_DECISION_BELOW_THRESHOLD")
    elif reason in {"SEMANTIC_GOAL_MATCH", "SEMANTIC_GOAL_MISMATCH"}:
        raise ProbeError("AI_REASON_MISMATCH")
    # Intentionally omit every provider field outside this allowlist.
    return {"decision": decision, "confidence": confidence, "reasonCode": reason,
            "modelVersion": value["modelVersion"], "promptVersion": value["promptVersion"]}


def synthetic_envelope(expected):
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    client_run_id = str(uuid.uuid4())
    goal = "Spring Security JWT 인증 구현 학습"
    text = ("Spring Security JWT 서명 검증과 토큰 만료 시간 검사, 인증 필터 구현을 학습합니다."
            if expected == "RELATED" else
            "오늘 축구 경기 하이라이트와 선수 득점 순위, 게임 방송과 예능 영상을 시청합니다.")
    request = {"sessionId": "qa-session", "clientRunId": client_run_id,
               "contentSource": "CHROME_DOM", "observedAt": now, "sequence": 1,
               "goalSnapshot": goal, "guardStatus": None,
               "metadata": {"appName": "Google Chrome", "windowTitle": None,
                            "pageTitle": "Synthetic QA", "urlHost": "example.com"},
               "contentPayload": {"text": text}}
    context = {"userId": "qa-user", "sessionId": request["sessionId"], "runId": "qa-run",
               "goalId": "qa-goal", "goalVersion": 1, "clientRunId": client_run_id,
               "sequence": 1, "navigationId": "qa-navigation", "observedAt": now,
               "contentSource": "CHROME_DOM", "isActiveTarget": True, "sessionStatus": "RUNNING",
               "exclusionMode": "NONE", "resumeRequired": False, "policyVersion": "v1",
               "extractorVersion": "public-text-v1", "stateVersion": 1}
    return {"request": request,
            "goal": {"goalId": "qa-goal", "goalVersion": 1, "text": goal, "confirmed": True},
            "context": context, "extractionStatus": "SUCCESS"}


def probe(origin, key, timeout=5.0):
    result = {"synthetic": True, "appInterventionVerified": False, "decisions": []}
    start = time.monotonic()
    try:
        origin = validate_origin(origin)
        if (not isinstance(key, str) or not 32 <= len(key.encode()) <= 1024
                or not all(33 <= ord(character) <= 126 for character in key)):
            raise ProbeError("AI_SERVICE_KEY_MISSING_OR_INVALID")
        result["health"] = validate_health(request_json(origin, "/health", key, timeout))
        for expected in ("RELATED", "UNRELATED"):
            summary = decision_summary(request_json(origin, "/internal/v1/analysis-runs", key,
                                                    timeout, synthetic_envelope(expected)))
            if any(key in item for item in summary.values() if isinstance(item, str)):
                raise ProbeError("AI_RESPONSE_CONTAINS_CREDENTIAL")
            summary["expected"] = expected
            summary["matchesScenario"] = summary["decision"] == expected
            result["decisions"].append(summary)
        result["decisionContractVerified"] = True
        result["scenariosMatched"] = all(row["matchesScenario"] for row in result["decisions"])
        result["status"] = "AI_SCENARIOS_VERIFIED" if result["scenariosMatched"] else "AI_SCENARIOS_UNCONFIRMED"
    except ProbeError as error:
        result["status"] = str(error)
    result["elapsedMs"] = round((time.monotonic() - start) * 1000)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path(__file__).resolve().parents[1] / ".env")
    parser.add_argument("--base-url", help="AI service origin; never put credentials in this URL")
    args = parser.parse_args()
    try:
        configuration = load_configuration(args.env_file)
        origin = args.base_url or configuration.get("AI_ANALYSIS_URL", "")
        if not origin:
            raise ProbeError("AI_ORIGIN_MISSING")
        result = probe(origin, configuration.get("FOCUSON_AI_API_KEY", ""))
    except (OSError, UnicodeError, ProbeError):
        result = {"synthetic": True, "appInterventionVerified": False,
                  "status": "AI_CONFIGURATION_MISSING_OR_INVALID"}
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["status"] == "AI_SCENARIOS_VERIFIED" else 2


if __name__ == "__main__":
    sys.exit(main())

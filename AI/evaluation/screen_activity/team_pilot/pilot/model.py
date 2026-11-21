"""Classify a verified local observation using the shared beta model and decision rules."""

import itertools
import json
import math
import os
from datetime import datetime, timezone
from pathlib import Path
import sys
from uuid import uuid4

SCREEN_ACTIVITY = Path(__file__).resolve().parents[2]
AI_ROOT = SCREEN_ACTIVITY.parents[1]
sys.path.insert(0, str(SCREEN_ACTIVITY))
sys.path.insert(0, str(AI_ROOT))

from src.analysis.acquisition_policy import desktop_target_block_reason
from src.api.reception import AnalysisReception
from src.api.relevance_contract import adapt_screen_request
from src.preprocessing.adapter import observation_is_current
from src.preprocessing.validator import contains_sensitive_data

PILOT_GOAL = "학습 활동 확인"
EXPECTED_HEAD = "b684d12c0e4af1d171b8244beb6bb0a78373e0c19ac3ee657b8d4678f58d35c9"


def verified_payload(sid, context, sequence, source, metadata, body):
    """The pilot is both the observer and the owner of its local session."""
    run_id = str(uuid4())
    observation = {
        "sessionId": sid,
        "clientRunId": run_id,
        "observedAt": datetime.now(timezone.utc).isoformat(),
        "sequence": sequence,
        "contentSource": source,
    }
    request = {**observation, "goalSnapshot": PILOT_GOAL, "guardStatus": None, "metadata": metadata}
    if body is not None:
        request["contentPayload"] = {"text": body}
    goal = {"goalId": "local-pilot", "goalVersion": 1}
    envelope = AnalysisReception.parse_safe(
        {
            "request": request,
            "goal": {**goal, "text": PILOT_GOAL, "confirmed": True},
            "context": {
                **observation,
                **goal,
                "runId": run_id,
                "navigationId": context,
                "userId": "local-pilot",
                "isActiveTarget": True,
                "sessionStatus": "RUNNING",
                "exclusionMode": "NONE",
                "resumeRequired": False,
                "policyVersion": "v1",
                "extractorVersion": "client-v1",
                "stateVersion": 1,
            },
            "extractionStatus": "SUCCESS",
        }
    )
    content = adapt_screen_request(
        envelope.request,
        envelope.goal,
        envelope.context,
        extraction_status=envelope.extraction_status,
    )
    if not content.is_active_target or not observation_is_current(content, envelope.context):
        raise ValueError("OBSERVATION_NOT_CURRENT")
    if desktop_target_block_reason(content.content_source, content.app_name):
        raise ValueError("TARGET_BLOCKED")
    if any(
        contains_sensitive_data(value)
        for value in (
            content.text,
            content.page_title,
            content.window_title,
            content.app_name,
            content.url_host,
            PILOT_GOAL,
        )
    ):
        raise ValueError("CONTENT_BLOCKED")
    return envelope.request.model_dump(mode="json", by_alias=True, exclude_none=True)


class LocalModel:
    def __init__(self):
        os.environ.update(
            HF_HUB_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1", TOKENIZERS_PARALLELISM="false"
        )
        from activity import ActivityClassifier, MODEL_DIR

        manifest = json.loads((MODEL_DIR / "manifest.json").read_text())
        if manifest["headSHA256"] != EXPECTED_HEAD or manifest["rows"] != 10_168:
            raise RuntimeError("MODEL_VERSION_MISMATCH")
        self.rows = manifest["rows"]
        self.classifier = ActivityClassifier(MODEL_DIR)
        scores = self.classifier.predict(
            [
                {
                    "contentSource": "CHROME_DOM",
                    "metadata": {"pageTitle": "선형 대수 강의"},
                    "contentPayload": {"text": "행렬 곱 예제와 연습 문제"},
                }
            ]
        )[0]["scores"]
        if not scores or not all(math.isfinite(score) for score in scores.values()):
            raise RuntimeError("MODEL_PROBE_FAILED")
        self.sequence = itertools.count(1)

    def classify(self, sid, context, source, metadata, body=None):
        from activity import validate_route
        from beta_decision import beta_decision

        try:
            payload = verified_payload(sid, context, next(self.sequence), source, metadata, body)
            validate_route(payload)
        except ValueError:
            return "GUARD"
        prediction = self.classifier.predict([payload])[0]
        return beta_decision(payload, prediction)["label"]

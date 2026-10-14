"""Opt-in OpenAI experiment using only a fixed, synthetic #29 fixture.

No caller-supplied goal or evidence can reach this provider boundary. Real
screen data requires a separately reviewed product integration.
"""

from __future__ import annotations

import json
import os
import re
from typing import Any

from pydantic import BaseModel, ConfigDict

from .learning_summary_demo import (
    DemoEvidence,
    DemoOutcome,
    MAX_SUMMARY_CHARS,
    _has_sensitive_value,
    _render_summary,
    eligible_evidence,
)


SYNTHETIC_GOAL = "React Query의 캐시와 쿼리 키 공부"
SYNTHETIC_EVIDENCE = (
    DemoEvidence("React Query 캐시", "캐시와 갱신 개념을 다룬 가상 자료 확인"),
    DemoEvidence("쿼리 키", "쿼리 키 구성 예시를 다룬 가상 자료 확인"),
)
DEFAULT_MODEL = "gpt-5-mini"

_UNSUPPORTED_ACHIEVEMENT = re.compile(
    r"(?:이해했|습득했|터득했|마스터했|구현했|완성했|완료했|해결했|달성했|"
    r"이해 완료|구현 완료|문제 해결|fully understood|implemented|mastered|completed)",
    re.IGNORECASE,
)


class KeyConcept(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str
    briefExplanation: str
    sessionEvidence: str


class LearningSummary(BaseModel):
    model_config = ConfigDict(extra="forbid")

    overview: str
    keyConcepts: list[KeyConcept]
    nextStartPrompt: str


def _validated_summary(output: Any, evidence: list[DemoEvidence]) -> dict[str, object] | None:
    """Reject malformed, sensitive, or plainly ungrounded model output."""
    try:
        parsed = LearningSummary.model_validate(output)
    except (TypeError, ValueError):
        return None
    if not 1 <= len(parsed.keyConcepts) <= 5:
        return None
    if not 2 <= len(re.findall(r"[.!?](?=\s|$)", parsed.overview)) <= 3:
        return None
    if len(re.findall(r"[.!?](?=\s|$)", parsed.nextStartPrompt)) > 1:
        return None
    allowed = {item.text for item in evidence}
    concepts = {item.concept for item in evidence}
    if any(item.sessionEvidence not in allowed or item.name not in concepts for item in parsed.keyConcepts):
        return None
    fields = [parsed.overview, parsed.nextStartPrompt]
    for item in parsed.keyConcepts:
        fields.extend((item.name, item.briefExplanation, item.sessionEvidence))
    if any(not field.strip() or _has_sensitive_value(field) for field in fields):
        return None
    if any(_UNSUPPORTED_ACHIEVEMENT.search(field) for field in fields):
        return None
    summary = parsed.model_dump()
    if len(_render_summary(summary)) > MAX_SUMMARY_CHARS:
        return None
    return summary


def generate_fixed_synthetic_summary(*, client: Any = None, model: str | None = None) -> DemoOutcome:
    """Generate a note from immutable fake evidence; never accepts UI text.

    The optional client enables offline contract tests. Provider failures are
    deliberately reduced to FAILED so responses and exceptions are not logged.
    """
    evidence = eligible_evidence(list(SYNTHETIC_EVIDENCE), record_consent=True)
    if len(evidence) < 2:
        return DemoOutcome("INSUFFICIENT_DATA", accepted_count=len(evidence))
    payload = {
        "goalSnapshot": SYNTHETIC_GOAL,
        "reviewEvidence": [item.text for item in evidence],
        "dataOrigin": "FIXED_SYNTHETIC_FIXTURE",
    }
    serialized = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    if len(serialized) > 2400:
        return DemoOutcome("FAILED", accepted_count=len(evidence))
    try:
        if client is None:
            if not os.environ.get("OPENAI_API_KEY"):
                return DemoOutcome("FAILED", accepted_count=len(evidence))
            from openai import OpenAI

            client = OpenAI(timeout=20.0, max_retries=0)
        response = client.responses.parse(
            model=model or os.environ.get("FOCUSON_SUMMARY_MODEL", DEFAULT_MODEL),
            store=False,
            timeout=20.0,
            max_output_tokens=1400,
            text_format=LearningSummary,
            instructions=(
                "You write a short Korean study-session review using ONLY the provided fixed synthetic observations. "
                "Return overview (2-3 sentences), 1-5 keyConcepts, and one nextStartPrompt. "
                "Every keyConcept.name must exactly match a concept before ':' in an observation; "
                "sessionEvidence must exactly copy one complete observation. "
                "Describe what was observed, never claim the learner understood, implemented, completed, "
                "or solved anything. Do not add URLs, personal data, or facts not in the observations."
            ),
            input=[{"role": "user", "content": serialized}],
        )
        summary = _validated_summary(response.output_parsed, evidence)
    except Exception:
        return DemoOutcome("FAILED", accepted_count=len(evidence))
    if summary is None:
        return DemoOutcome("FAILED", accepted_count=len(evidence))
    return DemoOutcome("READY", summary, len(evidence))

"""Opt-in OpenAI experiment for synthetic DOM-shaped #29 fixtures."""

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
    SyntheticDOMPage,
    _has_sensitive_value,
    _render_summary,
    evidence_from_dom_pages,
)


SYNTHETIC_GOAL = "React Query의 캐시와 쿼리 키 공부"
SYNTHETIC_DOM_PAGES = [
    SyntheticDOMPage(
        page_type="DOCUMENTATION",
        title="React Query 캐시 가이드",
        text=(
            "useQuery는 queryKey를 기준으로 데이터를 캐시에 저장합니다. 같은 queryKey는 기존 캐시를 재사용합니다.\n"
            "staleTime 동안 데이터는 신선한 것으로 취급됩니다. 시간이 지나면 쿼리가 다시 실행될 수 있습니다."
        ),
    )
]
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
    allowed = {item.observation for item in evidence}
    anchors = {f"{item.concept} {item.observation}".casefold() for item in evidence}
    if any(
        item.sessionEvidence not in allowed
        or not any(item.name.casefold() in anchor for anchor in anchors)
        for item in parsed.keyConcepts
    ):
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


def generate_synthetic_dom_summary(
    goal: str,
    pages: list[SyntheticDOMPage],
    *,
    client: Any = None,
    model: str | None = None,
) -> DemoOutcome:
    """Generate from fake DOM pages after local RELATED/paragraph filtering."""
    if not 2 <= len(goal.strip()) <= 40 or _has_sensitive_value(goal):
        return DemoOutcome("FAILED")
    evidence = evidence_from_dom_pages(pages)
    if len(evidence) < 2:
        return DemoOutcome("INSUFFICIENT_DATA", accepted_count=len(evidence))
    payload = {
        "goalSnapshot": goal.strip(),
        "reviewEvidence": [
            {"pageType": item.page_type, "sourceTitle": item.concept, "text": item.observation}
            for item in evidence
        ],
        "dataOrigin": "SYNTHETIC_DOM_FIXTURE",
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
            max_output_tokens=2500,
            text_format=LearningSummary,
            instructions=(
                "You write a short Korean study-session review using ONLY the provided synthetic DOM-derived excerpts. "
                "Return overview (2-3 sentences), 1-5 keyConcepts, and one nextStartPrompt. "
                "Each keyConcept.name must be a term appearing in a sourceTitle or excerpt; "
                "sessionEvidence must exactly copy one complete excerpt. "
                "Describe what was observed, never claim the learner understood, implemented, completed, "
                "or solved anything. Do not add URLs, personal data, or facts not in the excerpts."
            ),
            input=[{"role": "user", "content": serialized}],
        )
        summary = _validated_summary(response.output_parsed, evidence)
    except Exception:
        return DemoOutcome("FAILED", accepted_count=len(evidence))
    if summary is None:
        return DemoOutcome("FAILED", accepted_count=len(evidence))
    return DemoOutcome("READY", summary, len(evidence))


def generate_fixed_synthetic_summary(*, client: Any = None, model: str | None = None) -> DemoOutcome:
    """Backward-compatible helper using the built-in fake DOM page."""
    return generate_synthetic_dom_summary(
        SYNTHETIC_GOAL,
        list(SYNTHETIC_DOM_PAGES),
        client=client,
        model=model,
    )

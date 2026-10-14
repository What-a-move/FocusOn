"""Pure, provider-independent MVP learning-summary preparation and validation.

The Server owns session state, retries, storage, and the 20-second model timeout.
This module never persists input or calls an external model on its own.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import json
import re
from typing import Callable, Mapping, Sequence

from src.prompts.summary_prompt import SUMMARY_SYSTEM_PROMPT


MAX_EVIDENCE_COUNT = 20
MAX_EVIDENCE_CHARS = 120
MAX_INPUT_CHARS = 2400
MAX_OUTPUT_CHARS = 1200

_HASH = re.compile(r"[0-9a-fA-F]{64}\Z")
_SENSITIVE = (
    re.compile(r"https?://|www\.", re.IGNORECASE),
    re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE),
    re.compile(r"(?<!\d)(?:\+?82[-. ]?)?0?1[016789][-. ]?\d{3,4}[-. ]?\d{4}(?!\d)"),
    re.compile(r"\b(?:sk-|ghp_|gho_|github_pat_|AKIA)[A-Za-z0-9_-]{8,}\b"),
    re.compile(r"\b(?:Bearer\s+)?eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b", re.IGNORECASE),
    re.compile(r"(?<!\d)(?:\d[ -]*?){13,19}(?!\d)"),
)
_UNSUPPORTED_ACHIEVEMENT = re.compile(
    r"(?:이해|암기|습득|해결|구현|완료|마스터|숙달)(?:했|하였|됐|되었|함|했다)|"
    r"\b(?:mastered|understood|solved|completed|implemented)\b",
    re.IGNORECASE,
)


class SummaryContractError(ValueError):
    """A code-only error; never includes content from a goal or page."""

    def __init__(self, code: str) -> None:
        self.code = code
        super().__init__(code)


@dataclass(frozen=True)
class ReviewEvidence:
    concept: str
    observation: str
    observed_at: str
    confidence: float
    content_hash: str
    relevance_label: str

    @property
    def text(self) -> str:
        return f"{self.concept}: {self.observation}"


@dataclass(frozen=True)
class SummaryRequest:
    goal_snapshot: str
    evidences: Sequence[ReviewEvidence]
    session_status: str
    record_only: bool = False
    record_consent: bool = True


@dataclass(frozen=True)
class SummaryOutcome:
    status: str
    summary: dict[str, object] | None = None


SummaryGenerator = Callable[[str, dict[str, object]], Mapping[str, object]]


def contains_sensitive_value(value: str) -> bool:
    """Conservative second-line check; clients must also filter at collection."""
    return any(pattern.search(value) for pattern in _SENSITIVE)


def _valid_utc(value: str) -> bool:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return False
    return parsed.tzinfo is not None and parsed.utcoffset() == timezone.utc.utcoffset(parsed)


def _eligible(evidence: ReviewEvidence) -> bool:
    if evidence.relevance_label != "RELATED":
        return False
    if isinstance(evidence.confidence, bool) or not isinstance(evidence.confidence, (int, float)):
        return False
    if not 0.75 <= evidence.confidence <= 1.0:
        return False
    if not isinstance(evidence.content_hash, str) or not _HASH.fullmatch(evidence.content_hash):
        return False
    if not isinstance(evidence.observed_at, str) or not _valid_utc(evidence.observed_at):
        return False
    if not isinstance(evidence.concept, str) or not isinstance(evidence.observation, str):
        return False
    if not evidence.concept.strip() or not evidence.observation.strip():
        return False
    if len(evidence.text) > MAX_EVIDENCE_CHARS:
        return False
    return not contains_sensitive_value(evidence.text)


def prepare_payload(request: SummaryRequest) -> dict[str, object] | None:
    """Build a bounded model payload, or None when no summary may be generated."""
    if request.session_status != "ENDED":
        raise SummaryContractError("SESSION_NOT_ENDED")
    if request.record_only or not request.record_consent:
        return None

    if not isinstance(request.goal_snapshot, str):
        raise SummaryContractError("GOAL_SNAPSHOT_INVALID")
    goal = request.goal_snapshot.strip()
    if not 2 <= len(goal) <= 40 or contains_sensitive_value(goal):
        raise SummaryContractError("GOAL_SNAPSHOT_INVALID")

    unique: dict[str, ReviewEvidence] = {}
    for evidence in request.evidences:
        if isinstance(evidence, ReviewEvidence) and _eligible(evidence):
            unique.setdefault(evidence.content_hash.lower(), evidence)

    # Prefer the latest observed evidence, then send it in chronological order.
    candidates = sorted(unique.values(), key=lambda item: item.observed_at)[-MAX_EVIDENCE_COUNT:]
    payload: dict[str, object] = {"goalSnapshot": goal, "evidence": []}
    selected: list[dict[str, str]] = []
    for evidence in candidates:
        selected.append({"id": f"e{len(selected) + 1}", "text": evidence.text})
        payload["evidence"] = selected
        if len(json.dumps(payload, ensure_ascii=False, separators=(",", ":"))) > MAX_INPUT_CHARS:
            selected.pop()
            continue

    if len(selected) < 2:
        return None
    payload["evidence"] = selected
    return payload


def _validated_output(raw: Mapping[str, object], evidence_texts: set[str]) -> dict[str, object]:
    if set(raw) != {"overview", "keyConcepts", "nextStartPrompt"}:
        raise SummaryContractError("MODEL_OUTPUT_INVALID")
    overview = raw["overview"]
    next_prompt = raw["nextStartPrompt"]
    concepts = raw["keyConcepts"]
    if not isinstance(overview, str) or not overview.strip():
        raise SummaryContractError("MODEL_OUTPUT_INVALID")
    if not isinstance(next_prompt, str) or not next_prompt.strip():
        raise SummaryContractError("MODEL_OUTPUT_INVALID")
    if not isinstance(concepts, list) or not 1 <= len(concepts) <= 5:
        raise SummaryContractError("MODEL_OUTPUT_INVALID")

    for concept in concepts:
        if not isinstance(concept, dict) or set(concept) != {
            "name", "briefExplanation", "sessionEvidence"
        }:
            raise SummaryContractError("MODEL_OUTPUT_INVALID")
        if any(not isinstance(item, str) or not item.strip() for item in concept.values()):
            raise SummaryContractError("MODEL_OUTPUT_INVALID")
        if concept["sessionEvidence"] not in evidence_texts:
            raise SummaryContractError("MODEL_OUTPUT_UNGROUNDED")

    normalized = {
        "overview": overview.strip(),
        "keyConcepts": concepts,
        "nextStartPrompt": next_prompt.strip(),
    }
    serialized = json.dumps(normalized, ensure_ascii=False, separators=(",", ":"))
    if len(serialized) > MAX_OUTPUT_CHARS:
        raise SummaryContractError("MODEL_OUTPUT_TOO_LONG")
    if contains_sensitive_value(serialized):
        raise SummaryContractError("MODEL_OUTPUT_SENSITIVE")
    if _UNSUPPORTED_ACHIEVEMENT.search(serialized):
        raise SummaryContractError("MODEL_OUTPUT_UNGROUNDED")
    return normalized


def generate_summary(request: SummaryRequest, generator: SummaryGenerator) -> SummaryOutcome:
    """Generate a validated draft; persistence and retries remain Server-owned."""
    payload = prepare_payload(request)
    if payload is None:
        return SummaryOutcome(status="INSUFFICIENT_DATA")

    # Deliberately no logging, persistence, or provider configuration here.
    try:
        raw = generator(SUMMARY_SYSTEM_PROMPT, payload)
    except Exception:
        # Provider exception messages may contain request fragments. Do not
        # propagate them to the caller, its logs, or the public response.
        raise SummaryContractError("MODEL_GENERATION_FAILED") from None
    if not isinstance(raw, Mapping):
        raise SummaryContractError("MODEL_OUTPUT_INVALID")
    texts = {item["text"] for item in payload["evidence"]}
    summary = _validated_output(raw, texts)
    return SummaryOutcome(status="READY", summary=summary)

"""Fail-closed validation of already extracted DOM/OCR text.

The caller supplies the authoritative current event context. This module never
logs or raises an exception containing page text or credentials.
"""

import math
import re
import uuid
from dataclasses import dataclass

from .content_parser import InvalidStructure, parse_passages
from .models import EventContext, Passage, PreprocessingResult
from .text_cleaner import clean_text, content_fingerprint, deduplicate


_NAVIGATION_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}\Z")
_EMAIL = re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I)
_PHONE = re.compile(r"(?<!\d)(?:\+82[-. ]?)?0?1[016789][-. ]?\d{3,4}[-. ]?\d{4}(?!\d)")
_INTL_PHONE = re.compile(r"(?<!\w)\+\d{1,3}[-. ]?(?:\d[-. ]?){8,12}(?!\d)")
_BEARER = re.compile(r"\bBearer\s+\S+", re.I)
_JWT = re.compile(r"\beyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b")
_API_KEY = re.compile(r"\b(?:sk-[A-Za-z0-9_-]{12,}|ghp_[A-Za-z0-9]{12,}|github_pat_[A-Za-z0-9_]{12,}|AKIA[A-Z0-9]{16})\b")
_SECRET_FIELD = re.compile(r"\b(?:api[_-]?key|access[_-]?token|password|secret)\s*[:=]\s*\S+", re.I)
_URL = re.compile(r"\b(?:https?://|www\.)\S+", re.I)
_FORBIDDEN_MARKUP = re.compile(r"<!doctype\b|<(?:html|body|script|style|input|textarea|form|iframe)\b", re.I)
_CARD = re.compile(r"(?<![0-9])(?:[0-9][ -]?){12,18}[0-9](?![0-9])")
_BROKEN = re.compile(r"[\ufffd\ud800-\udfff\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_FIELDS = frozenset({
    "eventId", "sessionId", "runId", "goalVersion", "navigationId",
    "extractionMethod", "extractionStatus", "title", "passages",
})
_STATUS = frozenset({"SUCCESS", "PARTIAL", "FAILED", "EXCLUDED", "UNSUPPORTED"})
_METHOD = frozenset({"DOM", "OCR"})
_MAX_TOTAL_CHARACTERS = 128_000


@dataclass(frozen=True)
class PreprocessingPolicy:
    # The OCR cutoff must be set from an evaluated product configuration.
    min_ocr_confidence: float | None = None

    def __post_init__(self) -> None:
        value = self.min_ocr_confidence
        if value is not None and (
            type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 1
        ):
            raise ValueError("INVALID_OCR_POLICY")


def _is_uuid(value: object) -> bool:
    if not isinstance(value, str):
        return False
    try:
        return str(uuid.UUID(value)) == value.lower()
    except (ValueError, AttributeError):
        return False


def _valid_context(context: EventContext) -> bool:
    return (
        _is_uuid(context.event_id)
        and _is_uuid(context.session_id)
        and _is_uuid(context.run_id)
        and type(context.goal_version) is int
        and context.goal_version > 0
        and isinstance(context.navigation_id, str)
        and _NAVIGATION_ID.fullmatch(context.navigation_id) is not None
    )


def _luhn(candidate: str) -> bool:
    digits = [int(char) for char in candidate if char.isdigit()]
    if not 13 <= len(digits) <= 19:
        return False
    total = 0
    for index, digit in enumerate(reversed(digits)):
        if index % 2:
            digit *= 2
            if digit > 9:
                digit -= 9
        total += digit
    return total % 10 == 0


def _sensitive(text: str) -> bool:
    if any(pattern.search(text) for pattern in (
        _EMAIL, _PHONE, _INTL_PHONE, _BEARER, _JWT, _API_KEY, _SECRET_FIELD,
        _URL, _FORBIDDEN_MARKUP,
    )):
        return True
    return any(_luhn(match.group()) for match in _CARD.finditer(text))


def _result(extraction: str, status: str, quality: str, reason: str) -> PreprocessingResult:
    return PreprocessingResult(extraction, status, quality, reason)


def preprocess(
    payload: object,
    current_context: EventContext,
    policy: PreprocessingPolicy | None = None,
) -> PreprocessingResult:
    """Return sanitized content only when every gate passes.

    `current_context` must be sourced from live state, never copied from payload.
    A subsequent consumer must recheck it before applying an asynchronous result.
    """
    if type(payload) is not dict or not isinstance(current_context, EventContext):
        return _result("FAILED", "FAILED", "UNAVAILABLE", "INVALID_INPUT")
    if not _valid_context(current_context) or set(payload) - _FIELDS:
        return _result("FAILED", "FAILED", "UNAVAILABLE", "INVALID_INPUT")

    extraction = payload.get("extractionStatus")
    method = payload.get("extractionMethod")
    if not isinstance(extraction, str) or extraction not in _STATUS:
        return _result("FAILED", "FAILED", "UNAVAILABLE", "INVALID_INPUT")
    if not isinstance(method, str) or method not in _METHOD:
        return _result("FAILED", "FAILED", "UNAVAILABLE", "INVALID_INPUT")

    # Do not inspect, normalize or retain text for forbidden/failed extraction.
    if extraction == "EXCLUDED":
        return _result(extraction, "SKIPPED", "UNAVAILABLE", "PRIVACY_EXCLUDED")
    if extraction == "FAILED":
        return _result(extraction, "SKIPPED", "UNAVAILABLE", "EXTRACTION_FAILED")
    if extraction == "UNSUPPORTED":
        return _result(extraction, "SKIPPED", "UNAVAILABLE", "UNSUPPORTED_EXTRACTION")

    raw_context = EventContext(
        payload.get("eventId"), payload.get("sessionId"), payload.get("runId"),
        payload.get("goalVersion"), payload.get("navigationId"),
    )
    if not _valid_context(raw_context):
        return _result(extraction, "FAILED", "UNAVAILABLE", "INVALID_INPUT")
    if raw_context != current_context:
        return _result(extraction, "STALE_EVENT", "UNAVAILABLE", "STALE_EVENT")

    raw_title = payload.get("title")
    if raw_title is not None and (not isinstance(raw_title, str) or len(raw_title) > 8000):
        return _result(extraction, "FAILED", "UNAVAILABLE", "INVALID_INPUT")
    try:
        raw_passages = parse_passages(payload.get("passages"), method)
    except InvalidStructure:
        return _result(extraction, "FAILED", "UNAVAILABLE", "INVALID_INPUT")
    if len(raw_title or "") + sum(len(p.text) for p in raw_passages) > _MAX_TOTAL_CHARACTERS:
        return _result(extraction, "FAILED", "UNAVAILABLE", "INPUT_TOO_LARGE")

    raw_texts = ([raw_title] if raw_title is not None else []) + [p.text for p in raw_passages]
    if any(_sensitive(text) for text in raw_texts):
        return _result(extraction, "BLOCKED", "UNAVAILABLE", "PRIVACY_BLOCKED")

    title = clean_text(raw_title, "TITLE") if raw_title is not None else None
    passages = tuple(
        Passage(p.id, p.kind, p.order, clean_text(p.text, p.kind), p.recognition_confidence)
        for p in raw_passages
    )
    if any(_sensitive(text) for text in ([title] if title is not None else []) + [p.text for p in passages]):
        return _result(extraction, "BLOCKED", "UNAVAILABLE", "PRIVACY_BLOCKED")
    if any(_BROKEN.search(text) for text in ([title] if title is not None else []) + [p.text for p in passages]):
        return _result(extraction, "FAILED", "INSUFFICIENT", "CORRUPT_TEXT")

    passages = deduplicate(tuple(p for p in passages if p.text.strip()))
    # A title or heading alone is insufficient evidence for content analysis.
    substantive = any(p.kind in {"BODY", "CODE", "TABLE", "OCR"} for p in passages)
    if not substantive:
        return _result(extraction, "PARTIAL", "INSUFFICIENT", "INSUFFICIENT_CONTENT")

    policy = policy or PreprocessingPolicy()
    if method == "OCR" and policy.min_ocr_confidence is not None and any(
        p.recognition_confidence is not None
        and p.recognition_confidence < policy.min_ocr_confidence
        for p in passages
    ):
        return _result(extraction, "PARTIAL", "INSUFFICIENT", "LOW_OCR_CONFIDENCE")
    if extraction == "PARTIAL":
        return _result(extraction, "PARTIAL", "INSUFFICIENT", "PARTIAL_EXTRACTION")

    return PreprocessingResult(
        extraction_status=extraction,
        preprocessing_status="COMPLETED",
        quality_status="SUFFICIENT",
        title=title,
        passages=passages,
        content_hash=content_fingerprint(title, passages),
    )

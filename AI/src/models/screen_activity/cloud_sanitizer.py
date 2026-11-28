"""Sanitize text before sending screen/goal payloads to the external JEV cloud API."""
from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any, Sequence

MAX_REDACTION_RATIO = 0.40

_PEM_PRIVATE_KEY_RE = re.compile(
    r"-----BEGIN\s+(?:[A-Z0-9]+\s+)*PRIVATE KEY-----",
    re.IGNORECASE,
)
_DB_URL_CREDS_RE = re.compile(
    r"([a-zA-Z][a-zA-Z0-9+.-]*://)([^\s/:@]+:[^\s/@]+)(@)",
)
_BEARER_RE = re.compile(r"\b(Bearer\s+)([A-Za-z0-9._~+/=-]{8,})\b", re.IGNORECASE)
_JWT_RE = re.compile(r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b")
_API_KEY_RE = re.compile(
    r"\b(?:sk-(?:ant-|proj-)?[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9]{20,}|"
    r"github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16}|AIza[0-9A-Za-z_-]{35}|"
    r"xox[baprs]-[0-9A-Za-z-]{10,})\b"
)
_ENV_SECRET_RE = re.compile(
    r"\b([A-Za-z0-9_]*(?:API_KEY|SECRET_KEY|CLIENT_SECRET|ACCESS_TOKEN|PRIVATE_KEY|"
    r"PASSWORD|PASSWD|SECRET|TOKEN))\s*([:=])\s*(['\"]?)([^\s'\"`#,;]{6,})\3",
    re.IGNORECASE,
)
_RRN_RE = re.compile(r"(?<!\d)\d{6}-[1-4]\d{6}(?!\d)")
_CARD_RE = re.compile(r"(?<!\d)(?:\d{4}[ -]?){3}\d{4}(?!\d)")
_EMAIL_RE = re.compile(
    r"(?<![A-Za-z0-9_.+-])[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?![A-Za-z0-9_.-])"
)
_PHONE_RE = re.compile(r"(?<!\d)01[016789][ -]?\d{3,4}[ -]?\d{4}(?!\d)")
_IPV4_RE = re.compile(
    r"(?<!\d)(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}"
    r"(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?!\d)"
)

_DUMMY_SECRET_MARKERS = ("your_", "example", "placeholder", "changeme", "dummy", "[secret]", "[api_key]")


@dataclass(frozen=True, slots=True)
class SanitizationResult:
    text: str
    redacted_chars: int
    original_chars: int
    redaction_count: int
    blocked: bool
    reason: str | None = None


def sanitize_text_for_cloud(
    text: str,
    *,
    max_redaction_ratio: float = MAX_REDACTION_RATIO,
    check_ratio: bool = True,
) -> SanitizationResult:
    """Redact credentials/PII and block payloads dominated by secrets."""
    if not text:
        return SanitizationResult(text="", redacted_chars=0, original_chars=0, redaction_count=0, blocked=False)
    if _PEM_PRIVATE_KEY_RE.search(text):
        return SanitizationResult(
            text="",
            redacted_chars=len(text),
            original_chars=len(text),
            redaction_count=1,
            blocked=True,
            reason="PEM_PRIVATE_KEY",
        )

    original_chars = len(text)
    redacted_chars = 0
    redaction_count = 0

    def _sub_full(pattern: re.Pattern[str], tag: str, value: str) -> str:
        nonlocal redacted_chars, redaction_count

        def repl(match: re.Match[str]) -> str:
            nonlocal redacted_chars, redaction_count
            redacted_chars += len(match.group(0))
            redaction_count += 1
            return tag

        return pattern.sub(repl, value)

    def _sub_db(match: re.Match[str]) -> str:
        nonlocal redacted_chars, redaction_count
        redacted_chars += len(match.group(2))
        redaction_count += 1
        return f"{match.group(1)}[CREDENTIALS]{match.group(3)}"

    def _sub_bearer(match: re.Match[str]) -> str:
        nonlocal redacted_chars, redaction_count
        redacted_chars += len(match.group(2))
        redaction_count += 1
        return f"{match.group(1)}[AUTH_TOKEN]"

    def _sub_env(match: re.Match[str]) -> str:
        nonlocal redacted_chars, redaction_count
        secret_val = match.group(4)
        lowered = secret_val.casefold()
        if any(marker in lowered for marker in _DUMMY_SECRET_MARKERS) or set(secret_val) <= {"*", "x", "X"}:
            return match.group(0)
        redacted_chars += len(secret_val)
        redaction_count += 1
        quote = match.group(3)
        return f"{match.group(1)}{match.group(2)}{quote}[SECRET]{quote}"

    cleaned = _DB_URL_CREDS_RE.sub(_sub_db, text)
    cleaned = _BEARER_RE.sub(_sub_bearer, cleaned)
    cleaned = _sub_full(_JWT_RE, "[AUTH_TOKEN]", cleaned)
    cleaned = _sub_full(_API_KEY_RE, "[API_KEY]", cleaned)
    cleaned = _ENV_SECRET_RE.sub(_sub_env, cleaned)
    cleaned = _sub_full(_RRN_RE, "[RRN]", cleaned)
    cleaned = _sub_full(_CARD_RE, "[CARD]", cleaned)
    cleaned = _sub_full(_EMAIL_RE, "[EMAIL]", cleaned)
    cleaned = _sub_full(_PHONE_RE, "[PHONE]", cleaned)
    cleaned = _sub_full(_IPV4_RE, "[IP_ADDR]", cleaned)

    if check_ratio and original_chars > 0 and (redacted_chars / original_chars) >= max_redaction_ratio:
        return SanitizationResult(
            text="",
            redacted_chars=redacted_chars,
            original_chars=original_chars,
            redaction_count=redaction_count,
            blocked=True,
            reason="EXCESSIVE_REDACTION",
        )

    return SanitizationResult(
        text=cleaned,
        redacted_chars=redacted_chars,
        original_chars=original_chars,
        redaction_count=redaction_count,
        blocked=False,
    )


@dataclass(frozen=True, slots=True)
class SanitizedGoalItem:
    goal_id: str
    text: str
    main_topic: str
    core_topics: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class SanitizedMatchPayload:
    title: str
    body: str
    goals: tuple[SanitizedGoalItem, ...]
    blocked: bool
    reason: str | None = None
    redaction_count: int = 0


def sanitize_goal_match_payload(
    *,
    title: str,
    body: str,
    goals: Sequence[Any],
    max_body_chars: int = 2_000,
) -> SanitizedMatchPayload:
    """Sanitize screen title/body and goal fields before cloud JEV serialization."""
    title_res = sanitize_text_for_cloud(title, check_ratio=False)
    if title_res.blocked:
        return SanitizedMatchPayload(title="", body="", goals=(), blocked=True, reason=title_res.reason)

    body_res = sanitize_text_for_cloud(body[:max_body_chars], check_ratio=True)
    if body_res.blocked:
        return SanitizedMatchPayload(title="", body="", goals=(), blocked=True, reason=body_res.reason)

    total_redactions = title_res.redaction_count + body_res.redaction_count
    sanitized_goals: list[SanitizedGoalItem] = []
    for goal in goals:
        g_text = sanitize_text_for_cloud(goal.text, check_ratio=False)
        if g_text.blocked:
            return SanitizedMatchPayload(title="", body="", goals=(), blocked=True, reason=g_text.reason)
        total_redactions += g_text.redaction_count

        profile = getattr(goal, "profile", None)
        main_topic = ""
        core_topics: list[str] = []
        if profile is not None:
            m_res = sanitize_text_for_cloud(profile.main_topic, check_ratio=False)
            if m_res.blocked:
                return SanitizedMatchPayload(title="", body="", goals=(), blocked=True, reason=m_res.reason)
            main_topic = m_res.text
            total_redactions += m_res.redaction_count
            for topic in profile.core_topics:
                t_res = sanitize_text_for_cloud(topic, check_ratio=False)
                if t_res.blocked:
                    return SanitizedMatchPayload(title="", body="", goals=(), blocked=True, reason=t_res.reason)
                core_topics.append(t_res.text)
                total_redactions += t_res.redaction_count

        sanitized_goals.append(
            SanitizedGoalItem(
                goal_id=goal.goal_id,
                text=g_text.text,
                main_topic=main_topic,
                core_topics=tuple(core_topics),
            )
        )

    return SanitizedMatchPayload(
        title=title_res.text,
        body=body_res.text,
        goals=tuple(sanitized_goals),
        blocked=False,
        redaction_count=total_redactions,
    )

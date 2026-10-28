"""MVP acquisition policy; extraction and latest-device state belong to callers."""
from ..preprocessing.models import ContentSource

ACQUISITION_POLICY_VERSION = "screen-mvp-2026-10-26"
METADATA_SOURCES = frozenset({ContentSource.APP_META, ContentSource.TAB_META})
_NEXT = {ContentSource.APP_META: ContentSource.DESKTOP_APP_OCR,
         ContentSource.TAB_META: ContentSource.CHROME_DOM}
# These are invalid observations/execution failures, not requests for more content.
_STOP_REASONS = frozenset({
    "EXCLUDED", "PRIVACY_BLOCKED", "INACTIVE_TARGET", "STALE_EVENT", "STAGE_MISMATCH",
    "CONTEXT_UNAVAILABLE", "OWNER_CHANGED", "GOAL_VERSION_MISMATCH", "IDEMPOTENCY_CONFLICT",
    "MODEL_OR_SCORING_FAILED", "MODEL_UNAVAILABLE",
    "ANALYSIS_FAILED", "ANALYSIS_UNAVAILABLE", "CHROME_OCR_DEFERRED_P1",
    "CHROME_EXTENSION_REQUIRED", "CLIENT_GUARD_REQUIRED",
})


def desktop_target_block_reason(source: ContentSource, app_name: str) -> str | None:
    """Defense for known misrouted targets; the Client owns full bundle-ID guards."""
    if source not in {ContentSource.APP_META, ContentSource.DESKTOP_APP_OCR}:
        return None
    name = app_name.strip().casefold()
    if name in {"chrome", "google chrome", "com.google.chrome"}:
        return "CHROME_EXTENSION_REQUIRED"
    if name in {"safari", "arc", "edge", "microsoft edge", "firefox", "whale", "naver whale",
                "brave", "brave browser", "opera", "vivaldi", "chromium", "duckduckgo",
                "focuson", "floating poki"}:
        return "CLIENT_GUARD_REQUIRED"
    return None


def next_content_source(source: ContentSource, label: str, reason_code: str) -> ContentSource | None:
    """Only valid, non-related metadata observations may acquire one body stage."""
    if label not in {"UNRELATED", "UNCERTAIN"} or reason_code in _STOP_REASONS:
        return None
    return _NEXT.get(source)

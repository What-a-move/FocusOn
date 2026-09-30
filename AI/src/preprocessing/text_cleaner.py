"""Conservative text cleaning and deterministic content fingerprinting."""

import hashlib
import html
import json
import re

from .models import Passage


PREPROCESSING_VERSION = "1"
_HTML_TAG = re.compile(r"</?(?:p|div|span|br|li|ul|ol|h[1-6]|strong|em|b|i)\b[^>]*>", re.I)
_HORIZONTAL_SPACE = re.compile(r"[^\S\n]+")
_NEWLINES = re.compile(r"\n{3,}")


def clean_text(text: str, kind: str) -> str:
    cleaned = html.unescape(text).replace("\r\n", "\n").replace("\r", "\n")
    if kind not in {"CODE", "TABLE"}:
        cleaned = _HTML_TAG.sub(" ", cleaned)
        cleaned = _HORIZONTAL_SPACE.sub(" ", cleaned)
        cleaned = re.sub(r" *\n *", "\n", cleaned)
        cleaned = _NEWLINES.sub("\n\n", cleaned)
        return cleaned.strip()

    # Indentation and line breaks carry meaning in code and tables.
    return "\n".join(line.rstrip() for line in cleaned.split("\n")).strip("\n")


def deduplicate(passages: tuple[Passage, ...]) -> tuple[Passage, ...]:
    seen: set[tuple[str, str]] = set()
    unique: list[Passage] = []
    for passage in passages:
        key = (passage.kind, passage.text)
        if key not in seen:
            seen.add(key)
            unique.append(passage)
    return tuple(unique)


def content_fingerprint(title: str | None, passages: tuple[Passage, ...]) -> str:
    # Event, navigation and passage IDs do not change the content identity.
    content = {
        "version": PREPROCESSING_VERSION,
        "title": title,
        "passages": [[passage.kind, passage.text] for passage in passages],
    }
    canonical = json.dumps(content, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

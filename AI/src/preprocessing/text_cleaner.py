from __future__ import annotations

import html
import re
import unicodedata
from dataclasses import dataclass, field
from typing import FrozenSet

from .models import PassageKind


_HTML_BLOCK_RE = re.compile(
    r"</?(?:p|br|div|li|ul|ol|section|article|header|footer|h[1-6]|tr|table)[^>]*>",
    re.IGNORECASE,
)
_HTML_TAG_RE = re.compile(r"</?[A-Za-z][^>]{0,500}>")
_SENTENCE_BOUNDARY_RE = re.compile(r"(?<=[.!?。！？])\s+")
_SPACE_RE = re.compile(r"[ \t\f\v]+")
_BLANK_LINES_RE = re.compile(r"\n{3,}")


@dataclass(frozen=True, slots=True)
class CleanedText:
    text: str = field(repr=False)
    removed_duplicate_count: int = 0
    removed_ui_fragment_count: int = 0


def clean_text(
    text: str,
    kind: PassageKind,
    ui_fragments: FrozenSet[str],
) -> CleanedText:
    normalized = html.unescape(_normalize_characters(text))
    if kind in {PassageKind.CODE, PassageKind.TABLE}:
        return CleanedText(text=_clean_structured_text(normalized))

    normalized = _HTML_BLOCK_RE.sub("\n", normalized)
    normalized = _HTML_TAG_RE.sub("", normalized)

    cleaned_lines: list[str] = []
    seen_lines: set[str] = set()
    seen_sentences: set[str] = set()
    removed_duplicates = 0
    removed_ui_fragments = 0

    for raw_line in normalized.split("\n"):
        line = _SPACE_RE.sub(" ", raw_line).strip()
        if not line:
            continue
        if line in ui_fragments:
            removed_ui_fragments += 1
            continue

        unique_sentences: list[str] = []
        for sentence in _SENTENCE_BOUNDARY_RE.split(line):
            sentence = sentence.strip()
            if not sentence:
                continue
            if sentence in ui_fragments:
                removed_ui_fragments += 1
                continue
            if sentence in seen_sentences:
                removed_duplicates += 1
                continue
            seen_sentences.add(sentence)
            unique_sentences.append(sentence)
        line = " ".join(unique_sentences)
        if not line:
            continue
        if line in seen_lines:
            removed_duplicates += 1
            continue
        seen_lines.add(line)
        cleaned_lines.append(line)

    return CleanedText(
        text="\n".join(cleaned_lines),
        removed_duplicate_count=removed_duplicates,
        removed_ui_fragment_count=removed_ui_fragments,
    )


def split_text(
    text: str,
    max_chars: int,
    overlap_chars: int,
    preserve_lines: bool,
) -> tuple[str, ...]:
    if not text:
        return ()
    if len(text) <= max_chars:
        return (text,)

    chunks: list[str] = []
    start = 0
    while start < len(text):
        hard_end = min(start + max_chars, len(text))
        end = _find_chunk_end(text, start, hard_end, preserve_lines)
        if end <= start:
            end = hard_end

        chunk = text[start:end].rstrip()
        if not preserve_lines:
            chunk = chunk.strip()
        if chunk:
            chunks.append(chunk)
        if end >= len(text):
            break

        next_start = max(0, end - overlap_chars)
        if not preserve_lines:
            next_start = _move_to_word_start(text, next_start, end)
        if next_start <= start:
            next_start = end
        start = next_start

    return tuple(chunks)


def _normalize_characters(text: str) -> str:
    text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\u00a0", " ")
    return "".join(
        character
        for character in text
        if character in "\n\t" or unicodedata.category(character) != "Cc"
    )


def _clean_structured_text(text: str) -> str:
    lines = [line.rstrip() for line in text.split("\n")]
    return _BLANK_LINES_RE.sub("\n\n", "\n".join(lines)).strip("\n")


def _find_chunk_end(
    text: str, start: int, hard_end: int, preserve_lines: bool
) -> int:
    if hard_end >= len(text):
        return hard_end
    search_start = start + max(1, (hard_end - start) // 2)
    candidate = text.rfind("\n", search_start, hard_end)
    if candidate >= search_start:
        return candidate + 1
    if preserve_lines:
        return hard_end
    for marker in (". ", "? ", "! ", "。", "？", "！", " "):
        candidate = text.rfind(marker, search_start, hard_end)
        if candidate >= search_start:
            return candidate + len(marker)
    return hard_end


def _move_to_word_start(text: str, start: int, end: int) -> int:
    while start < end and start > 0 and not text[start - 1].isspace():
        start += 1
    while start < end and text[start].isspace():
        start += 1
    return start

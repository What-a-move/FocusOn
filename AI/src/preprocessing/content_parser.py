from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass

from .models import (
    CleanedPassage,
    ContentSource,
    PassageKind,
    PreprocessingConfig,
    PreprocessingRequest,
    RawPassage,
)
from .text_cleaner import clean_text, split_text


@dataclass(frozen=True, slots=True)
class ParsedContent:
    passages: tuple[CleanedPassage, ...]
    original_passage_count: int
    removed_duplicate_count: int
    removed_ui_fragment_count: int


def parse_content(
    request: PreprocessingRequest,
    config: PreprocessingConfig | None = None,
) -> ParsedContent:
    settings = config or PreprocessingConfig()
    raw_passages = _collect_raw_passages(request)
    cleaned_passages: list[CleanedPassage] = []
    seen_passages: set[tuple[PassageKind, str]] = set()
    removed_duplicates = 0
    removed_ui_fragments = 0
    total_chars = 0

    for raw_passage in raw_passages:
        cleaned = clean_text(raw_passage.text, raw_passage.kind, settings.ui_fragments)
        removed_duplicates += cleaned.removed_duplicate_count
        removed_ui_fragments += cleaned.removed_ui_fragment_count
        if not cleaned.text:
            continue

        passage_key = (raw_passage.kind, cleaned.text)
        if passage_key in seen_passages:
            removed_duplicates += 1
            continue
        seen_passages.add(passage_key)

        chunks = split_text(
            cleaned.text,
            max_chars=settings.max_passage_chars,
            overlap_chars=settings.overlap_chars,
            preserve_lines=raw_passage.kind in {PassageKind.CODE, PassageKind.TABLE},
        )
        for chunk in chunks:
            if len(cleaned_passages) >= settings.max_passages:
                break
            remaining = settings.max_total_chars - total_chars
            if remaining <= 0:
                break
            bounded_chunk = chunk[:remaining].rstrip()
            if not bounded_chunk:
                continue
            cleaned_passages.append(
                CleanedPassage(
                    id=f"passage-{len(cleaned_passages) + 1:03d}",
                    kind=raw_passage.kind,
                    text=bounded_chunk,
                    order=len(cleaned_passages),
                )
            )
            total_chars += len(bounded_chunk)

        if (
            len(cleaned_passages) >= settings.max_passages
            or total_chars >= settings.max_total_chars
        ):
            break

    return ParsedContent(
        passages=tuple(cleaned_passages),
        original_passage_count=len(raw_passages),
        removed_duplicate_count=removed_duplicates,
        removed_ui_fragment_count=removed_ui_fragments,
    )


def build_content_hash(passages: tuple[CleanedPassage, ...]) -> str:
    canonical_content = [
        {
            "kind": passage.kind.value,
            "order": passage.order,
            "text": passage.text,
        }
        for passage in passages
    ]
    encoded = json.dumps(
        canonical_content,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _collect_raw_passages(request: PreprocessingRequest) -> tuple[RawPassage, ...]:
    collected: list[RawPassage] = []
    if request.page_title.strip():
        collected.append(
            RawPassage(
                id="page-title",
                kind=PassageKind.TITLE,
                text=request.page_title,
                order=0,
            )
        )

    if request.passages:
        collected.extend(sorted(request.passages, key=lambda passage: passage.order))
    elif request.text:
        collected.append(
            RawPassage(
                id="content",
                kind=(
                    PassageKind.OCR
                    if request.content_source
                    in {
                        ContentSource.CHROME_VIEWPORT_OCR,
                        ContentSource.DESKTOP_APP_OCR,
                    }
                    else PassageKind.BODY
                ),
                text=request.text,
                order=1 if collected else 0,
            )
        )
    return tuple(collected)

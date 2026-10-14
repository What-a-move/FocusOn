from __future__ import annotations

import re

from ..api.schemas import ContentInput, ExclusionMode, VerifiedContext
from .models import (ContentSource, CurrentAnalysisContext, PassageKind,
                     PreprocessingRequest, RawPassage)

PREPROCESSING_VERSION = "school-text-v2"

_HEADING_RE = re.compile(r"^ {0,3}#{1,3}(?:[ \t]+|$)")
_FENCE_RE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")


def observation_is_current(content: ContentInput, context: VerifiedContext) -> bool:
    return (
        context.active
        and content.session_id == context.session_id
        and content.run_id == context.run_id
        and content.goal_id == context.goal_id
        and content.goal_version == context.goal_version
        and content.event_id == context.event_id
        and content.navigation_id == context.navigation_id
        and content.observed_at == context.observed_at
        and content.session_status == context.session_status
        and content.exclusion_mode == context.exclusion_mode
    )


def adapt_content(content: ContentInput, context: VerifiedContext) -> PreprocessingRequest:
    ocr_source = content.content_source in {ContentSource.CHROME_VIEWPORT_OCR, ContentSource.DESKTOP_APP_OCR}
    # Client qualityScore is not the final score. DOM uses measured text quality;
    # OCR additionally requires a successful capture and recognition confidence.
    source_quality = 1.0
    if ocr_source:
        source_quality = content.ocr.confidence if content.ocr and content.ocr.capture_status == "SUCCESS" else 0.0
    return PreprocessingRequest(
        session_id=content.session_id, run_id=content.run_id,
        goal_id=content.goal_id, goal_version=content.goal_version,
        event_id=content.event_id, navigation_id=content.navigation_id,
        content_source=content.content_source, extraction_status=content.extraction_status,
        quality_score=source_quality, page_title=content.page_title,
        passages=_split_passages(content), is_sensitive=content.is_sensitive,
        is_excluded=content.is_excluded or context.exclusion_mode is not ExclusionMode.NONE,
    )


def preprocessing_context(context: VerifiedContext) -> CurrentAnalysisContext:
    return CurrentAnalysisContext(context.run_id, context.goal_version, context.navigation_id)


def _split_passages(content: ContentInput) -> tuple[RawPassage, ...]:
    kind = {
        "CODE": PassageKind.CODE, "TABLE": PassageKind.TABLE,
        "ERROR": PassageKind.CODE,
    }.get(content.content_type, PassageKind.OCR if content.ocr else PassageKind.BODY)
    if kind in {PassageKind.CODE, PassageKind.TABLE}:
        blocks = [(kind, content.text)]
    else:
        blocks = _markdown_blocks(content.text, kind)
    return tuple(RawPassage(f"raw-{index:03d}", block_kind, text, index)
                 for index, (block_kind, text) in enumerate(blocks) if text.strip())


def _markdown_blocks(text: str, body_kind: PassageKind) -> list[tuple[PassageKind, str]]:
    """Preserve explicit Markdown structure without guessing plain-text headings."""
    blocks: list[tuple[PassageKind, str]] = []
    body: list[str] = []
    code: list[str] = []
    closing_fence: re.Pattern[str] | None = None

    def flush_body() -> None:
        if body:
            blocks.append((body_kind, "".join(body)))
            body.clear()

    for line in text.splitlines(keepends=True):
        syntax = line.rstrip("\r\n")
        if closing_fence is not None:
            code.append(line)
            if closing_fence.fullmatch(syntax):
                if any(part.strip() for part in code[1:-1]):
                    blocks.append((PassageKind.CODE, "".join(code)))
                code = []
                closing_fence = None
            continue

        opening = _FENCE_RE.fullmatch(syntax)
        if opening and not (opening[1][0] == "`" and "`" in opening[2]):
            flush_body()
            fence = opening[1]
            closing_fence = re.compile(r" {0,3}" + re.escape(fence[0]) + "{" + str(len(fence)) + r",}[ \t]*")
            code = [line]
        elif _HEADING_RE.match(syntax):
            flush_body()
            blocks.append((PassageKind.HEADING, line))
        elif line.strip():
            body.append(line)
        else:
            flush_body()

    # An unfinished fence still marks its remaining source as code. Its info
    # string alone is not evidence of actual body content.
    if code and any(part.strip() for part in code[1:]):
        blocks.append((PassageKind.CODE, "".join(code)))
    flush_body()
    return blocks

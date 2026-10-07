from __future__ import annotations

import html
import re
import unicodedata

from .content_parser import build_content_hash, parse_content
from .models import (
    CurrentAnalysisContext,
    CleanedPassage,
    ExtractionStatus,
    PassageKind,
    PreprocessingConfig,
    PreprocessingRequest,
    PreprocessingResult,
    PreprocessingStatus,
    QualityMetrics,
    RejectionReason,
)


_BEARER_RE = re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{8,}\b", re.IGNORECASE)
_JWT_RE = re.compile(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b")
_API_KEY_RE = re.compile(
    r"\b(?:sk-[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})\b"
)
_EMAIL_RE = re.compile(r"(?<![A-Za-z0-9_.+-])[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?![A-Za-z0-9_.-])")
_PHONE_RE = re.compile(r"(?<!\d)01[016789][ -]?\d{3,4}[ -]?\d{4}(?!\d)")
_CARD_CANDIDATE_RE = re.compile(r"(?<!\d)(?:\d[ -]?){13,19}(?!\d)")


def validate_and_preprocess(
    request: PreprocessingRequest,
    context: CurrentAnalysisContext,
    config: PreprocessingConfig | None = None,
) -> PreprocessingResult:
    settings = config or PreprocessingConfig()

    if request.is_excluded or request.extraction_status is ExtractionStatus.EXCLUDED:
        return _rejected(PreprocessingStatus.BLOCKED, RejectionReason.EXCLUDED)

    if request.is_sensitive or _contains_sensitive_data(request):
        return _rejected(
            PreprocessingStatus.BLOCKED, RejectionReason.PRIVACY_BLOCKED
        )

    if request.extraction_status is ExtractionStatus.FAILED:
        return _rejected(
            PreprocessingStatus.FAILED, RejectionReason.EXTRACTION_FAILED
        )

    if request.extraction_status is ExtractionStatus.UNSUPPORTED:
        return _rejected(PreprocessingStatus.FAILED, RejectionReason.UNSUPPORTED)

    if _is_stale(request, context):
        return _rejected(PreprocessingStatus.STALE, RejectionReason.STALE_EVENT)

    if request.event_id in context.processed_event_ids:
        return _rejected(
            PreprocessingStatus.DUPLICATE, RejectionReason.DUPLICATE_EVENT
        )

    parsed = parse_content(request, settings)
    broken_ratio = _broken_character_ratio(_raw_text(request))
    character_count = sum(len(passage.text) for passage in parsed.passages)
    quality = QualityMetrics(
        original_passage_count=parsed.original_passage_count,
        cleaned_passage_count=len(parsed.passages),
        removed_duplicate_count=parsed.removed_duplicate_count,
        removed_ui_fragment_count=parsed.removed_ui_fragment_count,
        character_count=character_count,
        broken_character_ratio=broken_ratio,
        input_quality_score=float(request.quality_score),
    )

    quality_reasons = _quality_reasons(request, parsed.passages, quality, settings)
    if quality_reasons:
        return PreprocessingResult(
            status=PreprocessingStatus.LOW_QUALITY,
            should_analyze=False,
            passages=parsed.passages,
            reasons=quality_reasons,
            quality=quality,
        )

    content_hash = build_content_hash(parsed.passages)
    if content_hash in context.seen_content_hashes:
        return PreprocessingResult(
            status=PreprocessingStatus.DUPLICATE,
            should_analyze=False,
            content_hash=content_hash,
            reasons=(RejectionReason.DUPLICATE_CONTENT,),
            quality=quality,
        )

    status = (
        PreprocessingStatus.PARTIAL
        if request.extraction_status is ExtractionStatus.PARTIAL
        else PreprocessingStatus.READY
    )
    return PreprocessingResult(
        status=status,
        should_analyze=True,
        passages=parsed.passages,
        content_hash=content_hash,
        quality=quality,
    )


def contains_sensitive_data(text: str) -> bool:
    if not text:
        return False
    text = unicodedata.normalize("NFKC", html.unescape(text))
    if any(
        pattern.search(text)
        for pattern in (_BEARER_RE, _JWT_RE, _API_KEY_RE, _EMAIL_RE, _PHONE_RE)
    ):
        return True
    return any(_passes_luhn(candidate.group(0)) for candidate in _CARD_CANDIDATE_RE.finditer(text))


def _contains_sensitive_data(request: PreprocessingRequest) -> bool:
    return any(
        contains_sensitive_data(value)
        for value in (
            request.page_title,
            request.text,
            *(passage.text for passage in request.passages),
        )
    )


def _is_stale(
    request: PreprocessingRequest, context: CurrentAnalysisContext
) -> bool:
    return (
        request.run_id != context.run_id
        or request.goal_version != context.goal_version
        or request.navigation_id != context.navigation_id
    )


def _quality_reasons(
    request: PreprocessingRequest,
    passages: tuple[CleanedPassage, ...],
    quality: QualityMetrics,
    config: PreprocessingConfig,
) -> tuple[RejectionReason, ...]:
    reasons: list[RejectionReason] = []
    if not passages:
        reasons.append(RejectionReason.EMPTY_CONTENT)
    has_short_form_content = any(
        passage.kind in {PassageKind.TITLE, PassageKind.CODE, PassageKind.TABLE}
        and passage.text
        for passage in passages
    )
    if (
        quality.character_count < config.min_text_chars
        and not has_short_form_content
    ):
        reasons.append(RejectionReason.TOO_SHORT)
    if float(request.quality_score) < config.min_quality_score:
        reasons.append(RejectionReason.LOW_CONFIDENCE)
    if quality.broken_character_ratio > config.max_broken_character_ratio:
        reasons.append(RejectionReason.BROKEN_CONTENT)
    return tuple(reasons)


def _raw_text(request: PreprocessingRequest) -> str:
    return "\n".join(
        value
        for value in (
            request.page_title,
            request.text,
            *(passage.text for passage in request.passages),
        )
        if value
    )


def _broken_character_ratio(text: str) -> float:
    if not text:
        return 0.0
    broken_count = sum(
        character == "\ufffd"
        or (unicodedata.category(character) == "Cc" and character not in "\n\r\t")
        for character in text
    )
    return broken_count / len(text)


def _passes_luhn(candidate: str) -> bool:
    digits = [int(character) for character in candidate if character.isdigit()]
    if not 13 <= len(digits) <= 19 or len(set(digits)) == 1:
        return False
    checksum = 0
    parity = len(digits) % 2
    for index, digit in enumerate(digits):
        if index % 2 == parity:
            digit *= 2
            if digit > 9:
                digit -= 9
        checksum += digit
    return checksum % 10 == 0


def _rejected(
    status: PreprocessingStatus, reason: RejectionReason
) -> PreprocessingResult:
    return PreprocessingResult(
        status=status,
        should_analyze=False,
        reasons=(reason,),
    )

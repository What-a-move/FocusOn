from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import FrozenSet, Tuple


class ContentSource(str, Enum):
    APP_META = "APP_META"
    TAB_META = "TAB_META"
    CHROME_DOM = "CHROME_DOM"
    CHROME_VIEWPORT_OCR = "CHROME_VIEWPORT_OCR"
    DESKTOP_APP_OCR = "DESKTOP_APP_OCR"


class ExtractionStatus(str, Enum):
    SUCCESS = "SUCCESS"
    PARTIAL = "PARTIAL"
    FAILED = "FAILED"
    EXCLUDED = "EXCLUDED"
    UNSUPPORTED = "UNSUPPORTED"


class PassageKind(str, Enum):
    TITLE = "TITLE"
    BODY = "BODY"
    CODE = "CODE"
    TABLE = "TABLE"
    OCR = "OCR"


class PreprocessingStatus(str, Enum):
    READY = "READY"
    PARTIAL = "PARTIAL"
    LOW_QUALITY = "LOW_QUALITY"
    BLOCKED = "BLOCKED"
    FAILED = "FAILED"
    DUPLICATE = "DUPLICATE"
    STALE = "STALE"


class RejectionReason(str, Enum):
    EXCLUDED = "EXCLUDED"
    PRIVACY_BLOCKED = "PRIVACY_BLOCKED"
    EXTRACTION_FAILED = "EXTRACTION_FAILED"
    UNSUPPORTED = "UNSUPPORTED"
    EMPTY_CONTENT = "EMPTY_CONTENT"
    TOO_SHORT = "TOO_SHORT"
    LOW_CONFIDENCE = "LOW_CONFIDENCE"
    BROKEN_CONTENT = "BROKEN_CONTENT"
    DUPLICATE_EVENT = "DUPLICATE_EVENT"
    DUPLICATE_CONTENT = "DUPLICATE_CONTENT"
    STALE_EVENT = "STALE_EVENT"


@dataclass(frozen=True, slots=True)
class RawPassage:
    id: str
    kind: PassageKind
    text: str = field(repr=False)
    order: int

    def __post_init__(self) -> None:
        _validate_identifier(self.id)
        if not isinstance(self.kind, PassageKind):
            raise TypeError("passage kind must be a PassageKind")
        if not isinstance(self.text, str):
            raise TypeError("passage text must be a string")
        if not isinstance(self.order, int) or isinstance(self.order, bool) or self.order < 0:
            raise ValueError("passage order must be a non-negative integer")


@dataclass(frozen=True, slots=True)
class CleanedPassage:
    id: str
    kind: PassageKind
    text: str = field(repr=False)
    order: int


@dataclass(frozen=True, slots=True)
class PreprocessingRequest:
    session_id: str
    run_id: str
    goal_id: str
    goal_version: int
    event_id: str
    navigation_id: str
    content_source: ContentSource
    extraction_status: ExtractionStatus
    quality_score: float
    text: str = field(default="", repr=False)
    page_title: str = field(default="", repr=False)
    passages: Tuple[RawPassage, ...] = field(default=(), repr=False)
    is_sensitive: bool = False
    is_excluded: bool = False

    def __post_init__(self) -> None:
        for identifier in (
            self.session_id,
            self.run_id,
            self.goal_id,
            self.event_id,
            self.navigation_id,
        ):
            _validate_identifier(identifier)
        if (
            not isinstance(self.goal_version, int)
            or isinstance(self.goal_version, bool)
            or self.goal_version < 1
        ):
            raise ValueError("goal version must be a positive integer")
        if not isinstance(self.content_source, ContentSource):
            raise TypeError("content source must be a ContentSource")
        if not isinstance(self.extraction_status, ExtractionStatus):
            raise TypeError("extraction status must be an ExtractionStatus")
        if not isinstance(self.quality_score, (int, float)) or isinstance(
            self.quality_score, bool
        ):
            raise TypeError("quality score must be numeric")
        if not 0.0 <= float(self.quality_score) <= 1.0:
            raise ValueError("quality score must be between zero and one")
        if not isinstance(self.text, str) or not isinstance(self.page_title, str):
            raise TypeError("text fields must be strings")
        if not isinstance(self.is_sensitive, bool) or not isinstance(
            self.is_excluded, bool
        ):
            raise TypeError("privacy flags must be boolean")
        if not isinstance(self.passages, tuple) or not all(
            isinstance(passage, RawPassage) for passage in self.passages
        ):
            raise TypeError("passages must be a tuple of RawPassage values")
        if self.text and self.passages:
            raise ValueError("provide flat text or passages, not both")
        passage_ids = [passage.id for passage in self.passages]
        if len(passage_ids) != len(set(passage_ids)):
            raise ValueError("passage identifiers must be unique")
        passage_orders = [passage.order for passage in self.passages]
        if len(passage_orders) != len(set(passage_orders)):
            raise ValueError("passage order values must be unique")


@dataclass(frozen=True, slots=True)
class CurrentAnalysisContext:
    run_id: str
    goal_version: int
    navigation_id: str
    processed_event_ids: FrozenSet[str] = field(default_factory=frozenset)
    seen_content_hashes: FrozenSet[str] = field(default_factory=frozenset)

    def __post_init__(self) -> None:
        _validate_identifier(self.run_id)
        _validate_identifier(self.navigation_id)
        if (
            not isinstance(self.goal_version, int)
            or isinstance(self.goal_version, bool)
            or self.goal_version < 1
        ):
            raise ValueError("goal version must be a positive integer")


@dataclass(frozen=True, slots=True)
class PreprocessingConfig:
    min_text_chars: int = 30
    min_quality_score: float = 0.70
    max_broken_character_ratio: float = 0.20
    max_passages: int = 8
    max_passage_chars: int = 800
    max_total_chars: int = 6_000
    overlap_chars: int = 150
    ui_fragments: FrozenSet[str] = field(
        default_factory=lambda: frozenset({"닫기", "확인", "공유"})
    )

    def __post_init__(self) -> None:
        if self.min_text_chars < 0:
            raise ValueError("minimum text length must be non-negative")
        if not 0.0 <= self.min_quality_score <= 1.0:
            raise ValueError("minimum quality score must be between zero and one")
        if not 0.0 <= self.max_broken_character_ratio <= 1.0:
            raise ValueError("broken character ratio must be between zero and one")
        if self.max_passages < 1 or self.max_passage_chars < 1:
            raise ValueError("passage limits must be positive")
        if self.max_total_chars < self.max_passage_chars:
            raise ValueError("total character limit must allow at least one passage")
        if not 0 <= self.overlap_chars < self.max_passage_chars:
            raise ValueError("overlap must be smaller than the passage limit")


@dataclass(frozen=True, slots=True)
class QualityMetrics:
    original_passage_count: int
    cleaned_passage_count: int
    removed_duplicate_count: int
    removed_ui_fragment_count: int
    character_count: int
    broken_character_ratio: float
    input_quality_score: float


@dataclass(frozen=True, slots=True)
class PreprocessingResult:
    status: PreprocessingStatus
    should_analyze: bool
    passages: Tuple[CleanedPassage, ...] = ()
    content_hash: str | None = None
    reasons: Tuple[RejectionReason, ...] = ()
    quality: QualityMetrics | None = None


def _validate_identifier(value: object) -> None:
    if not isinstance(value, str):
        raise TypeError("identifier must be a string")
    if not 1 <= len(value) <= 128:
        raise ValueError("identifier length is invalid")
    if not (value[0].isascii() and value[0].isalnum()) or any(
        not (
            character.isascii()
            and (character.isalnum() or character in "._:-")
        )
        for character in value
    ):
        raise ValueError("identifier format is invalid")

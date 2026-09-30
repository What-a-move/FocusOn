"""Internal, dependency-free contracts for untrusted extracted text."""

from dataclasses import dataclass, field


@dataclass(frozen=True)
class EventContext:
    event_id: str
    session_id: str
    run_id: str
    goal_version: int
    navigation_id: str


@dataclass(frozen=True)
class Passage:
    id: str
    kind: str
    order: int
    text: str = field(repr=False)
    recognition_confidence: float | None = None


@dataclass(frozen=True)
class PreprocessingResult:
    extraction_status: str
    preprocessing_status: str
    quality_status: str
    reason_code: str | None = None
    title: str | None = field(default=None, repr=False)
    passages: tuple[Passage, ...] = field(default=(), repr=False)
    content_hash: str | None = field(default=None, repr=False)

    @property
    def ready_for_analysis(self) -> bool:
        return self.preprocessing_status == "COMPLETED" and self.quality_status == "SUFFICIENT"

"""Internal school harness schemas; these are not the public Server API."""
from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

from ..preprocessing.models import ContentSource, ExtractionStatus

Identifier = Annotated[str, Field(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]*$")]


class SafeInputError(ValueError):
    """Only a fixed code crosses the application boundary."""


class InternalSchema(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, hide_input_in_errors=True,
                              populate_by_name=False)

    @classmethod
    def parse_safe(cls, value: object):
        try:
            # JSON mode accepts JSON enum strings without coercing scalar types.
            import json
            return cls.model_validate_json(json.dumps(value, allow_nan=False))
        except (ValidationError, ValueError, TypeError, OverflowError):
            pass
        raise SafeInputError("INVALID_INPUT")


class SessionStatus(str, Enum):
    ACTIVE = "ACTIVE"  # Internal PLAN example; public RUNNING maps explicitly.
    RUNNING = "RUNNING"
    DRAFT = "DRAFT"
    PAUSED = "PAUSED"
    AUTO_PAUSED = "AUTO_PAUSED"
    AWAITING_END_CONFIRMATION = "AWAITING_END_CONFIRMATION"
    ENDING = "ENDING"
    ENDED = "ENDED"
    ABANDONED = "ABANDONED"


class ExclusionMode(str, Enum):
    NONE = "NONE"
    ANALYSIS_ONLY = "ANALYSIS_ONLY"
    ANALYSIS_AND_TIME = "ANALYSIS_AND_TIME"


class Observation(InternalSchema):
    session_id: Identifier = Field(alias="sessionId")
    run_id: Identifier = Field(alias="runId")
    goal_id: Identifier = Field(alias="goalId")
    goal_version: Annotated[int, Field(ge=1)] = Field(alias="goalVersion")
    event_id: Identifier = Field(alias="eventId")
    navigation_id: Identifier = Field(alias="navigationId")
    observed_at: datetime = Field(alias="observedAt")
    session_status: SessionStatus = Field(alias="sessionStatus")
    exclusion_mode: ExclusionMode = Field(alias="exclusionMode")

    @field_validator("observed_at")
    @classmethod
    def utc_time(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() != timezone.utc.utcoffset(value):
            raise ValueError("UTC_TIME_REQUIRED")
        return value


class OCRMetadata(InternalSchema):
    confidence: Annotated[float, Field(ge=0, le=1)]
    capture_status: Literal["SUCCESS", "BLACK_SCREEN", "EMPTY_CAPTURE", "OCR_FAILED", "DUPLICATE", "LOW_CONFIDENCE"] = Field(alias="captureStatus")


class ContentInput(Observation):
    content_source: ContentSource = Field(alias="contentSource")
    content_type: Literal["DOCUMENT", "WEB_PAGE", "CODE", "TABLE", "ERROR", "UNKNOWN"] = Field(alias="contentType")
    app_name: Annotated[str, Field(max_length=256)] = Field(default="", alias="appName", repr=False)
    page_title: Annotated[str, Field(max_length=800)] = Field(default="", alias="pageTitle", repr=False)
    url_host: Annotated[str, Field(max_length=253)] = Field(default="", alias="urlHost", repr=False)
    text: Annotated[str, Field(max_length=64_000)] = Field(repr=False)
    extraction_status: ExtractionStatus = Field(alias="extractionStatus")
    is_sensitive: bool = Field(alias="isSensitive")
    is_excluded: bool = Field(alias="isExcluded")
    quality_score: Annotated[float, Field(ge=0, le=1)] | None = Field(default=None, alias="qualityScore")
    ocr: OCRMetadata | None = None

    @field_validator("url_host")
    @classmethod
    def host_only(cls, value: str) -> str:
        import re
        if value and not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?", value):
            raise ValueError("HOST_ONLY_REQUIRED")
        return value


class VerifiedContext(Observation):
    """Caller must supply Server-verified ownership/current state, or a synthetic fixture."""
    user_id: Identifier = Field(alias="userId")
    resume_required: bool = Field(alias="resumeRequired")
    policy_version: Identifier = Field(alias="policyVersion")
    extractor_version: Identifier = Field(alias="extractorVersion")
    state_version: Annotated[int, Field(ge=1)] = Field(alias="stateVersion")

    @property
    def active(self) -> bool:
        return self.session_status in {SessionStatus.ACTIVE, SessionStatus.RUNNING} and not self.resume_required


class ConfirmedGoal(InternalSchema):
    goal_id: Identifier = Field(alias="goalId")
    goal_version: Annotated[int, Field(ge=1)] = Field(alias="goalVersion")
    text: Annotated[str, Field(min_length=1, max_length=2_000)] = Field(repr=False)
    confirmed: bool
    template_id: Identifier = Field(alias="templateId")

    @field_validator("confirmed")
    @classmethod
    def confirmation_required(cls, value: bool) -> bool:
        if value is not True:
            raise ValueError("GOAL_CONFIRMATION_REQUIRED")
        return value

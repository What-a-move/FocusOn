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
    client_run_id: Identifier = Field(alias="clientRunId")
    sequence: Annotated[int, Field(ge=0)] = Field(alias="sequence")
    navigation_id: Identifier = Field(alias="navigationId")
    observed_at: datetime = Field(alias="observedAt")
    content_source: ContentSource = Field(alias="contentSource")
    is_active_target: bool = Field(alias="isActiveTarget")
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
    content_type: Literal["DOCUMENT", "WEB_PAGE", "CODE", "TABLE", "ERROR", "UNKNOWN"] = Field(alias="contentType")
    app_name: Annotated[str, Field(max_length=256)] = Field(default="", alias="appName", repr=False)
    window_title: Annotated[str, Field(max_length=800)] = Field(default="", alias="windowTitle", repr=False)
    page_title: Annotated[str, Field(max_length=800)] = Field(default="", alias="pageTitle", repr=False)
    url_host: Annotated[str, Field(max_length=253)] = Field(default="", alias="urlHost", repr=False)
    text: Annotated[str, Field(max_length=2_000)] = Field(default="", repr=False)
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
    """Server state plus the Client coordinator's currently selected screen target."""
    user_id: Identifier = Field(alias="userId")
    resume_required: bool = Field(alias="resumeRequired")
    policy_version: Identifier = Field(alias="policyVersion")
    extractor_version: Identifier = Field(alias="extractorVersion")
    state_version: Annotated[int, Field(ge=1)] = Field(alias="stateVersion")

    @property
    def active(self) -> bool:
        return self.session_status in {SessionStatus.ACTIVE, SessionStatus.RUNNING} and not self.resume_required


GoalTopic = Annotated[str, Field(min_length=1, max_length=200)]


class GoalProfile(InternalSchema):
    """User-confirmed semantic scope. Page content must never mutate this value."""
    main_topic: GoalTopic = Field(alias="mainTopic")
    purpose: Literal["LEARNING", "IMPLEMENTATION", "PROBLEM_SOLVING", "DEBUGGING", "PRACTICE", "OTHER"]
    core_topics: Annotated[list[GoalTopic], Field(min_length=1, max_length=12)] = Field(alias="coreTopics")
    supporting_topics: Annotated[list[GoalTopic], Field(max_length=12)] = Field(default_factory=list, alias="supportingTopics")
    expected_activities: Annotated[list[GoalTopic], Field(max_length=8)] = Field(default_factory=list, alias="expectedActivities")

    @field_validator("core_topics", "supporting_topics", "expected_activities")
    @classmethod
    def unique_topics(cls, values: list[str]) -> list[str]:
        normalized = [value.strip() for value in values]
        if any(not value for value in normalized) or len({value.casefold() for value in normalized}) != len(normalized):
            raise ValueError("GOAL_TOPICS_INVALID")
        return normalized


class ConfirmedGoal(InternalSchema):
    goal_id: Identifier = Field(alias="goalId")
    goal_version: Annotated[int, Field(ge=1)] = Field(alias="goalVersion")
    text: Annotated[str, Field(min_length=1, max_length=2_000)] = Field(repr=False)
    confirmed: bool
    template_id: Identifier | None = Field(default=None, alias="templateId")
    profile: GoalProfile | None = Field(default=None, alias="goalProfile", repr=False)

    @field_validator("confirmed")
    @classmethod
    def confirmation_required(cls, value: bool) -> bool:
        if value is not True:
            raise ValueError("GOAL_CONFIRMATION_REQUIRED")
        return value

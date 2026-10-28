"""10/26 wire contract helpers. Transport/authentication stay in the Backend.

Internal harness IDs, quality signals and state are supplied by the Server,
never synthesized from the public request. Similarity is not confidence.
"""
from __future__ import annotations

from datetime import datetime
from typing import Annotated, Literal, get_args
from uuid import UUID

from pydantic import Field, field_validator, model_validator

from .schemas import (ConfirmedGoal, ContentInput, Identifier, InternalSchema,
                      OCRMetadata, Observation, VerifiedContext)
from ..analysis.acquisition_policy import METADATA_SOURCES
from ..preprocessing.models import ContentSource, ExtractionStatus

ReasonCode = Literal["SEMANTIC_GOAL_MATCH", "SEMANTIC_GOAL_MISMATCH",
                     "INSUFFICIENT_CONTEXT", "AMBIGUOUS_CONTEXT", "ANALYSIS_UNAVAILABLE"]


class Metadata(InternalSchema):
    app_name: Annotated[str, Field(max_length=256)] = Field(default="", alias="appName", repr=False)
    window_title: Annotated[str, Field(max_length=800)] = Field(default="", alias="windowTitle", repr=False)
    page_title: Annotated[str, Field(max_length=800)] = Field(default="", alias="pageTitle", repr=False)
    url_host: Annotated[str, Field(max_length=253)] = Field(default="", alias="urlHost", repr=False)

    @field_validator("url_host")
    @classmethod
    def host_only(cls, value: str) -> str:
        return ContentInput.host_only(value)


class ContentPayload(InternalSchema):
    text: Annotated[str, Field(max_length=2_000)] = Field(repr=False)


class ScreenAnalysisRequest(InternalSchema):
    session_id: Identifier = Field(alias="sessionId")
    client_run_id: Identifier = Field(alias="clientRunId")
    observed_at: datetime = Field(alias="observedAt")
    sequence: Annotated[int, Field(ge=0)]
    content_source: ContentSource = Field(alias="contentSource")
    goal_snapshot: Annotated[str, Field(min_length=2, max_length=40)] | None = Field(default=None, alias="goalSnapshot", repr=False)
    metadata: Metadata | None = Field(default=None, repr=False)
    content_payload: ContentPayload | None = Field(default=None, alias="contentPayload", repr=False)
    guard_status: Literal["EXCLUDED", "PRIVACY_BLOCKED"] | None = Field(default=None, alias="guardStatus")

    @field_validator("observed_at")
    @classmethod
    def utc_time(cls, value):
        return Observation.utc_time(value)

    @field_validator("client_run_id")
    @classmethod
    def uuid_v4(cls, value: str) -> str:
        parsed = UUID(value)
        if parsed.version != 4 or str(parsed) != value:
            raise ValueError("UUID_V4_REQUIRED")
        return value

    @model_validator(mode="after")
    def allowed_payload(self):
        if self.guard_status is not None:
            if self.metadata is not None or self.content_payload is not None or self.goal_snapshot is not None:
                raise ValueError("GUARD_CONTENT_FORBIDDEN")
        elif self.goal_snapshot is None:
            raise ValueError("GOAL_SNAPSHOT_REQUIRED")
        elif self.content_source in METADATA_SOURCES and self.content_payload is not None:
            raise ValueError("METADATA_BODY_FORBIDDEN")
        return self


def adapt_screen_request(request: ScreenAnalysisRequest, goal: ConfirmedGoal,
                         context: VerifiedContext, *, extraction_status: ExtractionStatus,
                         ocr: OCRMetadata | None = None) -> ContentInput:
    """Called only with authoritative Server context and extractor status.

    OCR quality is a separate, trusted input until its wire location is agreed.
    Missing OCR quality remains missing; no placeholder confidence is invented.
    """
    if request.guard_status is not None:
        raise ValueError("GUARD_BLOCKED")
    if request.goal_snapshot != goal.text or (goal.goal_id, goal.goal_version) != (context.goal_id, context.goal_version):
        raise ValueError("GOAL_SNAPSHOT_MISMATCH")
    for name in ("session_id", "client_run_id", "observed_at", "sequence", "content_source"):
        if getattr(request, name) != getattr(context, name):
            raise ValueError("OBSERVATION_CONTEXT_MISMATCH")
    metadata = request.metadata or Metadata()
    payload = request.content_payload
    snapshot = context.model_dump(mode="json", by_alias=True)
    data = {field.alias: snapshot[field.alias] for field in Observation.model_fields.values()}
    data.update(metadata.model_dump(mode="json", by_alias=True))
    data.update(contentType="DOCUMENT", text=payload.text if payload else "",
                extractionStatus=extraction_status.value, isSensitive=False, isExcluded=False,
                ocr=ocr.model_dump(mode="json", by_alias=True) if ocr else None)
    return ContentInput.parse_safe(data)


class ScreenAnalysisResponse(InternalSchema):
    decision: Literal["RELATED", "UNRELATED", "UNCERTAIN"]
    confidence: Annotated[float, Field(ge=0, le=1, allow_inf_nan=False)] | None
    reason_code: ReasonCode = Field(alias="reasonCode")
    model_version: Identifier = Field(alias="modelVersion")
    prompt_version: Identifier = Field(alias="promptVersion")

    @model_validator(mode="after")
    def valid_decision(self):
        expected = {"RELATED": "SEMANTIC_GOAL_MATCH", "UNRELATED": "SEMANTIC_GOAL_MISMATCH"}
        if self.decision in expected:
            if self.confidence is None or self.confidence < .75 or self.reason_code != expected[self.decision]:
                raise ValueError("UNVERIFIED_DECISION")
        elif self.reason_code in expected.values():
            raise ValueError("UNCERTAIN_REASON_REQUIRED")
        return self


_CONFLICT_REASONS = frozenset({"METADATA_CONFLICTING", "CONFLICTING_METADATA", "CONFLICTING_EVIDENCE",
                              "AMBIGUOUS_CONTEXT", "MIXED_EVIDENCE"})
_FAILURE_REASONS = frozenset({"MODEL_OR_SCORING_FAILED", "MODEL_UNAVAILABLE", "ANALYSIS_FAILED",
                             "ANALYSIS_UNAVAILABLE"})
_INVALID_OBSERVATIONS = frozenset({"STALE_EVENT", "INACTIVE_TARGET", "STAGE_MISMATCH", "OWNER_CHANGED",
                                  "GOAL_VERSION_MISMATCH", "CONTEXT_UNAVAILABLE", "IDEMPOTENCY_CONFLICT"})


def contract_reason_code(label: str, internal_reason: str) -> str | None:
    """Map diagnostics without exposing original text or creating confidence."""
    if label not in {"RELATED", "UNRELATED", "UNCERTAIN"} or internal_reason in _INVALID_OBSERVATIONS:
        return None
    if label == "RELATED":
        return "SEMANTIC_GOAL_MATCH"
    if label == "UNRELATED":
        return "SEMANTIC_GOAL_MISMATCH"
    if internal_reason in _FAILURE_REASONS:
        return "ANALYSIS_UNAVAILABLE"
    if internal_reason in _CONFLICT_REASONS:
        return "AMBIGUOUS_CONTEXT"
    return "INSUFFICIENT_CONTEXT"


def public_response(label: str, internal_reason: str, *, source: ContentSource,
                    confidence: float | None, model_version: str, prompt_version: str) -> ScreenAnalysisResponse:
    """Normalize a decision using explicitly supplied, validated confidence.

    Callers must not supply cosine, Hybrid score or OCR recognition confidence.
    No calibration is inferred here. Absent confidence fails safely to UNCERTAIN.
    """
    if internal_reason in _INVALID_OBSERVATIONS:
        raise ValueError("RESULT_NOT_CURRENT")
    if label not in {"RELATED", "UNRELATED", "UNCERTAIN"}:
        raise ValueError("AI_DECISION_INVALID")
    # Validate confidence even when it will be discarded below.
    ScreenAnalysisResponse.parse_safe({"decision": "UNCERTAIN", "confidence": confidence,
        "reasonCode": "INSUFFICIENT_CONTEXT", "modelVersion": model_version, "promptVersion": prompt_version})
    if source is ContentSource.CHROME_VIEWPORT_OCR:
        label, confidence, reason = "UNCERTAIN", None, "INSUFFICIENT_CONTEXT"
    elif internal_reason in _FAILURE_REASONS:
        label, confidence, reason = "UNCERTAIN", None, "ANALYSIS_UNAVAILABLE"
    elif source in METADATA_SOURCES and label == "UNRELATED":
        label, confidence, reason = "UNCERTAIN", None, "INSUFFICIENT_CONTEXT"
    elif label != "UNCERTAIN" and confidence is None:
        label, reason = "UNCERTAIN", "ANALYSIS_UNAVAILABLE"
    elif label == "UNCERTAIN" or confidence < .75:
        label = "UNCERTAIN"
        reason = contract_reason_code(label, internal_reason)
    else:
        reason = contract_reason_code(label, internal_reason)
    return ScreenAnalysisResponse.parse_safe({"decision": label, "confidence": confidence,
        "reasonCode": reason, "modelVersion": model_version, "promptVersion": prompt_version})


def validate_provider_response(payload: object, *, source: ContentSource,
                               model_version: str, prompt_version: str) -> ScreenAnalysisResponse:
    """Discard invalid provider data; emit only the configured safe provenance."""
    try:
        if not isinstance(payload, dict) or set(payload) != {
            "decision", "confidence", "reasonCode", "modelVersion", "promptVersion"
        }:
            raise ValueError("INVALID_RESPONSE")
        label, reason = payload["decision"], payload["reasonCode"]
        if label not in ("RELATED", "UNRELATED", "UNCERTAIN") or reason not in get_args(ReasonCode):
            raise ValueError("INVALID_ENUM")
        expected = {"RELATED": "SEMANTIC_GOAL_MATCH", "UNRELATED": "SEMANTIC_GOAL_MISMATCH"}
        if (label in expected and reason != expected[label]) or (label == "UNCERTAIN" and reason in expected.values()):
            raise ValueError("INCONSISTENT_DECISION")
        checked = ScreenAnalysisResponse.parse_safe({**payload, "decision": "UNCERTAIN",
                                                     "reasonCode": "INSUFFICIENT_CONTEXT"})
        if checked.model_version != model_version or checked.prompt_version != prompt_version:
            raise ValueError("PROVENANCE_MISMATCH")
        return public_response(label, reason, source=source, confidence=checked.confidence,
                               model_version=model_version, prompt_version=prompt_version)
    except (ValueError, TypeError):
        return public_response("UNCERTAIN", "ANALYSIS_UNAVAILABLE", source=source, confidence=None,
                               model_version=model_version, prompt_version=prompt_version)

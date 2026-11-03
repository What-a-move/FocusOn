"""Backend-only reception envelope; not a change to Client request fields."""
from typing import Literal

from pydantic import Field

from .relevance_contract import ScreenAnalysisRequest, ScreenAnalysisResponse
from .schemas import ConfirmedGoal, Identifier, InternalSchema, OCRMetadata, VerifiedContext
from ..preprocessing.models import ExtractionStatus


class AnalysisReception(InternalSchema):
    request: ScreenAnalysisRequest = Field(repr=False)
    goal: ConfirmedGoal = Field(repr=False)
    context: VerifiedContext = Field(repr=False)
    extraction_status: ExtractionStatus = Field(alias="extractionStatus")
    ocr: OCRMetadata | None = Field(default=None, repr=False)


class ReceptionReceipt(InternalSchema):
    status: Literal["RECEIVED"] = "RECEIVED"
    session_id: Identifier = Field(alias="sessionId")
    client_run_id: Identifier = Field(alias="clientRunId")
    analysis_executed: Literal[False] = Field(default=False, alias="analysisExecuted")


class ExperimentalAnalysisResponse(ScreenAnalysisResponse):
    candidate_decision: Literal["RELATED", "UNRELATED", "UNCERTAIN"] | None = Field(alias="candidateDecision")
    candidate_reason_code: str | None = Field(alias="candidateReasonCode")
    candidate_calibrated: Literal[False] = Field(alias="candidateCalibrated")

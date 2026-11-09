"""Backend-only reception envelope; not a change to Client request fields."""
from pydantic import Field, model_validator

from .relevance_contract import ScreenAnalysisRequest, ScreenAnalysisResponse
from .schemas import ConfirmedGoal, Identifier, InternalSchema, OCRMetadata, VerifiedContext
from ..preprocessing.models import ExtractionStatus


class AnalysisReception(InternalSchema):
    request: ScreenAnalysisRequest = Field(repr=False)
    goal: ConfirmedGoal = Field(repr=False)
    context: VerifiedContext = Field(repr=False)
    extraction_status: ExtractionStatus = Field(alias="extractionStatus")
    ocr: OCRMetadata | None = Field(default=None, repr=False)


class AnalysisDecisionResponse(ScreenAnalysisResponse):
    @model_validator(mode="after")
    def valid_decision(self):
        # The version suffix marks a beta heuristic, not calibrated confidence.
        if not self.prompt_version.endswith("-uncalibrated"):
            return super().valid_decision()
        reasons = {"RELATED": "SEMANTIC_GOAL_MATCH", "UNRELATED": "SEMANTIC_GOAL_MISMATCH"}
        if self.decision in reasons:
            if self.confidence is None or self.confidence < .6 or self.reason_code != reasons[self.decision]:
                raise ValueError("INVALID_BETA_DECISION")
        elif self.reason_code in reasons.values():
            raise ValueError("INVALID_BETA_UNCERTAIN")
        return self

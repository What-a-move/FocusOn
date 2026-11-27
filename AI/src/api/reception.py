"""Backend-only reception envelope; not a change to Client request fields."""
from typing import Literal

from pydantic import Field, model_validator

from .relevance_contract import ScreenAnalysisRequest, ScreenAnalysisResponse
from .schemas import ConfirmedGoal, Identifier, InternalSchema, OCRMetadata, VerifiedContext
from ..preprocessing.models import ExtractionStatus


class AnalysisReception(InternalSchema):
    request: ScreenAnalysisRequest = Field(repr=False)
    goal: ConfirmedGoal | None = Field(default=None, repr=False)
    context: VerifiedContext = Field(repr=False)
    extraction_status: ExtractionStatus = Field(alias="extractionStatus")
    ocr: OCRMetadata | None = Field(default=None, repr=False)


REVIEW_CONCEPT_MAX_LENGTH = 120


class ReviewEvidence(InternalSchema):
    concept_label: str = Field(alias="conceptLabel", min_length=1, max_length=REVIEW_CONCEPT_MAX_LENGTH, repr=False)
    evidence_type: Literal["CONCEPT"] = Field(alias="evidenceType")


class AnalysisDecisionResponse(ScreenAnalysisResponse):
    matched_goal_id: Identifier | None = Field(alias="matchedGoalId")
    match_status: Literal["MATCHED", "FAR", "NOT_ENOUGH"] | None = Field(alias="matchStatus")
    review_evidence: list[ReviewEvidence] = Field(default_factory=list, alias="reviewEvidence", max_length=1, repr=False)

    @model_validator(mode="after")
    def valid_decision(self):
        if ((self.decision == "RELATED") != (self.match_status is not None)
                or (self.match_status == "MATCHED") != (self.matched_goal_id is not None)):
            raise ValueError("INVALID_GOAL_MATCH")
        if self.review_evidence and (self.decision != "RELATED" or self.match_status != "MATCHED"):
            raise ValueError("INVALID_REVIEW_EVIDENCE")
        # Backend supports uncalibrated decisions with a null confidence.
        if not self.prompt_version.endswith("-uncalibrated"):
            return super().valid_decision()
        reasons = {"RELATED": "SEMANTIC_GOAL_MATCH", "UNRELATED": "SEMANTIC_GOAL_MISMATCH"}
        if self.decision in reasons:
            if self.reason_code != reasons[self.decision] or (self.confidence is not None and self.confidence < .6):
                raise ValueError("INVALID_BETA_DECISION")
        elif self.reason_code in reasons.values():
            raise ValueError("INVALID_BETA_UNCERTAIN")
        return self

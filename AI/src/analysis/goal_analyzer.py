from dataclasses import dataclass
from src.api.schemas import ClarityStatus
from src.config import Settings


@dataclass(frozen=True, slots=True)
class GoalAssessment:
    is_usable_goal: float
    has_multiple_main_goals: float
    has_unclear_term: float
    specificity_level: float
    specificity_confidence: float


@dataclass(frozen=True, slots=True)
class GeneratedResultVerification:
    preserves_user_intent: float
    contains_one_goal: float
    is_specific_enough: float

    def failed_checks(self, pass_min: float) -> list[str]:
        checks = {
            "preserves_user_intent": self.preserves_user_intent,
            "contains_one_goal": self.contains_one_goal,
            "is_specific_enough": self.is_specific_enough,
        }
        return [name for name, probability in checks.items() if probability < pass_min]


def route_goal_assessment(
    assessment: GoalAssessment,
    settings: Settings,
) -> ClarityStatus:
    """Apply deterministic precedence; models cannot override this result."""

    if assessment.is_usable_goal <= settings.invalid_usable_max:
        return ClarityStatus.INVALID
    if assessment.has_unclear_term >= settings.unclear_term_min:
        return ClarityStatus.UNRECOGNIZED_TERM
    if assessment.has_multiple_main_goals >= settings.multiple_goals_min:
        return ClarityStatus.NEEDS_SELECTION
    if assessment.is_usable_goal < settings.usable_confident_min:
        return ClarityStatus.NEEDS_QUESTION
    if assessment.specificity_confidence < settings.specificity_confidence_min:
        return ClarityStatus.NEEDS_QUESTION
    if assessment.specificity_level >= settings.clear_specificity_min:
        return ClarityStatus.CLEAR
    if assessment.specificity_level >= settings.suggestion_specificity_min:
        return ClarityStatus.NEEDS_SUGGESTION
    return ClarityStatus.NEEDS_QUESTION

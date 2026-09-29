from __future__ import annotations

from typing import Any

from src.api.schemas import GoalClarifyRequest


def build_goal_assessment_state(request: GoalClarifyRequest) -> dict[str, Any]:
    """Build the original minimal state sent to each comparison model."""

    return {
        "goal_text": request.original_text,
        "selected_goal_text": request.selected_goal_text,
        "clarification_answers": [
            answer.model_dump(mode="json", by_alias=False)
            for answer in request.clarification_answers
        ],
    }


def build_goal_assessment_question_specs() -> dict[str, dict[str, Any]]:
    """Return model-neutral typed questions for JEV and Laya comparison."""

    return {
        "is_usable_goal": {
            "type": "noul",
            "instructions": (
                "Determine whether the effective goal in the state is a learning goal "
                "that a user can meaningfully perform or study. Use selected_goal_text "
                "when present, and use clarification_answers as added context."
            ),
            "criteria": {
                "true": "A learnable topic, implementation, practice, assignment, or problem-solving goal",
                "false": "Meaningless text or not a usable learning goal",
            },
        },
        "has_multiple_main_goals": {
            "type": "noul",
            "instructions": (
                "Determine whether the effective goal contains two or more independent "
                "primary learning goals that should not be completed as one session goal."
            ),
            "criteria": {
                "true": "Two or more independent primary goals",
                "false": "One primary goal, even if it includes subordinate steps",
            },
        },
        "has_unclear_term": {
            "type": "noul",
            "instructions": (
                "Determine whether an abbreviation, typo, slang expression, or ambiguous "
                "term prevents confident interpretation of the effective learning goal."
            ),
            "criteria": {
                "true": "A term must be confirmed with the user before interpretation",
                "false": "The meaning can be interpreted confidently without guessing",
            },
        },
        "specificity_level": {
            "type": "score",
            "instructions": (
                "Rate how specific and actionable the effective learning goal is, using "
                "clarification_answers as context."
            ),
            "criteria": [
                "Not interpretable or unusable as a learning goal",
                "Missing essential topic or activity information",
                "Understandable but broad and should be narrowed",
                "A clear, single, actionable learning goal",
            ],
        },
    }

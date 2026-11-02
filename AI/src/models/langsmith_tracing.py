from __future__ import annotations

import os
from typing import Any

from src.analysis.goal_analyzer import GoalAssessment, route_goal_assessment
from src.api.schemas import GoalClarifyRequest
from src.config import Settings


RAW_GOAL_TEXT_ENV = "LANGSMITH_TRACE_RAW_GOAL_TEXT"
_TRUE_VALUES = {"1", "true", "yes", "on"}


def summarize_goal_request(request: GoalClarifyRequest) -> dict[str, Any]:
    """Build the LangSmith input view for a goal-assistance request."""

    summary: dict[str, Any] = {
        "requestId": request.request_id,
        "originalTextLength": len(request.original_text),
        "hasSelectedGoalText": request.selected_goal_text is not None,
        "selectedGoalTextLength": (
            len(request.selected_goal_text) if request.selected_goal_text else 0
        ),
        "clarificationAnswersCount": len(request.clarification_answers),
    }
    if os.getenv(RAW_GOAL_TEXT_ENV, "").strip().casefold() in _TRUE_VALUES:
        summary["originalText"] = request.original_text
        summary["selectedGoalText"] = request.selected_goal_text
        summary["clarificationAnswers"] = [
            answer.model_dump(mode="json", by_alias=True)
            for answer in request.clarification_answers
        ]
        summary["traceInputMode"] = "raw_goal_text"
    else:
        summary["traceInputMode"] = "redacted"
    return summary


def trace_goal_assessment_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    request = inputs.get("request")
    if not isinstance(request, GoalClarifyRequest):
        return {"input": "unavailable"}
    return {"request": summarize_goal_request(request)}


def trace_goal_assessment_outputs(output: GoalAssessment) -> dict[str, Any]:
    return {
        "isUsableGoal": output.is_usable_goal,
        "hasMultipleMainGoals": output.has_multiple_main_goals,
        "hasUnclearTerm": output.has_unclear_term,
        "specificityLevel": output.specificity_level,
        "specificityConfidence": output.specificity_confidence,
        "routerStatus": route_goal_assessment(output, Settings()).value,
    }


def trace_comparative_assessment_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    request = inputs.get("request")
    settings = inputs.get("settings")
    result: dict[str, Any] = {
        "layaComparisonEnabled": isinstance(settings, Settings)
        and settings.laya_comparison_enabled,
    }
    if isinstance(request, GoalClarifyRequest):
        result["request"] = summarize_goal_request(request)
    return result

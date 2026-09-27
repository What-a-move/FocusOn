from __future__ import annotations

import asyncio
from typing import Any, Literal, TypedDict

from langgraph.graph import END, START, StateGraph

from src.analysis.goal_analyzer import GoalAssessment, route_goal_assessment
from src.api.schemas import (
    ClarityStatus,
    ClarificationQuestion,
    GoalClarifyData,
    GoalClarifyRequest,
    GoalProfileDraft,
    RecommendedGoalBatch,
)
from src.config import Settings
from src.models.errors import InputValidationError
from src.models.jev_client import GoalJudgmentClient
from src.models.llm_client import GoalGenerationClient


class GoalAssistanceState(TypedDict, total=False):
    request: GoalClarifyRequest
    assessment: GoalAssessment
    clarity_status: ClarityStatus
    candidates: RecommendedGoalBatch
    profile: GoalProfileDraft
    question: ClarificationQuestion
    failed_checks: list[str]
    candidate_failures: dict[int, list[str]]
    repair_count: int
    response: GoalClarifyData


class GoalAssistanceWorkflow:
    """One-request graph. No checkpoint or conversation text is persisted."""

    def __init__(
        self,
        settings: Settings,
        judgments: GoalJudgmentClient,
        generator: GoalGenerationClient,
    ) -> None:
        self._settings = settings
        self._judgments = judgments
        self._generator = generator
        self._graph = self._build_graph()

    def _build_graph(self):
        graph = StateGraph(GoalAssistanceState)
        graph.add_node("validate_input", self._validate_input)
        graph.add_node("assess_with_jev", self._assess_with_jev)
        graph.add_node("generate_profile", self._generate_profile)
        graph.add_node("generate_candidates", self._generate_candidates)
        graph.add_node("generate_question", self._generate_question)
        graph.add_node("verify_generated", self._verify_generated)
        graph.add_node("repair_generated", self._repair_generated)
        graph.add_node("generate_fallback_question", self._generate_fallback_question)
        graph.add_node("build_generated_response", self._build_generated_response)
        graph.add_node("build_question_response", self._build_question_response)
        graph.add_node("build_invalid_response", self._build_invalid_response)

        graph.add_edge(START, "validate_input")
        graph.add_edge("validate_input", "assess_with_jev")
        graph.add_conditional_edges(
            "assess_with_jev",
            self._route_clarity,
            {
                "profile": "generate_profile",
                "candidates": "generate_candidates",
                "question": "generate_question",
                "invalid": "build_invalid_response",
            },
        )
        graph.add_edge("generate_profile", "verify_generated")
        graph.add_edge("generate_candidates", "verify_generated")
        graph.add_edge("generate_question", "build_question_response")
        graph.add_conditional_edges(
            "verify_generated",
            self._route_verification,
            {
                "pass": "build_generated_response",
                "repair": "repair_generated",
                "fallback": "generate_fallback_question",
            },
        )
        graph.add_edge("repair_generated", "verify_generated")
        graph.add_edge("generate_fallback_question", "build_question_response")
        graph.add_edge("build_generated_response", END)
        graph.add_edge("build_question_response", END)
        graph.add_edge("build_invalid_response", END)
        return graph.compile()

    async def run(self, request: GoalClarifyRequest) -> GoalClarifyData:
        state = await self._graph.ainvoke({"request": request, "repair_count": 0})
        return state["response"]

    async def _validate_input(self, state: GoalAssistanceState) -> dict[str, Any]:
        request = state["request"]
        details: list[dict[str, object]] = []
        if len(request.original_text) > self._settings.goal_text_max_length:
            details.append({"field": "originalText", "reason": "length_exceeded"})
        if (
            request.selected_goal_text is not None
            and len(request.selected_goal_text) > self._settings.goal_text_max_length
        ):
            details.append({"field": "selectedGoalText", "reason": "length_exceeded"})
        if (
            len(request.clarification_answers)
            > self._settings.clarification_answers_max_count
        ):
            details.append(
                {"field": "clarificationAnswers", "reason": "count_exceeded"}
            )
        for index, answer in enumerate(request.clarification_answers):
            if len(answer.answer) > self._settings.clarification_answer_max_length:
                details.append(
                    {
                        "field": f"clarificationAnswers.{index}.answer",
                        "reason": "length_exceeded",
                    }
                )
        if details:
            raise InputValidationError(details)
        return {}

    async def _assess_with_jev(self, state: GoalAssistanceState) -> dict[str, Any]:
        assessment = await self._judgments.assess_goal(state["request"])
        return {
            "assessment": assessment,
            "clarity_status": route_goal_assessment(assessment, self._settings),
        }

    def _route_clarity(
        self, state: GoalAssistanceState
    ) -> Literal["profile", "candidates", "question", "invalid"]:
        status = state["clarity_status"]
        if status is ClarityStatus.CLEAR:
            return "profile"
        if status in {
            ClarityStatus.NEEDS_SELECTION,
            ClarityStatus.NEEDS_SUGGESTION,
        }:
            return "candidates"
        if status is ClarityStatus.INVALID:
            return "invalid"
        return "question"

    async def _generate_profile(self, state: GoalAssistanceState) -> dict[str, Any]:
        return {"profile": await self._generator.generate_profile(state["request"])}

    async def _generate_candidates(self, state: GoalAssistanceState) -> dict[str, Any]:
        return {
            "candidates": await self._generator.generate_candidates(
                state["request"], state["clarity_status"]
            )
        }

    async def _generate_question(self, state: GoalAssistanceState) -> dict[str, Any]:
        return {
            "question": await self._generator.generate_question(
                state["request"], state["clarity_status"]
            )
        }

    async def _verify_generated(self, state: GoalAssistanceState) -> dict[str, Any]:
        request = state["request"]
        if "profile" in state:
            verification = await self._judgments.verify_generated_result(
                request,
                state["profile"].model_dump(mode="json", by_alias=True),
                "goal_profile",
            )
            failed = verification.failed_checks(
                self._settings.generated_result_pass_min
            )
            return {"failed_checks": failed, "candidate_failures": {}}

        candidates = state["candidates"].goals
        verifications = await asyncio.gather(
            *(
                self._judgments.verify_generated_result(
                    request,
                    candidate.model_dump(mode="json", by_alias=True),
                    "recommended_goal",
                )
                for candidate in candidates
            )
        )
        candidate_failures = {
            index: failed
            for index, verification in enumerate(verifications)
            if (
                failed := verification.failed_checks(
                    self._settings.generated_result_pass_min
                )
            )
        }
        failed = sorted(
            {
                check
                for checks in candidate_failures.values()
                for check in checks
            }
        )
        return {"failed_checks": failed, "candidate_failures": candidate_failures}

    def _route_verification(
        self, state: GoalAssistanceState
    ) -> Literal["pass", "repair", "fallback"]:
        if not state.get("failed_checks"):
            return "pass"
        if state.get("repair_count", 0) < 1:
            return "repair"
        return "fallback"

    async def _repair_generated(self, state: GoalAssistanceState) -> dict[str, Any]:
        request = state["request"]
        failed = state["failed_checks"]
        result: dict[str, Any] = {
            "repair_count": state.get("repair_count", 0) + 1,
            "failed_checks": [],
            "candidate_failures": {},
        }
        if "profile" in state:
            result["profile"] = await self._generator.repair_profile(
                request, state["profile"], failed
            )
        else:
            candidates = list(state["candidates"].goals)
            failures = state["candidate_failures"]
            repaired = await asyncio.gather(
                *(
                    self._generator.repair_candidate(
                        request, candidates[index], checks
                    )
                    for index, checks in failures.items()
                )
            )
            for index, candidate in zip(failures, repaired, strict=True):
                candidates[index] = candidate
            result["candidates"] = RecommendedGoalBatch(goals=candidates)
        return result

    async def _generate_fallback_question(
        self, state: GoalAssistanceState
    ) -> dict[str, Any]:
        return {
            "clarity_status": ClarityStatus.NEEDS_QUESTION,
            "question": await self._generator.generate_question(
                state["request"], ClarityStatus.NEEDS_QUESTION
            ),
        }

    async def _build_generated_response(
        self, state: GoalAssistanceState
    ) -> dict[str, Any]:
        profile = state.get("profile")
        candidates = state.get("candidates")
        return {
            "response": GoalClarifyData(
                clarity_status=state["clarity_status"],
                interpreted_goal=profile.interpreted_goal if profile else None,
                recommended_goals=candidates.goals if candidates else [],
                goal_profile_draft=profile,
                requires_user_confirmation=True,
                fallback_allowed=True,
            )
        }

    async def _build_question_response(
        self, state: GoalAssistanceState
    ) -> dict[str, Any]:
        return {
            "response": GoalClarifyData(
                clarity_status=state["clarity_status"],
                question=state["question"],
                requires_user_confirmation=True,
                fallback_allowed=True,
            )
        }

    async def _build_invalid_response(
        self, state: GoalAssistanceState
    ) -> dict[str, Any]:
        return {
            "response": GoalClarifyData(
                clarity_status=ClarityStatus.INVALID,
                invalid_reason="학습 목표로 해석하기 어려워 내용을 수정해야 합니다.",
                requires_user_confirmation=True,
                fallback_allowed=False,
            )
        }

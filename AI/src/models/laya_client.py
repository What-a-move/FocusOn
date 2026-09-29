from __future__ import annotations

import os
from functools import lru_cache
from typing import Any

from src.analysis.goal_analyzer import GoalAssessment
from src.analysis.goal_assessment_contract import (
    build_goal_assessment_question_specs,
    build_goal_assessment_state,
)
from src.api.schemas import GoalClarifyRequest
from src.config import Settings
from src.models.errors import InvalidModelResponseError, ModelUnavailableError


class LayaComparisonClient:
    """Local, read-only comparison client; it never affects the JEV workflow."""

    def __init__(self, settings: Settings) -> None:
        self._model = settings.laya_comparison_model

    @staticmethod
    @lru_cache(maxsize=1)
    def _load_agent(model: str) -> Any:
        # Laya documents this guard for environments where TensorFlow is installed.
        os.environ.setdefault("USE_TF", "0")
        try:
            import laya
        except ModuleNotFoundError as exc:
            raise ModelUnavailableError(
                "Laya 비교 패키지가 설치되지 않았습니다. `python -m pip install -r requirements.txt`를 실행해 주세요."
            ) from exc
        try:
            return laya.load(model)
        except Exception as exc:  # First use can download the public checkpoint.
            raise ModelUnavailableError(
                "Laya 비교 모델을 불러오지 못했습니다. 네트워크·디스크 공간·모델 다운로드 상태를 확인해 주세요."
            ) from exc

    def assess_goal(self, request: GoalClarifyRequest) -> GoalAssessment:
        """Run the same state and four typed questions used by JEV, locally."""

        try:
            response = self._load_agent(self._model).predict(
                build_goal_assessment_state(request),
                build_goal_assessment_question_specs(),
            )
            answers = response["answers"]
            specificity = answers["specificity_level"]
            return GoalAssessment(
                is_usable_goal=float(answers["is_usable_goal"]["noul"]),
                has_multiple_main_goals=float(
                    answers["has_multiple_main_goals"]["noul"]
                ),
                has_unclear_term=float(answers["has_unclear_term"]["noul"]),
                specificity_level=float(specificity["score"]),
                specificity_confidence=float(
                    specificity.get(
                        "confidence", specificity.get("answer_confidence", 0.0)
                    )
                ),
            )
        except ModelUnavailableError:
            raise
        except (KeyError, TypeError, ValueError, AttributeError) as exc:
            raise InvalidModelResponseError(
                "Laya 비교 응답 형식이 올바르지 않습니다."
            ) from exc
        except Exception as exc:
            raise ModelUnavailableError(
                "Laya 비교 판단을 실행할 수 없습니다."
            ) from exc

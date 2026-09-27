from pathlib import Path

import pytest
from streamlit.testing.v1 import AppTest

from streamlit_app import _build_saved_goal


def test_streamlit_test_ui_renders_without_external_calls():
    app_path = Path(__file__).resolve().parents[1] / "streamlit_app.py"

    app = AppTest.from_file(app_path).run(timeout=10)

    assert not app.exception
    assert app.title[0].value == "🎯 FocusOn 목표 설정 AI 테스트"
    assert app.text_area[0].label == "학습 목표"
    assert app.sidebar.button[0].label == "전체 초기화"
    assert app.button[0].label == "목표 저장"


def test_only_clear_confirmed_result_can_be_saved_in_test_session():
    result = {
        "clarityStatus": "CLEAR",
        "interpretedGoal": "React Query 캐싱 원리 학습",
        "goalProfileDraft": {
            "originalText": "React Query 캐싱 원리 학습",
            "interpretedGoal": "React Query 캐싱 원리 학습",
            "mainTopic": "React Query 캐싱",
            "purpose": "CONCEPT_LEARNING",
            "coreTopics": ["staleTime"],
            "supportingTopics": [],
            "expectedActivities": ["공식 문서 읽기"],
            "profileSource": "AI_ASSISTED",
        },
    }

    saved = _build_saved_goal(result)

    assert saved["goalText"] == "React Query 캐싱 원리 학습"
    assert saved["goalProfile"]["mainTopic"] == "React Query 캐싱"
    with pytest.raises(ValueError, match="명확한 목표"):
        _build_saved_goal(
            {
                "clarityStatus": "NEEDS_QUESTION",
                "interpretedGoal": None,
                "goalProfileDraft": None,
            }
        )

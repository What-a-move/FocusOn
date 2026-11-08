from pathlib import Path

import pytest
from streamlit.testing.v1 import AppTest

import streamlit_app
from src.analysis.goal_analyzer import GoalAssessment
from src.api.schemas import ClarityStatus, GoalClarifyRequest
from src.config import Settings
from src.models.errors import ModelUnavailableError
from streamlit_app import (
    _apply_answer_to_goal,
    _build_saved_goal,
    _jev_assessment_rows,
)


def test_jev_assessment_rows_include_readable_labels_and_explanations():
    rows = _jev_assessment_rows(
        {
            "is_usable_goal": 0.87,
            "has_multiple_main_goals": 0.07,
            "has_unclear_term": 0.18,
            "specificity_level": 1.89,
            "specificity_confidence": 0.7,
        }
    )

    assert rows[0] == {
        "판단 항목": "학습 목표 성립 가능성",
        "결과": "87%",
        "설명": "입력한 문장이 실제로 학습할 수 있는 목표일 가능성입니다.",
    }
    assert rows[3]["결과"] == "1.89 / 3"


def test_clarification_answer_is_added_to_the_editable_goal_text_once():
    goal = "React Query 캐싱 원리 학습"

    updated = _apply_answer_to_goal(goal, "직접 구현")
    repeated = _apply_answer_to_goal(updated, "직접 구현")

    assert updated == "React Query 캐싱 원리 학습 — 직접 구현"
    assert repeated == updated


@pytest.mark.asyncio
async def test_laya_failure_does_not_prevent_the_jev_assessment(monkeypatch):
    jev_assessment = GoalAssessment(0.9, 0.1, 0.1, 3.0, 0.9)

    async def successful_jev(_settings, _request):
        return jev_assessment, ClarityStatus.CLEAR

    async def unavailable_laya(_settings, _request):
        raise ModelUnavailableError("Laya 비교 모델을 불러오지 못했습니다.")

    monkeypatch.setattr(streamlit_app, "_run_assessment", successful_jev)
    monkeypatch.setattr(streamlit_app, "_run_laya_comparison", unavailable_laya)

    jev_result, laya_result = await streamlit_app._run_comparative_assessment(
        Settings(), GoalClarifyRequest(request_id="req", original_text="JWT 인증 구현")
    )

    assert jev_result == (jev_assessment, ClarityStatus.CLEAR)
    assert isinstance(laya_result, ModelUnavailableError)


def test_streamlit_test_ui_renders_without_external_calls():
    app_path = Path(__file__).resolve().parents[1] / "streamlit_app.py"

    app = AppTest.from_file(app_path).run(timeout=10)

    assert not app.exception
    assert app.title[0].value == "🎯 FocusOn 목표 설정 AI 테스트"
    assert app.text_area[0].label == "학습 목표"
    assert app.sidebar.button[0].label == "전체 초기화"
    assert app.button[0].label == "목표 저장"
    assert any("Claude" in item.value for item in app.sidebar.markdown)
    assert any("JEV·Claude" in item.value for item in app.sidebar.warning)


def test_pending_answer_updates_goal_input_before_widget_render():
    app_path = Path(__file__).resolve().parents[1] / "streamlit_app.py"
    app = AppTest.from_file(app_path).run(timeout=10)
    app.session_state["flow_original_text"] = "React Query 캐싱 원리 학습"
    app.session_state["pending_clarification_answer"] = {
        "questionId": "approach",
        "question": "어떤 방식으로 학습할까요?",
        "answer": "직접 구현",
    }

    app.run(timeout=10)

    assert not app.exception
    assert app.text_area[0].value == "React Query 캐싱 원리 학습 — 직접 구현"
    assert app.session_state["clarification_answers"] == [
        {
            "questionId": "approach",
            "question": "어떤 방식으로 학습할까요?",
            "answer": "직접 구현",
        }
    ]


def test_clear_result_keeps_the_jev_assessment_table_visible():
    app_path = Path(__file__).resolve().parents[1] / "streamlit_app.py"
    app = AppTest.from_file(app_path).run(timeout=10)
    app.session_state["jev_assessment"] = {
        "is_usable_goal": 0.93,
        "has_multiple_main_goals": 0.04,
        "has_unclear_term": 0.03,
        "specificity_level": 1.89,
        "specificity_confidence": 0.70,
    }
    app.session_state["jev_clarity_status"] = "CLEAR"
    app.session_state["laya_assessment"] = {
        "is_usable_goal": 0.81,
        "has_multiple_main_goals": 0.05,
        "has_unclear_term": 0.08,
        "specificity_level": 2.0,
        "specificity_confidence": 0.62,
    }
    app.session_state["analysis_result"] = {
        "clarityStatus": "CLEAR",
        "invalidReason": None,
        "recommendedGoals": [],
        "question": None,
        "goalProfileDraft": None,
        "requiresUserConfirmation": True,
    }

    app.run(timeout=10)

    assert app.subheader[0].value == "JEV 판단 결과"
    assert len(app.dataframe) == 1
    assert any("실제 상태·질문·저장 흐름은 JEV만 사용" in item.value for item in app.caption)
    assert any("JEV 확률과 직접 비교" in item.value for item in app.warning)


def test_broad_goal_offers_original_text_start_before_recommendation_selection():
    app_path = Path(__file__).resolve().parents[1] / "streamlit_app.py"
    app = AppTest.from_file(app_path).run(timeout=10)
    app.session_state["flow_original_text"] = "상태 관리 공부하기"
    app.session_state["jev_assessment"] = {
        "is_usable_goal": 0.92,
        "has_multiple_main_goals": 0.03,
        "has_unclear_term": 0.04,
        "specificity_level": 1.8,
        "specificity_confidence": 0.78,
    }
    app.session_state["jev_clarity_status"] = "NEEDS_SUGGESTION"
    app.session_state["analysis_result"] = {
        "clarityStatus": "NEEDS_SUGGESTION",
        "interpretedGoal": None,
        "invalidReason": None,
        "recommendedGoals": [
            {"id": "candidate-1", "title": "상태 관리 방식 비교", "reason": "범위를 정함"}
        ],
        "question": None,
        "goalProfileDraft": None,
        "requiresUserConfirmation": True,
    }

    app.run(timeout=10)

    assert "입력한 목표로 바로 시작" in [button.label for button in app.button]
    assert "이 목표 선택" in [button.label for button in app.button]


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
    with_dialog = _build_saved_goal(result | {"clarificationSessionId": "demo-dialog"})
    assert with_dialog["clarificationSessionId"] == "demo-dialog"
    with pytest.raises(ValueError, match="명확한 목표"):
        _build_saved_goal(
            {
                "clarityStatus": "NEEDS_QUESTION",
                "interpretedGoal": None,
                "goalProfileDraft": None,
            }
        )


def test_demo_request_keeps_dialog_and_uses_agreed_json_names(monkeypatch):
    from types import SimpleNamespace
    state = SimpleNamespace(
        flow_original_text="캐시 학습", selected_goal_text=None,
        clarification_session_id=None,
        clarification_answers=[{"question": "어떤 범위인가요?", "answer": "캐시 갱신"}],
    )
    monkeypatch.setattr(streamlit_app.st, "session_state", state)
    first = streamlit_app._current_request("demo-first")
    second = streamlit_app._current_request("demo-second")
    assert first.clarification_session_id == second.clarification_session_id
    assert first.clarification_session_id is not None
    serialized = second.model_dump(by_alias=True)
    assert serialized["conversationTurn"] == 1
    assert serialized["goalText"] == "캐시 학습"
    assert serialized["previousAnswers"] == [{"question": "어떤 범위인가요?", "answer": "캐시 갱신"}]
    streamlit_app._reset_flow()
    assert state.clarification_session_id is None

from __future__ import annotations

import asyncio
from typing import Any
from uuid import uuid4

import streamlit as st

from src.api.schemas import ClarityStatus, GoalClarifyData, GoalClarifyRequest
from src.config import Settings, get_settings
from src.models.errors import (
    InputValidationError,
    InvalidModelResponseError,
    ModelConfigurationError,
    ModelUnavailableError,
)
from src.models.jev_client import TypeSafeJevClient
from src.models.llm_client import OpenAIGoalGenerationClient
from src.workflow.goal_assistance import GoalAssistanceWorkflow


STATUS_LABELS = {
    ClarityStatus.CLEAR: "명확한 목표",
    ClarityStatus.NEEDS_SELECTION: "목표 하나를 선택해 주세요",
    ClarityStatus.NEEDS_SUGGESTION: "목표를 조금 더 구체화해 주세요",
    ClarityStatus.NEEDS_QUESTION: "추가 정보가 필요해요",
    ClarityStatus.UNRECOGNIZED_TERM: "표현의 의미를 확인해 주세요",
    ClarityStatus.INVALID: "학습 목표로 사용하기 어려워요",
}


def _initialize_state() -> None:
    defaults: dict[str, Any] = {
        "goal_input": "",
        "flow_original_text": None,
        "selected_goal_text": None,
        "clarification_answers": [],
        "analysis_result": None,
        "analysis_error": None,
        "last_request_id": None,
    }
    for key, value in defaults.items():
        if key not in st.session_state:
            st.session_state[key] = value


def _reset_flow(*, clear_input: bool = False) -> None:
    if clear_input:
        st.session_state.goal_input = ""
    st.session_state.flow_original_text = None
    st.session_state.selected_goal_text = None
    st.session_state.clarification_answers = []
    st.session_state.analysis_result = None
    st.session_state.analysis_error = None
    st.session_state.last_request_id = None


async def _run_workflow(
    settings: Settings,
    request: GoalClarifyRequest,
) -> GoalClarifyData:
    judgments = TypeSafeJevClient(settings)
    generator = OpenAIGoalGenerationClient(settings)
    workflow = GoalAssistanceWorkflow(settings, judgments, generator)
    try:
        return await workflow.run(request)
    finally:
        await judgments.aclose()


def _analyze(*, selected_goal_text: str | None = None) -> None:
    original_text = st.session_state.flow_original_text
    if not original_text:
        st.session_state.analysis_error = "먼저 학습 목표를 입력해 주세요."
        return

    if selected_goal_text is not None:
        st.session_state.selected_goal_text = selected_goal_text

    request_id = f"streamlit-{uuid4()}"
    request = GoalClarifyRequest(
        request_id=request_id,
        original_text=original_text,
        selected_goal_text=st.session_state.selected_goal_text,
        clarification_answers=st.session_state.clarification_answers,
    )
    st.session_state.last_request_id = request_id
    st.session_state.analysis_error = None
    try:
        with st.spinner("JEV가 목표를 판단하고 필요한 문구를 생성하고 있어요..."):
            result = asyncio.run(_run_workflow(get_settings(), request))
        st.session_state.analysis_result = result.model_dump(
            mode="json", by_alias=True
        )
    except InputValidationError:
        st.session_state.analysis_error = "입력 길이 또는 답변 개수를 확인해 주세요."
    except ModelConfigurationError:
        st.session_state.analysis_error = (
            "API Key 또는 모델 설정을 확인해 주세요. AI/.env를 수정했다면 화면을 새로고침해 주세요."
            " API Key를 변경했다면 Streamlit 서버를 재시작해야 합니다."
        )
    except ModelUnavailableError:
        st.session_state.analysis_error = (
            "외부 AI 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
        )
    except InvalidModelResponseError:
        st.session_state.analysis_error = (
            "모델 응답이 Schema 검증을 통과하지 못했습니다. 다시 분석해 주세요."
        )
    except Exception:
        st.session_state.analysis_error = (
            "예상하지 못한 오류가 발생했습니다. 터미널 상태를 확인해 주세요."
        )


def _submit_new_goal() -> None:
    goal_text = st.session_state.goal_input.strip()
    if not goal_text:
        st.session_state.analysis_error = "공백이 아닌 학습 목표를 입력해 주세요."
        return
    _reset_flow()
    st.session_state.flow_original_text = goal_text
    _analyze()


def _submit_answer(question_id: str, question: str, answer: str) -> None:
    st.session_state.clarification_answers.append(
        {"questionId": question_id, "question": question, "answer": answer}
    )
    _analyze()


def _render_configuration(settings: Settings) -> None:
    st.sidebar.header("테스트 설정")
    st.sidebar.write(
        "TypeSafe JEV",
        "✅ 설정됨" if settings.typesafe_api_key else "❌ API Key 없음",
    )
    st.sidebar.write(
        "OpenAI",
        "✅ 설정됨" if settings.openai_api_key else "❌ API Key 없음",
    )
    st.sidebar.caption(f"JEV 모델: {settings.typesafe_default_model}")
    st.sidebar.caption(f"OpenAI 모델: {settings.openai_model}")
    st.sidebar.warning(
        "입력한 목표와 답변은 외부 JEV·OpenAI 서비스로 전송됩니다. 테스트용 문장만 사용하세요."
    )
    if st.sidebar.button("전체 초기화", use_container_width=True):
        _reset_flow(clear_input=True)
        st.rerun()


def _render_profile(profile: dict[str, Any]) -> None:
    st.subheader("GoalProfile 초안")
    st.write(f"**해석된 목표:** {profile['interpretedGoal']}")
    st.write(f"**핵심 주제:** {profile['mainTopic']}")
    st.write(f"**목적:** `{profile['purpose']}`")
    if profile["coreTopics"]:
        st.write("**핵심 범위:**", ", ".join(profile["coreTopics"]))
    if profile["supportingTopics"]:
        st.write("**보조 범위:**", ", ".join(profile["supportingTopics"]))
    if profile["expectedActivities"]:
        st.write("**예상 학습 활동:**")
        for activity in profile["expectedActivities"]:
            st.markdown(f"- {activity}")


def _render_candidates(result: dict[str, Any]) -> None:
    st.subheader("추천 목표")
    for goal in result["recommendedGoals"]:
        with st.container(border=True):
            st.write(f"**{goal['title']}**")
            st.caption(goal["reason"])
            if st.button(
                "이 목표로 다시 분석",
                key=f"candidate-{goal['id']}",
                use_container_width=True,
            ):
                _analyze(selected_goal_text=goal["title"])
                st.rerun()


def _render_question(result: dict[str, Any]) -> None:
    question = result["question"]
    st.subheader("확인 질문")
    st.write(question["text"])
    columns = st.columns(len(question["options"]))
    for column, option in zip(columns, question["options"], strict=True):
        if column.button(
            option["label"],
            key=f"option-{question['id']}-{option['id']}",
            use_container_width=True,
        ):
            _submit_answer(question["id"], question["text"], option["label"])
            st.rerun()

    if question["allowCustomAnswer"]:
        with st.form(f"custom-answer-{question['id']}", clear_on_submit=True):
            custom_answer = st.text_input(
                "직접 답변",
                placeholder="선택지에 없다면 직접 입력하세요.",
            )
            submitted = st.form_submit_button(
                "답변하고 다시 분석", use_container_width=True
            )
        if submitted:
            if custom_answer.strip():
                _submit_answer(
                    question["id"], question["text"], custom_answer.strip()
                )
                st.rerun()
            else:
                st.warning("답변을 입력해 주세요.")


def _render_result() -> None:
    if st.session_state.analysis_error:
        st.error(st.session_state.analysis_error)
        if st.button("다시 분석", use_container_width=True):
            _analyze()
            st.rerun()
        return

    result = st.session_state.analysis_result
    if result is None:
        return

    status = ClarityStatus(result["clarityStatus"])
    if status is ClarityStatus.CLEAR:
        st.success(STATUS_LABELS[status])
    elif status is ClarityStatus.INVALID:
        st.error(STATUS_LABELS[status])
    else:
        st.info(STATUS_LABELS[status])

    if result["invalidReason"]:
        st.write(result["invalidReason"])
    if result["recommendedGoals"]:
        _render_candidates(result)
    if result["question"]:
        _render_question(result)
    if result["goalProfileDraft"]:
        _render_profile(result["goalProfileDraft"])

    st.divider()
    st.caption(
        "이 화면은 테스트 도구이며 목표를 저장하거나 학습 세션을 시작하지 않습니다. "
        f"사용자 확인 필요: {result['requiresUserConfirmation']}"
    )
    with st.expander("응답 JSON 확인"):
        st.json(result)
    with st.expander("현재 임시 답변 확인"):
        st.json(
            {
                "selectedGoalText": st.session_state.selected_goal_text,
                "clarificationAnswers": st.session_state.clarification_answers,
            }
        )


def main() -> None:
    st.set_page_config(
        page_title="FocusOn 목표 설정 AI 테스트",
        page_icon="🎯",
        layout="centered",
    )
    _initialize_state()
    settings = get_settings()
    _render_configuration(settings)

    st.title("🎯 FocusOn 목표 설정 AI 테스트")
    st.caption("JEV는 판단하고 OpenAI는 질문·추천·GoalProfile 문구를 생성합니다.")

    with st.form("goal-input-form"):
        st.text_area(
            "학습 목표",
            key="goal_input",
            max_chars=settings.goal_text_max_length,
            placeholder="예: React Query 캐싱 원리 학습",
            height=120,
        )
        submitted = st.form_submit_button(
            "목표 분석하기", type="primary", use_container_width=True
        )
    if submitted:
        _submit_new_goal()

    _render_result()


if __name__ == "__main__":
    main()

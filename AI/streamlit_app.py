from __future__ import annotations

import asyncio
from dataclasses import asdict
from typing import Any
from uuid import uuid4

import streamlit as st

from src.analysis.goal_analyzer import GoalAssessment
from src.api.schemas import ClarityStatus, GoalClarifyData, GoalClarifyRequest
from src.config import Settings, get_settings
from src.models.errors import (
    InputValidationError,
    InvalidModelResponseError,
    ModelConfigurationError,
    ModelUnavailableError,
)
from src.models.jev_client import TypeSafeJevClient
from src.models.laya_client import LayaComparisonClient
from src.models.llm_client import OpenAIGoalGenerationClient
from src.workflow.goal_assistance import GoalAssistanceWorkflow


STATUS_LABELS = {
    ClarityStatus.CLEAR: "명확한 목표",
    ClarityStatus.NEEDS_SELECTION: "목표 하나를 선택해 주세요",
    ClarityStatus.NEEDS_SUGGESTION: "원문으로 시작하거나 추천 목표를 선택해 주세요",
    ClarityStatus.NEEDS_QUESTION: "추가 정보가 필요해요",
    ClarityStatus.UNRECOGNIZED_TERM: "표현의 의미를 확인해 주세요",
    ClarityStatus.INVALID: "학습 목표로 사용하기 어려워요",
}

def _jev_assessment_rows(assessment: dict[str, Any]) -> list[dict[str, str]]:
    """Format typed JEV outputs as a readable summary for the Streamlit UI."""

    return [
        {
            "판단 항목": "학습 목표 성립 가능성",
            "결과": f"{assessment['is_usable_goal']:.0%}",
            "설명": "입력한 문장이 실제로 학습할 수 있는 목표일 가능성입니다.",
        },
        {
            "판단 항목": "복수 목표 포함",
            "결과": f"{assessment['has_multiple_main_goals']:.0%}",
            "설명": "서로 독립된 학습 목표가 두 개 이상 섞였을 가능성입니다.",
        },
        {
            "판단 항목": "불명확한 표현",
            "결과": f"{assessment['has_unclear_term']:.0%}",
            "설명": "약어·오타·은어처럼 사용자 확인이 필요한 표현이 있을 가능성입니다.",
        },
        {
            "판단 항목": "구체성",
            "결과": f"{assessment['specificity_level']:.2f} / 3",
            "설명": "0은 분석 불가, 1은 질문 필요, 2는 범위가 넓어 추천이 필요, 3은 명확한 단일 목표입니다.",
        },
        {
            "판단 항목": "구체성 신뢰도",
            "결과": f"{assessment['specificity_confidence']:.0%}",
            "설명": "JEV가 위 구체성 점수를 얼마나 확신하는지 나타냅니다.",
        },
    ]


def _initialize_state() -> None:
    defaults: dict[str, Any] = {
        "goal_input": "",
        "flow_original_text": None,
        "selected_goal_text": None,
        "clarification_answers": [],
        "analysis_result": None,
        "analysis_error": None,
        "analysis_stage": "idle",
        "jev_assessment": None,
        "jev_clarity_status": None,
        "laya_assessment": None,
        "laya_comparison_error": None,
        "last_request_id": None,
        "saved_goal": None,
    }
    for key, value in defaults.items():
        if key not in st.session_state:
            st.session_state[key] = value


def _reset_flow(*, clear_input: bool = False, clear_saved: bool = False) -> None:
    if clear_input:
        st.session_state.goal_input = ""
    st.session_state.flow_original_text = None
    st.session_state.selected_goal_text = None
    st.session_state.clarification_answers = []
    st.session_state.analysis_result = None
    st.session_state.analysis_error = None
    st.session_state.analysis_stage = "idle"
    st.session_state.jev_assessment = None
    st.session_state.jev_clarity_status = None
    st.session_state.laya_assessment = None
    st.session_state.laya_comparison_error = None
    st.session_state.last_request_id = None
    if clear_saved:
        st.session_state.saved_goal = None


async def _run_assessment(
    settings: Settings,
    request: GoalClarifyRequest,
) -> tuple[GoalAssessment, ClarityStatus]:
    judgments = TypeSafeJevClient(settings)
    generator = OpenAIGoalGenerationClient(settings)
    workflow = GoalAssistanceWorkflow(settings, judgments, generator)
    try:
        return await workflow.assess(request)
    finally:
        await judgments.aclose()


async def _run_laya_comparison(
    settings: Settings,
    request: GoalClarifyRequest,
) -> GoalAssessment | None:
    if not settings.laya_comparison_enabled:
        return None
    return await asyncio.to_thread(LayaComparisonClient(settings).assess_goal, request)


async def _run_comparative_assessment(
    settings: Settings,
    request: GoalClarifyRequest,
) -> tuple[
    tuple[GoalAssessment, ClarityStatus] | Exception,
    GoalAssessment | Exception | None,
]:
    jev_result, laya_result = await asyncio.gather(
        _run_assessment(settings, request),
        _run_laya_comparison(settings, request),
        return_exceptions=True,
    )
    return jev_result, laya_result


async def _run_after_assessment(
    settings: Settings,
    request: GoalClarifyRequest,
    assessment: GoalAssessment,
    clarity_status: ClarityStatus,
) -> GoalClarifyData:
    judgments = TypeSafeJevClient(settings)
    generator = OpenAIGoalGenerationClient(settings)
    workflow = GoalAssistanceWorkflow(settings, judgments, generator)
    try:
        return await workflow.run_after_assessment(request, assessment, clarity_status)
    finally:
        await judgments.aclose()


def _current_request(request_id: str) -> GoalClarifyRequest:
    return GoalClarifyRequest(
        request_id=request_id,
        original_text=st.session_state.flow_original_text,
        selected_goal_text=st.session_state.selected_goal_text,
        clarification_answers=st.session_state.clarification_answers,
    )


def _set_analysis_error(exc: Exception) -> None:
    if isinstance(exc, InputValidationError):
        st.session_state.analysis_error = "입력 길이 또는 답변 개수를 확인해 주세요."
    elif isinstance(exc, ModelConfigurationError):
        st.session_state.analysis_error = (
            f"{exc} AI/.env를 수정했다면 Streamlit 서버를 재시작해 주세요."
        )
    elif isinstance(exc, ModelUnavailableError):
        st.session_state.analysis_error = f"{exc} 잠시 후 다시 시도해 주세요."
    elif isinstance(exc, InvalidModelResponseError):
        st.session_state.analysis_error = (
            "모델 응답이 Schema 검증을 통과하지 못했습니다. 다시 분석해 주세요."
        )
    else:
        st.session_state.analysis_error = (
            "예상하지 못한 오류가 발생했습니다. 터미널 상태를 확인해 주세요."
        )


def _assess(*, selected_goal_text: str | None = None) -> None:
    original_text = st.session_state.flow_original_text
    if not original_text:
        st.session_state.analysis_error = "먼저 학습 목표를 입력해 주세요."
        return

    if selected_goal_text is not None:
        st.session_state.selected_goal_text = selected_goal_text

    request_id = f"streamlit-{uuid4()}"
    request = _current_request(request_id)
    st.session_state.last_request_id = request_id
    st.session_state.analysis_error = None
    st.session_state.analysis_result = None
    st.session_state.jev_assessment = None
    st.session_state.jev_clarity_status = None
    st.session_state.laya_assessment = None
    st.session_state.laya_comparison_error = None
    st.session_state.analysis_stage = "assessment"
    try:
        with st.spinner("JEV와 Laya가 같은 기준으로 학습 목표를 판단하고 있어요..."):
            jev_result, laya_result = asyncio.run(
                _run_comparative_assessment(get_settings(), request)
            )
        if isinstance(jev_result, Exception):
            raise jev_result
        assessment, clarity_status = jev_result
        st.session_state.jev_assessment = asdict(assessment)
        st.session_state.jev_clarity_status = clarity_status.value
        if isinstance(laya_result, GoalAssessment):
            st.session_state.laya_assessment = asdict(laya_result)
        elif isinstance(laya_result, Exception):
            st.session_state.laya_comparison_error = str(laya_result)
        st.session_state.analysis_stage = "generation"
    except Exception as exc:
        _set_analysis_error(exc)


def _generate_after_assessment() -> None:
    assessment_data = st.session_state.jev_assessment
    clarity_status_value = st.session_state.jev_clarity_status
    request_id = st.session_state.last_request_id
    if not assessment_data or not clarity_status_value or not request_id:
        st.session_state.analysis_error = "먼저 JEV 목표 판단을 완료해 주세요."
        return

    assessment = GoalAssessment(**assessment_data)
    clarity_status = ClarityStatus(clarity_status_value)
    request = _current_request(request_id)
    st.session_state.analysis_error = None
    st.session_state.analysis_stage = "generation"
    try:
        with st.spinner("질문·추천 문구와 최종 확인 정보를 생성하고 있어요..."):
            result = asyncio.run(
                _run_after_assessment(
                    get_settings(), request, assessment, clarity_status
                )
            )
        st.session_state.analysis_result = result.model_dump(
            mode="json", by_alias=True
        )
        st.session_state.analysis_stage = "done"
    except Exception as exc:
        _set_analysis_error(exc)


def _submit_new_goal() -> None:
    goal_text = st.session_state.goal_input.strip()
    if not goal_text:
        st.session_state.analysis_error = "공백이 아닌 학습 목표를 입력해 주세요."
        return
    _reset_flow()
    st.session_state.flow_original_text = goal_text
    _assess()


def _submit_answer(question_id: str, question: str, answer: str) -> None:
    st.session_state.clarification_answers.append(
        {"questionId": question_id, "question": question, "answer": answer}
    )
    _assess()


def _build_saved_goal(result: dict[str, Any]) -> dict[str, Any]:
    """Build the session-only saved value after explicit final confirmation."""

    if result.get("clarityStatus") != ClarityStatus.CLEAR.value:
        raise ValueError("명확한 목표만 저장할 수 있습니다.")
    profile = result.get("goalProfileDraft")
    if not profile or not result.get("interpretedGoal"):
        raise ValueError("확인 가능한 GoalProfile이 필요합니다.")
    return {
        "goalText": result["interpretedGoal"],
        "goalProfile": profile,
    }


def _save_goal(result: dict[str, Any]) -> None:
    st.session_state.saved_goal = _build_saved_goal(result)


def _render_configuration(settings: Settings) -> None:
    st.sidebar.header("테스트 설정")
    st.sidebar.write(
        "TypeSafe JEV",
        "✅ API Key 감지됨" if settings.typesafe_api_key else "❌ API Key 없음",
    )
    st.sidebar.write(
        "OpenAI",
        "✅ API Key 감지됨" if settings.openai_api_key else "❌ API Key 없음",
    )
    st.sidebar.caption(f"JEV 모델: {settings.typesafe_default_model}")
    st.sidebar.caption(f"문장 생성 모델: {settings.openai_model}")
    st.sidebar.write(
        "Laya 비교",
        "✅ 사용" if settings.laya_comparison_enabled else "➖ 비활성화",
    )
    if settings.laya_comparison_enabled:
        st.sidebar.caption(f"Laya 모델: {settings.laya_comparison_model}")
        st.sidebar.caption("첫 비교 시 공개 체크포인트 다운로드로 시간이 더 걸릴 수 있습니다.")
    st.sidebar.warning(
        "입력한 목표와 답변은 외부 JEV·OpenAI 서비스로 전송됩니다. 테스트용 문장만 사용하세요."
    )
    if st.session_state.saved_goal:
        st.sidebar.success("테스트 세션에 저장된 목표")
        st.sidebar.write(st.session_state.saved_goal["goalText"])
    if st.sidebar.button("전체 초기화", use_container_width=True):
        _reset_flow(clear_input=True, clear_saved=True)
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
    if result["clarityStatus"] == ClarityStatus.NEEDS_SUGGESTION.value:
        st.caption("입력한 목표는 넓지만 학습을 시작할 수 있어요. 원문을 유지하거나 추천 목표를 선택해 주세요.")
        if st.button("입력한 목표로 바로 시작", use_container_width=True):
            _assess(selected_goal_text=st.session_state.flow_original_text)
            st.rerun()
    for goal in result["recommendedGoals"]:
        with st.container(border=True):
            st.write(f"**{goal['title']}**")
            st.caption(goal["reason"])
            if st.button(
                "이 목표 선택",
                key=f"candidate-{goal['id']}",
                use_container_width=True,
            ):
                _assess(selected_goal_text=goal["title"])
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


def _generation_action_label(status: ClarityStatus) -> str:
    if status is ClarityStatus.CLEAR:
        return "최종 확인 정보 생성"
    if status in {ClarityStatus.NEEDS_SELECTION, ClarityStatus.NEEDS_SUGGESTION}:
        return "추천 목표 생성"
    return "확인 질문 생성"


def _render_jev_assessment(*, show_generation_action: bool = True) -> None:
    assessment = st.session_state.jev_assessment
    laya_assessment = st.session_state.laya_assessment
    laya_error = st.session_state.laya_comparison_error
    clarity_status_value = st.session_state.jev_clarity_status
    if not assessment or not clarity_status_value:
        return

    status = ClarityStatus(clarity_status_value)
    st.subheader("JEV 판단 결과")
    if status is ClarityStatus.INVALID:
        st.error(STATUS_LABELS[status])
        st.write("학습할 주제와 활동을 포함하도록 목표를 직접 수정해 주세요.")
    else:
        st.info(STATUS_LABELS[status])
        if show_generation_action:
            st.caption(
                "JEV 판단을 먼저 확인했습니다. 아래 버튼을 누르면 필요한 질문·추천 문구를 생성합니다."
            )

    if laya_assessment:
        laya_rows = _jev_assessment_rows(laya_assessment)
        comparison_rows = [
            {
                "판단 항목": jev_row["판단 항목"],
                "JEV": jev_row["결과"],
                "Laya 원시값 (실험용)": laya_row["결과"],
                "설명": jev_row["설명"],
            }
            for jev_row, laya_row in zip(
                _jev_assessment_rows(assessment), laya_rows, strict=True
            )
        ]
        st.caption(
            "Laya는 같은 입력·질문 기준의 로컬 원시 비교 결과입니다. 실제 상태·질문·저장 흐름은 JEV만 사용합니다."
        )
        st.warning(
            "Laya Multilingual 기본 모델은 이 목표 분류 도메인에서 보정·검증되지 않았습니다. "
            "Laya 퍼센트는 JEV 확률과 직접 비교하거나 통과 기준으로 해석하지 마세요."
        )
        rows = comparison_rows
    else:
        rows = _jev_assessment_rows(assessment)
    st.dataframe(
        rows,
        hide_index=True,
        use_container_width=True,
    )
    if laya_error:
        st.warning(
            f"Laya 비교 결과를 표시하지 못했습니다. JEV 흐름에는 영향이 없습니다. {laya_error}"
        )
    with st.expander("JEV 원본 JSON 확인"):
        st.json(assessment)

    if show_generation_action and status is not ClarityStatus.INVALID and st.button(
        _generation_action_label(status), type="primary", use_container_width=True
    ):
        _generate_after_assessment()
        st.rerun()


def _render_result() -> None:
    if st.session_state.analysis_error:
        st.error(st.session_state.analysis_error)
        if st.button("다시 분석", use_container_width=True):
            if (
                st.session_state.analysis_stage == "generation"
                and st.session_state.jev_assessment
            ):
                _generate_after_assessment()
            else:
                _assess()
            st.rerun()
        return

    result = st.session_state.analysis_result
    if result is None:
        _render_jev_assessment()
        return

    _render_jev_assessment(show_generation_action=False)
    st.divider()

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
        st.subheader("최종 확인")
        st.write("아래 목표와 범위를 확인한 뒤 저장해 주세요.")
        _render_profile(result["goalProfileDraft"])
        saved_goal = _build_saved_goal(result)
        if st.session_state.saved_goal == saved_goal:
            st.success("목표가 현재 테스트 세션에 저장되었습니다.")
        elif st.button(
            "최종 확인 후 목표 저장",
            type="primary",
            use_container_width=True,
        ):
            _save_goal(result)
            st.rerun()

    st.divider()
    st.caption(
        "이 화면의 저장은 현재 Streamlit 테스트 세션 메모리에만 유지되며 학습 세션을 시작하지 않습니다. "
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
    st.caption("JEV는 판단하고 생성 모델은 질문·추천·GoalProfile 문구를 생성합니다.")
    st.info(
        "목표 저장을 누르면 바로 저장하지 않고 먼저 AI 분석을 진행합니다. "
        "질문·추천 과정을 마친 뒤 명확한 목표만 최종 확인할 수 있습니다."
    )

    with st.form("goal-input-form"):
        st.text_area(
            "학습 목표",
            key="goal_input",
            max_chars=settings.goal_text_max_length,
            placeholder="예: React Query 캐싱 원리 학습",
            height=120,
        )
        submitted = st.form_submit_button(
            "목표 저장", type="primary", use_container_width=True
        )
    if submitted:
        _submit_new_goal()

    _render_result()


if __name__ == "__main__":
    main()

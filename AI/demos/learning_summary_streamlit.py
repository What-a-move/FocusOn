"""Run with: streamlit run AI/demos/learning_summary_streamlit.py"""

from __future__ import annotations

from pathlib import Path
import sys
import time

import streamlit as st

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from src.learning_summary_demo import DemoEvidence, copy_text, make_demo_summary


SCENARIOS = {
    "정상 (RELATED 2개)": [
        DemoEvidence("React Query", "캐시와 갱신 개념을 다룬 자료 확인"),
        DemoEvidence("쿼리 키", "쿼리 키 구성 예시 확인"),
    ],
    "근거 0개": [],
    "근거 1개": [DemoEvidence("React Query", "캐시 설명 자료 확인")],
    "중복 근거": [
        DemoEvidence("React Query", "캐시 설명 자료 확인"),
        DemoEvidence("React Query", "캐시 설명 자료 확인"),
    ],
    "제외·민감 화면": [
        DemoEvidence("React Query", "캐시 설명 자료 확인", "EXCLUDED"),
        DemoEvidence("계정", "로그인 화면 확인", "PRIVACY_BLOCKED"),
    ],
    "생성 실패": [
        DemoEvidence("React Query", "캐시 설명 자료 확인"),
        DemoEvidence("쿼리 키", "쿼리 키 예시 확인"),
    ],
}


def clear_previous_result() -> None:
    st.session_state.pop("demo_outcome", None)

st.set_page_config(page_title="FocusOn 학습 정리 시연", page_icon="📝", layout="centered")
st.title("학습 정리 로컬 시연")
st.warning("개발용 합성 데이터만 사용하세요. 실제 사이트·화면을 분석하지 않고 OpenAI·Server·저장소에 전송하지 않습니다.")
st.caption("#29 시연 — 제품 기능 완료 아님 · Notion 기획 REVIEW")

scenario = st.selectbox("테스트 시나리오", list(SCENARIOS), on_change=clear_previous_result)
goal = st.text_input("확정된 목표 문장 (가상)", value="React Query의 캐시와 쿼리 키 공부", on_change=clear_previous_result)
consent = st.checkbox("학습 기록 저장 동의 (가상)", value=True, on_change=clear_previous_result)
st.caption("최종 RELATED이고 서로 다른 짧은 근거 2개 이상일 때만 정리합니다. 제외·민감 화면과 동의 철회는 근거로 사용하지 않습니다.")

edited: list[DemoEvidence] = []
for index, evidence in enumerate(SCENARIOS[scenario]):
    with st.expander(f"가상 근거 {index + 1}: {evidence.concept}", expanded=True):
        concept = st.text_input("개념", value=evidence.concept, key=f"{scenario}-concept-{index}", on_change=clear_previous_result)
        observation = st.text_input("짧은 관찰", value=evidence.observation, key=f"{scenario}-observation-{index}", on_change=clear_previous_result)
        label = st.selectbox(
            "최종 화면 판정",
            ["RELATED", "UNRELATED", "UNCERTAIN", "EXCLUDED", "PRIVACY_BLOCKED"],
            index=["RELATED", "UNRELATED", "UNCERTAIN", "EXCLUDED", "PRIVACY_BLOCKED"].index(evidence.label),
            key=f"{scenario}-label-{index}",
            on_change=clear_previous_result,
        )
        edited.append(DemoEvidence(concept, observation, label))

if st.button("가상 세션 종료 후 정리", type="primary"):
    progress = st.empty()
    progress.info("QUEUED · 세션 종료와 시간 기록이 확정됐다고 가정합니다.")
    time.sleep(0.3)
    progress.info("GENERATING · 로컬 모의 정리 중입니다. 실제 AI 호출은 없습니다.")
    time.sleep(0.3)
    st.session_state["demo_outcome"] = make_demo_summary(
        goal, edited, record_consent=consent, simulate_failure=scenario == "생성 실패"
    )
    progress.empty()

outcome = st.session_state.get("demo_outcome")
if outcome is not None:
    st.subheader(f"결과: {outcome.status}")
    st.caption(f"사용된 서로 다른 허용 근거: {outcome.accepted_count}개")
    if outcome.status == "READY" and outcome.summary is not None:
        st.write(outcome.summary["overview"])
        for item in outcome.summary["keyConcepts"]:
            st.markdown(f"**{item['name']}** — {item['briefExplanation']}")
            st.caption(f"근거: {item['sessionEvidence']}")
        st.info(outcome.summary["nextStartPrompt"])
        st.code(copy_text(outcome), language=None)
        st.caption("위 텍스트를 복사해 확인할 수 있습니다. 자동 클립보드 복사는 Desktop 시연에 있습니다.")
    elif outcome.status == "INSUFFICIENT_DATA":
        st.info("관련 근거가 2개 미만이라 정리를 만들지 않았습니다. 세션 종료·시간 기록에는 영향이 없습니다.")
    else:
        st.error("가상 생성에 실패했습니다. 세션 종료·시간 기록은 유지됩니다.")

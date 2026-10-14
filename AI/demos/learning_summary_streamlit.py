"""Run with: streamlit run AI/demos/learning_summary_streamlit.py"""

from __future__ import annotations

import os
from pathlib import Path
import sys
import time

import streamlit as st

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from src.learning_summary_demo import (
    SyntheticDOMPage,
    copy_text,
    evidence_from_dom_pages,
    make_demo_summary,
)
from src.learning_summary_openai import (
    SYNTHETIC_GOAL,
    generate_synthetic_dom_summary,
)


SCENARIOS = {
    "정상 (한 페이지 문단 2개)": [
        SyntheticDOMPage(
            "DOCUMENTATION",
            "React Query 캐시 가이드",
            "useQuery는 queryKey를 기준으로 데이터를 캐시에 저장합니다.\n"
            "staleTime 동안 데이터는 신선한 것으로 취급되고, 시간이 지나면 다시 가져올 수 있습니다.",
        ),
    ],
    "두 페이지 (각 문단 1개)": [
        SyntheticDOMPage(
            "DOCUMENTATION",
            "React Query queryKey 가이드",
            "queryKey는 조회 대상과 변수를 배열로 표현합니다.",
        ),
        SyntheticDOMPage(
            "DOCUMENTATION",
            "React Query 캐시 갱신 가이드",
            "invalidateQueries에 queryKey를 전달하면 해당 캐시가 무효화됩니다.",
        ),
    ],
    "근거 0개": [],
    "근거 1개": [SyntheticDOMPage("DOCUMENTATION", "React Query 캐시", "캐시 설명 문단 하나만 확인했습니다.")],
    "중복 문단": [
        SyntheticDOMPage(
            "DOCUMENTATION",
            "React Query 캐시",
            "같은 queryKey는 캐시 데이터를 재사용합니다.\n같은 queryKey는 캐시 데이터를 재사용합니다.",
        ),
    ],
    "제외·민감 화면": [
        SyntheticDOMPage("DOCUMENTATION", "React Query", "캐시 설명 문단을 확인했습니다.", relation="EXCLUDED"),
        SyntheticDOMPage("DOCUMENTATION", "로그인", "개인정보가 포함된 화면", relation="PRIVACY_BLOCKED"),
    ],
    "생성 실패": [
        SyntheticDOMPage(
            "DOCUMENTATION",
            "React Query 캐시 가이드",
            "useQuery는 queryKey를 기준으로 데이터를 캐시에 저장합니다.\n"
            "staleTime 동안 데이터는 신선한 것으로 취급됩니다.",
        ),
    ],
}


def clear_previous_result() -> None:
    st.session_state.pop("demo_outcome", None)
    st.session_state.pop("openai_outcome", None)

st.set_page_config(page_title="FocusOn 학습 정리 시연", page_icon="📝", layout="centered")
st.title("학습 정리 로컬 시연")
st.warning("가상 DOM 추출 데이터를 사용하세요. 실제 화면 데이터는 입력하지 마세요. OpenAI 버튼을 누르면 아래에서 편집한 합성 페이지 데이터가 전송됩니다.")
st.caption("#29 시연 — 제품 기능 완료 아님 · Notion 기획 REVIEW")

scenario = st.selectbox("테스트 시나리오", list(SCENARIOS), on_change=clear_previous_result)
goal = st.text_input("확정된 목표 문장 (가상)", value="React Query의 캐시와 쿼리 키 공부", on_change=clear_previous_result)
consent = st.checkbox("학습 기록 저장 동의 (가상)", value=True, on_change=clear_previous_result)
st.caption("문서의 DOM 추출 형태(pageType·title·text·ocr·추출 상태 등)를 흉내 냅니다. RELATED 페이지의 서로 다른 문단 2개부터 정리합니다.")

edited: list[SyntheticDOMPage] = []
for index, page in enumerate(SCENARIOS[scenario]):
    with st.expander(f"가상 DOM 페이지 {index + 1}: {page.title}", expanded=True):
        page_type = st.selectbox(
            "pageType",
            ["DOCUMENTATION", "QA", "ARTICLE", "OTHER"],
            index=["DOCUMENTATION", "QA", "ARTICLE", "OTHER"].index(page.page_type),
            key=f"{scenario}-pageType-{index}",
            on_change=clear_previous_result,
        )
        title = st.text_input("title", value=page.title, key=f"{scenario}-title-{index}", on_change=clear_previous_result)
        text = st.text_area("text (DOM에서 추출된 가상 텍스트)", value=page.text, height=140, key=f"{scenario}-text-{index}", on_change=clear_previous_result)
        extraction_method = st.selectbox(
            "extractionMethod",
            ["DOM", "OCR"],
            index=["DOM", "OCR"].index(page.extraction_method),
            key=f"{scenario}-method-{index}",
            on_change=clear_previous_result,
        )
        extraction_status = st.selectbox(
            "extractionStatus",
            ["SUCCESS", "FAILED"],
            index=["SUCCESS", "FAILED"].index(page.extraction_status),
            key=f"{scenario}-status-{index}",
            on_change=clear_previous_result,
        )
        extractor_version = st.text_input(
            "extractorVersion", value=page.extractor_version,
            key=f"{scenario}-version-{index}", on_change=clear_previous_result
        )
        relation = st.selectbox(
            "최종 화면 관련성 (추출 객체와 별도 결과)",
            ["RELATED", "UNRELATED", "UNCERTAIN", "EXCLUDED", "PRIVACY_BLOCKED"],
            index=["RELATED", "UNRELATED", "UNCERTAIN", "EXCLUDED", "PRIVACY_BLOCKED"].index(page.relation),
            key=f"{scenario}-relation-{index}",
            on_change=clear_previous_result,
        )
        st.caption("DOM 경로의 ocr 값은 null입니다.")
        edited.append(SyntheticDOMPage(
            page_type, title, text, extraction_method, extraction_status,
            extractor_version, relation, ocr=None,
        ))
        st.json({
            "pageType": page_type,
            "title": title,
            "text": text,
            "ocr": None,
            "extractionMethod": extraction_method,
            "extractionStatus": extraction_status,
            "extractorVersion": extractor_version,
        })

selected_evidence = evidence_from_dom_pages(edited, record_consent=consent)
st.caption(f"정제 후 사용할 서로 다른 RELATED 문단: {len(selected_evidence)}개")
with st.expander("AI 생성기에 전달될 근거 미리보기", expanded=False):
    st.json({
        "goalSnapshot": goal,
        "reviewEvidence": [
            {"pageType": item.page_type, "sourceTitle": item.concept, "text": item.observation}
            for item in selected_evidence
        ],
    })

if st.button("가상 세션 종료 후 정리", type="primary"):
    progress = st.empty()
    progress.info("QUEUED · 세션 종료와 시간 기록이 확정됐다고 가정합니다.")
    time.sleep(0.3)
    progress.info("GENERATING · 로컬 모의 정리 중입니다. 실제 AI 호출은 없습니다.")
    time.sleep(0.3)
    st.session_state["demo_outcome"] = make_demo_summary(
        goal, selected_evidence, record_consent=consent, simulate_failure=scenario == "생성 실패"
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

st.divider()
st.subheader("OpenAI 생성 테스트 · 합성 DOM 입력")
st.caption("위에서 입력한 가상 DOM 페이지에서 통과된 RELATED 문단만 보냅니다. 실제 화면 추출 데이터는 연결되어 있지 않습니다.")
has_api_key = bool(os.environ.get("OPENAI_API_KEY"))
if not has_api_key:
    st.info("OPENAI_API_KEY가 없어 실제 호출 버튼을 사용할 수 없습니다. 로컬 모의 시연과 자동 테스트는 계속 가능합니다.")
if st.button("현재 합성 DOM 근거로 OpenAI 생성", disabled=not has_api_key or len(selected_evidence) < 2):
    with st.spinner("현재 합성 DOM 근거로 생성 중 · 최대 20초"):
        st.session_state["openai_outcome"] = generate_synthetic_dom_summary(goal, edited)

openai_outcome = st.session_state.get("openai_outcome")
if openai_outcome is not None:
    st.subheader(f"OpenAI 테스트 결과: {openai_outcome.status}")
    if openai_outcome.status == "READY" and openai_outcome.summary is not None:
        st.code(copy_text(openai_outcome), language=None)
        st.caption("AI 생성 결과입니다. 실제 사용자 화면·세션·저장소와 연결되지 않았습니다.")
    else:
        st.error("생성 또는 검증에 실패했습니다. 원본 모델 응답은 표시하거나 저장하지 않습니다.")

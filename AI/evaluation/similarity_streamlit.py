"""Run with: streamlit run AI/evaluation/similarity_streamlit.py"""

from __future__ import annotations

import json

import streamlit as st

from similarity_methods import (
    METHODS,
    VECTOR_METHODS,
    parse_cases,
    parse_goal,
    prepare_case,
    score_method,
)


EMBEDDING_MODEL = "intfloat/multilingual-e5-small"
RERANKER_MODEL = "BAAI/bge-reranker-v2-m3"


@st.cache_resource(show_spinner=False)
def load_embedder(device: str):
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(EMBEDDING_MODEL, device=device)


@st.cache_resource(show_spinner=False)
def load_reranker(device: str):
    from sentence_transformers import CrossEncoder

    return CrossEncoder(RERANKER_MODEL, device=device)


def _device() -> tuple[str, object | None]:
    try:
        import torch
    except ImportError:
        return "cpu", None
    return ("cuda" if torch.cuda.is_available() else "cpu"), torch


def _score_text(score: float | int | None) -> str:
    if score is None:
        return "—"
    return str(score) if isinstance(score, int) else f"{score:.4f}"


def main() -> None:
    st.set_page_config(page_title="FocusOn 유사도 비교", layout="wide")
    st.title("FocusOn 유사도 방식 비교")
    st.caption("로컬 실험 도구 · DOM/OCR 추출 이후 텍스트를 입력 · 실제 임베딩/재정렬 모델 사용")
    st.info(
        "이 화면은 관련성 후보와 원점수를 비교합니다. 한 사례의 점수는 정확도나 정답 확률이 아닙니다. "
        "임계값이 검증되지 않은 벡터 방식은 최종 판정을 보류합니다. "
        "UNRELATED 한 번이 곧 이탈 알림은 아닙니다."
    )

    with st.form("similarity_test"):
        goal_text = st.text_area(
            "학습 목표",
            value="파인튜닝 학습",
            height=90,
            help="일반 문장 또는 기존 GoalProfile/더미 goal JSON을 붙여넣으세요.",
        )
        source = st.radio(
            "텍스트 출처", ["CHROME_DOM", "CHROME_VIEWPORT_OCR", "DESKTOP_APP_OCR"],
            horizontal=True,
        )
        content_text = st.text_area(
            "DOM/OCR 추출 텍스트",
            height=220,
            placeholder="추출된 본문 또는 pageTitle/text/contentSource가 있는 JSON을 붙여넣으세요.",
            help="기존 더미 JSON의 cases 배열도 받을 수 있습니다. 정답 필드는 계산에 전달하지 않습니다.",
        )
        selected = st.multiselect(
            "유사도 방식 선택", list(METHODS), default=list(METHODS),
            format_func=lambda key: f"{key}. {METHODS[key]}",
        )
        submitted = st.form_submit_button("선택한 방식 비교", type="primary")

    with st.expander("붙여넣기 형식과 방식별 한계"):
        st.markdown(
            "두 텍스트 칸만 사용합니다. 목표 칸에는 일반 문장 또는 아래처럼 **기존 GoalProfile 필드**를 "
            "넣을 수 있습니다. 일반 문장만 넣으면 보조·선수 개념을 알 수 없어 2·4·5·6·7번은 "
            "직접 개념만 사용하는 부분 비교가 됩니다. 5~7번의 Graph는 GoalProfile의 핵심·보조 개념 "
            "관계를 이용한 최소 실험형입니다. 콘텐츠 칸에는 추출 텍스트 자체를 넣거나 "
            "기존 `pageTitle`·`text`·`contentSource`·`extractionStatus` JSON을 넣으세요."
        )
        st.code(json.dumps({
            "mainTopic": "파인튜닝 학습",
            "coreTopics": ["Fine-tuning", "LoRA", "PEFT"],
            "supportingTopics": ["Transformer", "선형대수", "행렬"],
        }, ensure_ascii=False, indent=2), language="json")
        st.code(json.dumps({
            "pageTitle": "LoRA로 언어 모델 파인튜닝하기",
            "text": "LoRA 어댑터와 PEFT를 이용한 미세조정 예제입니다.",
            "contentSource": "CHROME_DOM", "extractionStatus": "SUCCESS",
        }, ensure_ascii=False, indent=2), language="json")
        st.markdown(
            "[Notion의 1~8번 방식](https://app.notion.com/p/3e1357537c3180b7a519f8c7940c80d2) 중 "
            "1~7번을 비교합니다. 8번은 목표 개념 확장 방법이므로 별도 점수 방식이 아닙니다. "
            "단순 키워드 미적중, 낮은 코사인, Graph 미적중만으로 UNRELATED를 만들지 않습니다."
        )

    if not submitted:
        return
    if not selected:
        st.warning("비교할 방식을 하나 이상 선택하세요.")
        return
    try:
        goal = parse_goal(goal_text)
        cases = parse_cases(content_text, source)
    except ValueError as exc:
        st.error(str(exc))
        return

    if not goal.structured or not goal.support_terms:
        st.warning(
            "목표의 보조·선수 개념이 제공되지 않았습니다. 2·4·5·6·7번의 Graph/확장 결과는 "
            "직접 개념만 사용한 부분 비교입니다. 목표 칸에 GoalProfile JSON을 넣으면 보조 개념도 비교합니다."
        )
    st.caption("텍스트만 입력하거나 JSON에 qualityScore가 없으면 1.0으로 가정합니다. 실제 OCR 품질 측정값이 아닙니다.")

    prepared = [prepare_case(case) for case in cases]
    device, torch = _device()
    st.caption(f"실행 장치: {device.upper()} · 임베딩 후보: {EMBEDDING_MODEL} · 재정렬 후보: {RERANKER_MODEL}")
    embedder = None
    reranker = None
    runnable = any(case.can_score for case in prepared)
    if runnable and any(method in VECTOR_METHODS for method in selected):
        try:
            with st.spinner("실제 임베딩 모델을 준비하는 중입니다. 첫 실행에는 다운로드가 포함될 수 있습니다."):
                embedder = load_embedder(device)
        except (ImportError, OSError, RuntimeError):
            st.warning("임베딩 모델을 불러오지 못했습니다. 벡터 방식은 NOT_RUN으로 표시합니다.")
    if runnable and "7" in selected and embedder is not None:
        try:
            with st.spinner("재정렬 모델을 준비하는 중입니다."):
                reranker = load_reranker(device)
        except (ImportError, OSError, RuntimeError):
            st.warning("재정렬 모델을 불러오지 못했습니다. 7번은 NOT_RUN으로 표시합니다.")

    synchronize = torch.cuda.synchronize if device == "cuda" and torch is not None else None
    rows = []
    for case, clean in zip(cases, prepared):
        passages_by_id = {passage_id: passage_text for passage_id, _, passage_text in clean.passages}
        for method in selected:
            try:
                result = score_method(method, goal, clean, embedder, reranker, synchronize)
            except (RuntimeError, ValueError, TypeError):
                rows.append({
                    "사례": case.case_id, "방식": f"{method}. {METHODS[method]}",
                    "상태": "ERROR", "실험 후보": "—", "relevanceLabel": "UNCERTAIN",
                    "원점수": "—", "점수 종류": "—", "근거 ID": "—",
                    "근거 텍스트": "—", "방식별 계산 근거": "—",
                    "reasonCode": "ANALYSIS_UNAVAILABLE",
                    "판정 이유": "모델 계산에 실패해 판정을 적용하지 않았습니다.",
                    "처리 시간 ms": "—",
                })
                continue
            rows.append({
                "사례": case.case_id,
                "방식": f"{method}. {result.method_name}",
                "상태": result.status,
                "실험 후보": result.candidate,
                "relevanceLabel": result.relevance_label,
                "원점수": _score_text(result.score),
                "점수 종류": result.score_name,
                "근거 ID": ", ".join(result.evidence_ids) or "—",
                "근거 텍스트": " | ".join(
                    passages_by_id[passage_id][:180]
                    for passage_id in result.evidence_ids
                    if passage_id in passages_by_id
                ) or "—",
                "방식별 계산 근거": result.detail or "—",
                "reasonCode": result.reason_code,
                "판정 이유": result.reason,
                "처리 시간 ms": f"{result.elapsed_ms:.2f}" if result.status == "COMPLETED" else "—",
            })
    st.subheader("방식별 결과")
    st.dataframe(rows, hide_index=True)
    st.caption(
        "처리 시간은 모델 적재·다운로드·전처리를 제외한 방식별 1회 계산 시간입니다. "
        "P50/P95와 정확도는 고정 정답 세트를 반복 평가한 후 계산해야 합니다. "
        "실험 후보와 relevanceLabel을 구분해 보세요."
    )


if __name__ == "__main__":
    main()

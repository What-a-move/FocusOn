"""Run locally with PYTHONPATH=AI:. python -m streamlit run AI/evaluation/goal_profile_streamlit.py."""

import csv
import io
import logging
import os
from contextlib import contextmanager
from pathlib import Path
from time import perf_counter

import streamlit as st
from dotenv import dotenv_values, load_dotenv
from openai import APIError, APIStatusError, OpenAI

from AI.evaluation.goal_profile_experiment import (
    BASE_URL, EMBEDDING_MODEL, ExperimentEmbeddings, compare_profile,
    effective_label, generate_profile, summarize,
)
from AI.evaluation.graphless_hybrid import GraphlessConfig
from AI.evaluation.goal_profile_jev import DEFAULT_MIN_PROBABILITY, JevPageJudge, open_jev_client
from src.api.schemas import GoalProfile, SafeInputError
from src.utils.logger import log_event


PROFILES = {"mainTopic": "핵심 주제의 이름", "coreTopics": "핵심 주제",
            "supportingTopics": "보조 주제", "expectedActivities": "예상 활동"}
logger = logging.getLogger(__name__)
DEFAULTS = GraphlessConfig()


def seed_profile(profile):
    st.session_state["profile_purpose"] = profile["purpose"]
    for field in PROFILES:
        value = profile[field]
        st.session_state[field] = "\n".join(value) if isinstance(value, list) else value


def split_items(text):
    return [line.strip() for line in text.splitlines() if line.strip()]


def diagnostic_rows(report, method):
    for row in report["rows"]:
        yield {
            "방식": method,
            "사례 ID": row["caseId"],
            "기대": row["expectedLabel"] or "—",
            "판정": effective_label(row),
            "사유": (row["publicReasonCode"] or row["reasonCode"])
            if row["method"] == "graphless" else row["reasonCode"],
            "출처 유형": row["analysisPath"],
            "다음 확인 출처": row["nextContentSource"],
            "Hybrid 최고점": row["score"],
            "관련 본문 비율": row["relatedCoverage"] if row["coverageApplicable"] else None,
            "무관 본문 비율": row["unrelatedCoverage"] if row["coverageApplicable"] else None,
            "처리 시간(ms)": row["elapsedMs"],
            "임베딩 호출 수": row["embeddingCalls"],
            "관련 최소 유사도": report["config"]["min_related_cosine"] if row["method"] == "graphless" else None,
            "관련 최소 결합점수": report["config"]["related_passage_threshold"] if row["method"] == "graphless" else None,
            "무관 최대 유사도": report["config"]["unrelated_cosine_threshold"] if row["method"] == "graphless" else None,
            "Jev 선택지 확률": row.get("jevProbability"),
            "Jev 최소 확률": report.get("jevMinProbability"),
            "Jev 호출 수": row.get("jevCalls"),
        }


def render_results(comparisons, labels):
    st.subheader("비교 결과")
    st.caption("입력한 화면의 후보 판정입니다. 점수는 보정된 신뢰도가 아니며 실제 포키 알림 전송이나 지연은 측정하지 않습니다.")
    reports = {
        f"{name} · {goal_type}": report
        for name, pair in comparisons.items()
        for goal_type, report in zip(("목표 한 줄", "확장 목표"), pair)
    }
    st.dataframe(
        [{"방식": method, **summarize(report["rows"]), "총 처리 시간(ms)": report["totalMs"]}
         for method, report in reports.items()],
        hide_index=True,
    )
    results_by_case = {
        row["caseId"]: {"화면": labels[row["caseId"]], "기대": row["expectedLabel"] or "—"}
        for row in next(iter(reports.values()))["rows"]
    }
    for method, report in reports.items():
        for row in report["rows"]:
            results_by_case[row["caseId"]][method] = effective_label(row)
    st.dataframe(
        list(results_by_case.values()),
        hide_index=True,
    )
    rows = [item for method, report in reports.items()
            for item in diagnostic_rows(report, method)]
    with st.expander("판정 사유 · 본문 근거 비율 · 처리 시간"):
        st.dataframe(rows, hide_index=True)
    if "Hybrid" in comparisons:
        render_hybrid_evidence(comparisons["Hybrid"], labels)
    csv_file = io.StringIO()
    writer = csv.DictWriter(csv_file, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
    st.download_button("결과 CSV 다운로드", csv_file.getvalue().encode("utf-8-sig"),
                       "focuson-goal-profile-comparison.csv", "text/csv")


def render_hybrid_evidence(reports, labels):
    with st.expander("Hybrid 문단별 판정 근거"):
        case_id = st.selectbox("사례", list(labels), format_func=labels.__getitem__)
        for method, report in zip(("목표 한 줄", "확장 목표"), reports):
            row = next(item for item in report["rows"] if item["caseId"] == case_id)
            st.markdown(f"**{method}**")
            matches = [
                {"문단": match["passageId"], "종류": match["unitType"],
                 "유사도": round(match["cosine"], 3), "키워드 점수": round(match["keywordScore"], 3),
                 "결합 점수": round(match["hybridScore"], 3),
                 "관련 근거": match["relatedEvidence"], "무관 근거": match["unrelatedEvidence"]}
                for match in row["matches"]
            ]
            if matches:
                st.dataframe(matches, hide_index=True)
            else:
                st.caption(row["publicReasonCode"] or row["reasonCode"])


def manual_case():
    source = st.selectbox("화면 입력 유형", ("CHROME_DOM", "DESKTOP_APP_OCR", "TAB_META", "APP_META"))
    guard = st.radio("화면 보호 상태", ("정상", "EXCLUDED", "PRIVACY_BLOCKED"), horizontal=True)
    case = {"caseId": "custom-1", "contentSource": source}
    if guard != "정상":
        case.update(isExcluded=guard == "EXCLUDED", isSensitive=guard == "PRIVACY_BLOCKED",
                    expectedLabel=guard)
    else:
        app_source = source in {"APP_META", "DESKTOP_APP_OCR"}
        if app_source:
            case["appName"] = st.text_input("앱 이름", "Visual Studio Code", max_chars=100)
        case["windowTitle" if app_source else "pageTitle"] = st.text_input(
            "창 제목" if app_source else "탭 제목", max_chars=200)
        if source in {"CHROME_DOM", "DESKTOP_APP_OCR"}:
            case["text"] = st.text_area("테스트용 DOM·OCR 텍스트", max_chars=2000)
        if source == "DESKTOP_APP_OCR":
            confidence = st.number_input("테스트 OCR 인식 점수", 0.0, 1.0, 0.95)
            case["ocr"] = {"confidence": confidence, "captureStatus": "SUCCESS"}
        expected = st.selectbox("기대 판정", ("미지정", "RELATED", "UNRELATED", "UNCERTAIN"))
        if expected != "미지정":
            case["expectedLabel"] = expected
    return [case], {"custom-1": "직접 입력한 화면"}


@contextmanager
def openai_embeddings(api_key):
    with OpenAI(api_key=api_key, base_url=BASE_URL, timeout=10, max_retries=0) as client:
        yield ExperimentEmbeddings(client)


def main(*, provider="OpenAI", key_name="OPENAI_API_KEY", embedding_models=EMBEDDING_MODEL,
         profile_generator=None, embedding_client=openai_embeddings):
    st.set_page_config(page_title=f"FocusOn 화면 관련성 비교 · {provider}", layout="wide")
    env_path = Path(__file__).resolve().parents[1] / ".env"
    if provider == "OpenAI":
        load_dotenv(env_path, override=False)
    env = dotenv_values(env_path)
    api_key = (os.environ.get(key_name) or env.get(key_name) or "").strip()
    jev_key = (os.environ.get("TYPESAFE_API_KEY") or env.get("TYPESAFE_API_KEY") or "").strip()
    jev_model = (os.environ.get("TYPESAFE_MODEL") or os.environ.get("TYPESAFE_DEFAULT_MODEL")
                 or env.get("TYPESAFE_MODEL") or env.get("TYPESAFE_DEFAULT_MODEL") or "jev-latest")
    profile_generator = profile_generator or generate_profile

    st.title(f"화면 관련성 비교 · {provider}")
    st.write("같은 화면을 목표 한 줄과 확장된 목표로 각각 판정해 비교합니다.")
    st.caption(f"임베딩 모델 · {embedding_models}　|　화면마다 같은 Hybrid 기준을 적용합니다.")
    if not api_key:
        st.info(f"Hybrid를 실행하려면 AI/.env에 {key_name}를 설정해 주세요.")
    st.subheader("학습 목표")
    goal = st.text_input("목표 한 줄", max_chars=40)
    if st.button("목표 확장 자동 생성", disabled=not api_key):
        st.session_state.pop("profile_generation_timing", None)
        started = perf_counter()
        try:
            with st.spinner("목표의 주제와 예상 활동을 정리하고 있습니다."):
                profile = profile_generator(goal, api_key)
                seed_profile(profile.model_dump(mode="json", by_alias=True))
            st.session_state["profile_generation_timing"] = (goal.strip(), perf_counter() - started)
        except APIStatusError as exc:
            log_event(logger, "request.fail", level=logging.WARNING,
                      error_code="GOAL_EXPANSION_FAILED", http_status=exc.status_code)
            st.error(f"목표 확장 요청이 실패했습니다. (HTTP {exc.status_code})")
        except APIError:
            log_event(logger, "request.fail", level=logging.WARNING, error_code="GOAL_EXPANSION_FAILED")
            st.error("목표 확장 요청에 실패했습니다. 목록을 직접 입력해도 비교할 수 있습니다.")
        except (ValueError, SafeInputError):
            st.error("목표 또는 생성된 확장 목록을 확인해 주세요.")
    timing = st.session_state.get("profile_generation_timing")
    if timing is not None and timing[0] == goal.strip():
        st.caption(f"목표 확장 생성 시간 · {timing[1]:.2f}초")
    st.text_input(PROFILES["mainTopic"], key="mainTopic", max_chars=200)
    columns = st.columns(3)
    for column, field in zip(columns, ("coreTopics", "supportingTopics", "expectedActivities")):
        column.text_area(PROFILES[field], key=field, height=115, help="한 줄에 한 항목을 입력해 주세요.")

    st.subheader("테스트 화면")
    st.caption("개인정보가 없는 합성 또는 공개 테스트 텍스트를 입력해 주세요.")
    cases, labels = manual_case()

    use_jev = st.checkbox("Jev로 평가", disabled=not jev_key)
    if not jev_key:
        st.caption("Jev를 사용하려면 AI/.env에 TYPESAFE_API_KEY를 설정해 주세요.")
    run_hybrid = bool(api_key)
    jev_min_probability = DEFAULT_MIN_PROBABILITY
    if use_jev:
        run_hybrid = st.checkbox("Hybrid도 함께 평가", value=bool(api_key), disabled=not api_key) and bool(api_key)
        jev_min_probability = st.number_input("Jev 최소 선택지 확률", 0.0, 1.0, DEFAULT_MIN_PROBABILITY, step=0.05,
                                              help="관련·무관 선택 확률이 이 값 아래이면 판단을 보류합니다.")
        st.caption("비교 실행 시 검사를 통과한 테스트 텍스트와 목표를 TypeSafe로 전송합니다.")

    with st.expander("본문 판정 기준 실험"):
        st.caption("기본값으로 먼저 기록해 보세요. 변경한 기준은 두 방식의 본문 분석에 똑같이 적용됩니다.")
        columns = st.columns(3)
        related_cosine = columns[0].number_input("관련 최소 유사도", 0.0, 1.0, DEFAULTS.min_related_cosine, step=0.01)
        related_hybrid = columns[1].number_input("관련 최소 결합점수", 0.0, 1.0, DEFAULTS.related_passage_threshold, step=0.01)
        unrelated_cosine = columns[2].number_input("무관 최대 유사도", 0.0, 1.0, DEFAULTS.unrelated_cosine_threshold, step=0.01)

    if st.button("기존과 비교 실행", type="primary", disabled=not (run_hybrid or use_jev)):
        st.session_state.pop("comparison", None)
        try:
            if run_hybrid and unrelated_cosine >= related_cosine:
                raise ValueError("무관 최대 유사도는 관련 최소 유사도보다 낮게 설정해 주세요.")
            config = GraphlessConfig(min_related_cosine=related_cosine, related_passage_threshold=related_hybrid,
                                     unrelated_cosine_threshold=unrelated_cosine) if run_hybrid else DEFAULTS
            profile = GoalProfile.parse_safe({
                "mainTopic": st.session_state["mainTopic"].strip(),
                "purpose": st.session_state.get("profile_purpose", "OTHER"),
                **{field: split_items(st.session_state[field])
                   for field in ("coreTopics", "supportingTopics", "expectedActivities")},
            })
            comparisons = {}
            with st.spinner("기존과 확장 목표의 화면 판정을 비교하고 있습니다."):
                if run_hybrid:
                    with embedding_client(api_key) as client:
                        comparisons["Hybrid"] = compare_profile(goal, profile, cases, client, config=config)
                if use_jev:
                    with open_jev_client(jev_key) as client:
                        judge = JevPageJudge(client, jev_model, jev_min_probability)
                        comparisons["Jev"] = compare_profile(goal, profile, cases, None, jev_judge=judge)
                    for report in comparisons["Jev"]:
                        report["jevMinProbability"] = jev_min_probability
            st.session_state["comparison"] = comparisons, labels
        except SafeInputError:
            st.error("핵심 주제를 한 가지 이상 입력하고 중복 항목이나 테스트 화면 입력을 확인해 주세요.")
        except ValueError as exc:
            st.error(str(exc))

    if "comparison" in st.session_state:
        st.caption("아래 결과는 마지막으로 비교 실행한 입력 기준입니다.")
        render_results(*st.session_state["comparison"])


if __name__ == "__main__":
    main()

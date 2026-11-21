import json
import sys
from time import perf_counter

import streamlit as st
import torch

from activity import (
    MODEL_DIR,
    NAMES,
    ROOT,
    ActivityClassifier,
    app_identity,
    load_cases,
    validate_route,
)
from beta_decision import beta_decision

# Use the same target and sensitive-content checks as the AI ingress for local examples.
sys.path.insert(0, str(ROOT.parents[1]))
from src.analysis.acquisition_policy import (
    desktop_target_block_reason,
    next_content_source,
)
from src.preprocessing.models import ContentSource, ExtractionStatus
from src.preprocessing.validator import contains_sensitive_data


@st.cache_resource
def load_model():
    torch.set_num_threads(1)
    return ActivityClassifier()


@st.cache_data
def load_examples():
    return load_cases(ROOT / "data/eval.jsonl")


def observation(raw):
    if not isinstance(raw, dict):
        raise TypeError("화면 한 개를 JSON 객체로 입력해 주세요.")
    payload = raw.get("input", raw)
    if not isinstance(payload, dict):
        raise TypeError("화면 입력을 확인해 주세요.")
    validate_route(payload)
    source = ContentSource(payload["contentSource"])
    if desktop_target_block_reason(source, app_identity(payload)):
        raise ValueError("이 앱은 데스크톱 화면 분석 대상에서 제외됩니다.")
    extraction = ExtractionStatus(payload.get("extractionStatus", "SUCCESS"))
    if extraction is ExtractionStatus.EXCLUDED:
        raise ValueError("분석 제외 화면입니다.")
    content = payload.get("contentPayload")
    if source in (ContentSource.APP_META, ContentSource.TAB_META) and (
        content is not None or payload.get("text")
    ):
        raise ValueError("메타데이터에는 화면 본문을 넣을 수 없습니다.")
    meta = payload.get("metadata") or payload
    fields = [meta.get(key) for key in ("pageTitle", "windowTitle", "appName", "urlHost")]
    fields += [(content or payload).get("text"), payload.get("goalSnapshot")]
    if any(contains_sensitive_data(text) for text in fields):
        raise ValueError("민감정보가 포함된 화면은 분석할 수 없습니다.")
    return payload, extraction


def title(case):
    payload = case["input"]
    metadata = payload.get("metadata") or payload
    return metadata.get("pageTitle") or metadata.get("windowTitle") or "(제목 없음)"


def run_observation(raw):
    payload, extraction = observation(raw)
    prediction = None
    if extraction in (ExtractionStatus.SUCCESS, ExtractionStatus.PARTIAL):
        prediction = load_model().predict([payload])[0]
    return prediction, beta_decision(payload, prediction, extraction)


st.set_page_config(page_title="FocusOn · 화면 활동 테스트")
st.title("화면 활동 테스트")
model_rows = json.loads((MODEL_DIR / "manifest.json").read_text(encoding="utf-8"))["rows"]
st.caption(f"{model_rows:,}건 베타 모델 · 탭 메타데이터와 Chrome DOM · 앱 메타데이터와 데스크톱 OCR")
if not (MODEL_DIR / "encoder" / "model.safetensors").is_file():
    st.error("먼저 python setup_encoder.py를 실행해 주세요.")
    st.stop()
examples = {case["caseId"]: case for case in load_examples()}
selection = st.selectbox(
    "테스트 화면",
    [None, *examples],
    format_func=lambda key: "JSON 직접 입력" if key is None else title(examples[key]),
)
example = examples.get(selection)
default = (
    example["input"]
    if example
    else {
        "contentSource": "TAB_META",
        "metadata": {"appName": "Google Chrome", "pageTitle": "", "urlHost": ""},
    }
)
with st.form(key=f"input:{selection}"):
    raw = st.text_area(
        "현재 화면의 관측 JSON", json.dumps(default, ensure_ascii=False, indent=2), height=240
    )
    run = st.form_submit_button("판정하기", type="primary")

if run:
    try:
        request = json.loads(raw)
        payload = request.get("input", request) if isinstance(request, dict) else request
        start = perf_counter()
        prediction, decision = run_observation(request)
    except (ValueError, AttributeError, TypeError):
        st.error(
            "입력 형식 또는 분석 허용 상태를 확인해 주세요. 메타데이터에는 본문을 넣을 수 없습니다."
        )
    else:
        st.metric("현재 단계의 베타 판정", NAMES[decision["label"]])
        if example and payload == example["input"]:
            st.write(f"예시 기대 판정 · {NAMES[example['expectedLabel']]}")
        public_label = {"STUDY": "RELATED", "OFF_TASK": "UNRELATED", "UNCERTAIN": "UNCERTAIN"}[
            decision["label"]
        ]
        followup = next_content_source(
            ContentSource(payload["contentSource"]), public_label, decision["reasonCode"]
        )
        if followup:
            st.info(f"이 단계에서는 추가 근거가 필요합니다. 다음 입력은 {followup.value}입니다.")
        st.caption(f"실행 {perf_counter() - start:.3f}초")
        st.json(
            {
                "모델 1위": prediction["rawLabel"] if prediction else None,
                "1%p 기준 적용 후": prediction["label"] if prediction and prediction["scores"] else None,
                "상위 두 점수 차이 (%p)": prediction["marginPp"] if prediction else None,
                "모델 점수": prediction["scores"] if prediction else None,
                "베타 판정": decision,
            }
        )

if st.button(f"기존 예시 {len(examples)}건 일괄 실행"):
    results = []
    start = perf_counter()
    for case in examples.values():
        try:
            prediction, decision = run_observation(case["input"])
        except (ValueError, AttributeError, TypeError):
            predicted = "분석 제외"
            prediction = None
        else:
            predicted = decision["label"]
        gold = case["expectedLabel"]
        results.append(
            {
                "화면": title(case),
                "출처": case["input"]["contentSource"],
                "기대": NAMES[gold],
                "모델 1위": (prediction or {}).get("rawLabel"),
                "베타 판정": NAMES.get(predicted, predicted),
                "일치": gold == predicted,
            }
        )
    st.metric("예시 정답", f"{sum(row['일치'] for row in results)}/{len(results)}")
    st.caption(f"실행 {perf_counter() - start:.3f}초")
    st.dataframe(results, hide_index=True)

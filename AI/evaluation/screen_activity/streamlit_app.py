import json
from time import perf_counter

import streamlit as st
import torch

from activity import MODEL_DIR, ROOT, NAMES, ActivityClassifier, load_cases

@st.cache_resource
def load_model():
    torch.set_num_threads(1)
    return ActivityClassifier()

@st.cache_data
def load_examples():
    return load_cases(ROOT / "data/eval.jsonl")

def observation(raw):
    if not isinstance(raw, dict):
        raise ValueError("화면 한 개를 JSON 객체로 입력해 주세요.")
    payload = raw.get("input", raw)
    if not isinstance(payload, dict):
        raise ValueError("화면 입력을 확인해 주세요.")
    if payload.get("contentSource") not in {"CHROME_DOM", "DESKTOP_APP_OCR"}:
        raise ValueError("Chrome DOM 또는 Apple Vision OCR 화면을 입력해 주세요.")
    return payload

def title(case):
    payload = case["input"]
    metadata = payload.get("metadata") or payload
    return metadata.get("pageTitle") or metadata.get("windowTitle") or "(제목 없음)"

st.set_page_config(page_title="FocusOn · 화면 활동 테스트")
st.title("화면 활동 테스트")
st.caption("Chrome DOM · Apple Vision OCR")
if not (MODEL_DIR / "encoder" / "model.safetensors").is_file():
    st.error("먼저 python setup_encoder.py를 실행해 주세요.")
    st.stop()
examples = {r["caseId"]: r for r in load_examples()}
selection = st.selectbox("테스트 화면", [None, *examples],
                         format_func=lambda key: "JSON 직접 입력" if key is None else title(examples[key]))
example = examples.get(selection)
default = json.dumps(example["input"] if example else {"contentSource": "CHROME_DOM", "pageTitle": "", "text": ""},
                     ensure_ascii=False, indent=2)
with st.form(key=f"input:{selection}"):
    raw = st.text_area("화면 JSON", default, height=240)
    run = st.form_submit_button("판정하기", type="primary")

if run:
    try:
        payload = observation(json.loads(raw))
        expected = example["expectedLabel"] if example and payload == example["input"] else None
        started = perf_counter()
        outcome = load_model().predict([payload])[0]
    except (ValueError, AttributeError, TypeError) as exc:
        st.error(str(exc))
    else:
        st.metric("판정", NAMES[outcome["label"]])
        if expected is not None:
            st.write(f"기대 판정 · {NAMES[expected]}")
        st.caption(f"실행 {perf_counter() - started:.3f}초")
        st.json({"판정": outcome["label"], "보류 적용 전": outcome["rawLabel"], "상위 두 점수 차이 (%p)": outcome["marginPp"], "기대 라벨": expected, "모델 점수": outcome["scores"]})

if st.button(f"예시 {len(examples)}건 일괄 실행"):
    cases = list(examples.values())
    start = perf_counter()
    scores = load_model().predict([r["input"] for r in cases])
    results = []
    for case, result in zip(cases, scores, strict=True):
        gold, predicted = case["expectedLabel"], result["label"]
        results.append({"ID": case["caseId"], "화면": title(case), "기대": NAMES[gold], "판정": NAMES[predicted],
                        "일치": gold == predicted, "분류": case["category"], "출처": case["input"]["contentSource"]})
    correct = sum(r["일치"] for r in results)
    st.metric("평가 정답", f"{correct}/{len(results)}")
    st.caption(f"실행 {perf_counter() - start:.3f}초")
    st.dataframe(results, hide_index=True)

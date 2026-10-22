"""Run: python3 -m streamlit run AI/evaluation/similarity_streamlit.py"""
from __future__ import annotations

import json
import os
import sys
from dataclasses import asdict
from pathlib import Path

import streamlit as st

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from AI.evaluation.streamlit_runner import (DEFAULT_REVISION, DEFAULT_URL,
                                            GraphlessSession, compare_graphless, parse_inputs)
from AI.evaluation.graphless_hybrid import (KEYWORD_EXTRACTOR_VERSION, SCORING_VERSION,
                                           load_calibrated_config, METADATA_CONFIG, METADATA_SCORING_VERSION)
from src.models.embedding_client import EmbeddingSettings, OllamaEmbeddingClient

REASONS = {
    "METADATA_HYBRID_RELATED": "실제 제목의 코사인·결합 점수가 메타데이터 관련 기준을 통과했습니다. 본문 길이·커버리지는 적용하지 않습니다.",
    "METADATA_HYBRID_UNRELATED": "주제를 식별할 수 있는 제목의 코사인이 메타데이터 무관 기준 이하입니다. 도메인·앱 이름만으로 판정하지 않습니다.",
    "METADATA_CONFLICTING": "페이지 제목과 창 제목에 관련·무관 근거가 함께 있어 추가 수집이 필요합니다.",
    "INSUFFICIENT_GOAL_KEYWORDS": "목표에 일반 표현만 있어 주제를 식별하기 어렵습니다. 구체적인 학습 대상을 입력하세요.",
    "RELATED_EVIDENCE_FOUND": "본문에 코사인·결합 점수 기준을 통과한 관련 근거가 있고 무관 근거와 충돌하지 않습니다. 관련 커버리지 최소 기준은 적용하지 않습니다.",
    "UNRELATED_COVERAGE_MET": "본문 코사인이 무관 기준 이하이고 본문 비율·길이·완전 추출 조건을 통과했습니다. 단어 한 개의 적중으로 이 판정을 막지 않습니다.",
    "INSUFFICIENT_BODY": "분석할 본문이 부족합니다. 제목만으로 본문 커버리지를 계산하지 않습니다.",
    "MODEL_OR_SCORING_FAILED": "임베딩 요청 또는 벡터 계산에 실패했습니다. 관련성 정답으로 집계하지 않습니다.",
    "INCOMPLETE_REPRESENTATION": "본문이 잘려 전체 화면을 대표한다고 볼 수 없어 판정을 보류했습니다.",
    "GOAL_CONNECTED": "관련 개념의 근거와 본문 커버리지가 기준을 통과했습니다.",
    "POSITIVE_UNRELATED_TOPIC": "목표와 무관한 주제의 양성 근거와 커버리지가 기준을 통과했습니다.",
    "INSUFFICIENT_EVIDENCE": "관련 본문 근거가 확인되지 않았고 무관 판정 조건도 충족하지 못했습니다. 관련 커버리지 60% 미달 때문에 보류한 것은 아닙니다.",
    "CONFLICTING_EVIDENCE": "관련 근거와 무관 근거가 함께 통과해 판정을 보류했습니다.",
    "METADATA_GOAL_CONNECTED": "제목 등 메타데이터에서 관련 개념 별칭이 충분히 확인됐습니다.",
    "METADATA_UNRELATED_TOPIC": "메타데이터에서 무관 주제 별칭이 충분히 확인됐습니다.",
    "METADATA_INSUFFICIENT": "메타데이터만으로 부족해 다음 수집 단계가 필요합니다.",
    "BLACK_SCREEN": "검은 화면이므로 관련성을 판정하지 않았습니다.",
    "OCR_METADATA_REQUIRED": "JSON에 OCR confidence와 captureStatus가 필요합니다.",
    "LOW_CONFIDENCE": "OCR 또는 추출 품질이 부족합니다.",
    "PRIVACY_BLOCKED": "민감정보 또는 민감 화면으로 분석을 차단했습니다.",
    "EXCLUDED": "분석 제외 입력입니다.",
}


def _example(kind: str) -> str:
    if kind == "공부 사례":
        text = "useState로 객체 상태를 업데이트할 때 기존 객체를 직접 수정하지 않습니다. 상태 변경 함수에 새 객체를 전달합니다.\n\n```javascript\nsetState(prev => ({ ...prev, count: prev.count + 1 }));\n```"
        title, expected = "React useState 객체 상태 업데이트", "RELATED"
    else:
        text = "오늘은 요리 레시피에 따라 재료를 준비하고 조리 시간을 맞춥니다. 레시피의 순서대로 채소와 고기를 익히는 요리 과정입니다. 양파와 당근을 잘게 썰고 팬에 볶은 다음 소금과 후추로 간을 맞춥니다. 완성된 음식은 접시에 담아 식탁에 올립니다."
        title, expected = "요리 레시피와 조리 방법", "UNRELATED"
    return json.dumps({"caseId": "manual-example", "contentSource": "CHROME_DOM", "pageTitle": title,
                       "text": text, "expectedLabel": expected}, ensure_ascii=False, indent=2)


def main():
    st.set_page_config(page_title="FocusOn 관련성 테스트", layout="wide")
    st.session_state.setdefault("goal", "React useState로 객체 상태 변경하기")
    try:
        config, calibration = load_calibrated_config()
    except (ValueError, TypeError, KeyError):
        st.error("점수 보정 설정의 버전·검증 상태가 맞지 않습니다. 보정 보고서와 설정 파일을 확인하세요.")
        return
    # Release the previous graph-based UI runtime when this page hot reloads.
    old_runtime = st.session_state.pop("hybrid_runtime", None)
    if old_runtime is not None:
        old_runtime.close()
    st.session_state.pop("resolver", None)
    st.title("FocusOn 키워드 + 벡터 Hybrid 테스트")
    st.caption("Graph 없는 Streamlit 실험 · 메타데이터 제목 / DOM·OCR 본문을 구분해 비교")
    st.caption("OpenAI API: 미사용 (Graph 생성 없음). 임베딩은 학교 Qwen API를 호출합니다.")
    st.caption("같은 화면의 제목·창 제목·명시 소제목을 본문 문맥으로 사용합니다. DOM/OCR 경로에서는 제목만으로 판정하지 않으며, "
               "원래 본문의 코사인이 최소 기준을 통과해야 문맥 점수를 관련 근거로 인정합니다.")
    st.caption("TAB_META·APP_META는 제목만 별도 분석합니다. 메타데이터 V≥0.55·H≥0.60, 본문 길이·커버리지 미적용. 도메인·앱 이름만으로는 판정하지 않습니다.")
    evaluation = calibration.get("evaluationMetrics")
    if evaluation:
        st.caption(f"DOM/OCR 보정에 쓰지 않은 목표의 합성 평가: {evaluation['correct']}/{evaluation['cases']} 일치 · "
                   f"Macro-F1 {evaluation['macroF1']:.3f} · 독립 사람 검수 전 참고 결과")
        if evaluation["unrelatedToRelated"]:
            st.warning(f"합성 평가에서 무관 화면 {evaluation['unrelatedToRelated']}건을 RELATED로 오판했습니다. "
                       "아직 운영 정확도가 검증된 기준은 아닙니다.")
    st.info("DOM/OCR 경로: 목표 키워드 추출 → Qwen 임베딩 → 문단별 키워드·코사인 결합 → 본문 커버리지 → "
            "RELATED / UNRELATED / UNCERTAIN 순서로 계산합니다. 보강 계수·임계값은 고정된 검증 전 실험값입니다.")
    st.caption("DOM/OCR 경로의 관련 커버리지는 참고 지표입니다. 60% 미만이어도 본문 관련 근거가 있고 무관 근거와 충돌하지 않으면 RELATED입니다. "
               "짧은 본문도 점수로 판단하며, 빈 본문·정보 부족·추출/모델 실패는 UNCERTAIN을 유지합니다.")
    with st.sidebar:
        st.header("학교 임베딩 연결")
        base_url = st.text_input("API 기본 주소", value=os.getenv("FOCUSON_EMBEDDING_URL", DEFAULT_URL))
        revision = st.text_input("로컬 revision 설정", value=os.getenv("FOCUSON_EMBEDDING_REVISION", DEFAULT_REVISION))
        timeout = st.number_input("요청 제한 시간 (초)", min_value=5, max_value=300, value=120)
        st.caption("qwen3-embedding:4b-fp16 · 2560차원 · POST /api/embed만 호출합니다. "
                   "revision·F16은 서버 조회값이 아닌 설정값입니다.")
        with st.expander("고정 실험 기준", expanded=True):
            st.caption("벡터 기본 + 키워드 보강 · 보강 계수 0.20")
            st.caption(f"점수 버전: {SCORING_VERSION}")
            st.json(asdict(config))
            st.caption(f"메타데이터 별도 실험값 ({METADATA_SCORING_VERSION}, 미보정): V ≥ {METADATA_CONFIG.min_related_cosine:.2f} 및 H ≥ {METADATA_CONFIG.related_passage_threshold:.2f}이면 관련 후보, V ≤ {METADATA_CONFIG.unrelated_cosine_threshold:.2f}이면 무관 후보. 본문 길이·커버리지 미적용. 빈/일반 제목은 UNCERTAIN.")
            st.caption("보정 상태: " + calibration.get("status", "SYNTHETIC_DEVELOPMENT_CALIBRATED")
                       + " · 개발용 선택 / 다른 목표 평가용 확인 · 독립 사람 검수 전 실험값")
        st.caption("낮은 유사도 기반 UNRELATED는 실험 규칙입니다. 별도 정답 데이터로 기준을 조정해야 합니다.")

    col1, col2 = st.columns(2)
    if col1.button("공부 예제 넣기"):
        st.session_state.update(goal="React useState로 객체 상태 변경하기", content=_example("공부 사례"))
    if col2.button("무관 예제 넣기"):
        st.session_state.update(goal="React useState로 객체 상태 변경하기", content=_example("무관 사례"))
    with st.form("comparison"):
        goal_raw = st.text_area("확정 학습 목표", key="goal", height=90,
                                help="목표 문장 또는 기존 ConfirmedGoal JSON. 키워드는 목표 text에서만 추출합니다. GoalProfile·템플릿·기대 라벨은 점수 계산에 사용하지 않습니다.")
        source = st.radio("입력 출처", ["CHROME_DOM", "CHROME_VIEWPORT_OCR", "DESKTOP_APP_OCR", "TAB_META", "APP_META"], horizontal=True)
        page_title = st.text_input("페이지 제목 (텍스트 입력 시)")
        content_raw = st.text_area("메타데이터·DOM/OCR 텍스트 또는 JSON", key="content", height=270,
                                  placeholder='{"contentSource":"CHROME_DOM","pageTitle":"자료 제목","text":"추출 본문"}',
                                  help="기존 단일 추출 JSON·배열·cases 지원. cases의 observations는 현재 화면 1개만 허용합니다. JSON에 적힌 출처·제목이 우선합니다.")
        with st.expander("OCR 텍스트 수동 입력 가정"):
            confidence = st.slider("수동 OCR confidence", 0.0, 1.0, .95, .01)
            capture_status = st.selectbox("수동 captureStatus", ["SUCCESS", "BLACK_SCREEN", "EMPTY_CAPTURE", "OCR_FAILED", "LOW_CONFIDENCE"])
            st.caption("원문 텍스트를 붙이는 경우의 수동 가정이며 실제 인식 품질 측정값이 아닙니다. OCR JSON은 자체 ocr 값을 사용하며, 누락되면 판정을 보류합니다.")
        st.markdown("**분석 방식: 키워드 검색 + 벡터 유사도를 결합하는 Hybrid Search**")
        st.caption("K = 발견된 목표 용어 가중치 합 / 전체 목표 용어 가중치 합 · V = 코사인 유사도 · "
                   "S = max(0, V) · H = S + 0.20 × K × (1 − S)")
        st.caption(f"DOM/OCR 관련 문단: 본문 V ≥ {config.min_related_cosine:.2f}, 본문 또는 문맥 H ≥ {config.related_passage_threshold:.2f} · "
                   f"무관 후보: 본문 V ≤ {config.unrelated_cosine_threshold:.2f}. "
                   "관련 커버리지는 참고값입니다. 본문 관련 근거가 있고 무관 근거와 충돌하지 않으면 RELATED입니다. "
                   "무관 커버리지 70%·본문 80자·완전 추출과 품질·충돌 검사는 유지합니다.")
        st.caption(f"키워드 추출: {KEYWORD_EXTRACTOR_VERSION} · 목표와 본문에 같은 조사·어미·불용어 제거를 적용합니다. "
                   "임베딩 원문은 유지하며, 분석기 최초 초기화는 로컬 처리 시간에 포함됩니다.")
        st.caption("상태·객체·값 같은 넓은 용어는 가중치 0.25, 다른 목표 용어는 1.0입니다. "
                   "일반 표현은 기존대로 제외하며 어떤 단어도 단독으로 무관 판정을 금지하지 않습니다.")
        submitted = st.form_submit_button("비교하기", type="primary")
    with st.expander("JSON 형식 · 실행 범위"):
        st.code(_example("공부 사례"), language="json")
        st.markdown("- `expectedLabel`은 테스트용 기대값이며 계산에 넣지 않습니다.\n"
                    "- 여러 건은 `[{...}, {...}]` 또는 `{\"cases\": [...]}`로 입력합니다.\n"
                    "- ID·세션 문맥이 없으면 로컬 테스트용 값을 채웁니다. 입력된 ID는 변경하지 않습니다.\n"
                    "- 제목·소제목 점수도 표시하지만 커버리지는 본문·코드·표·OCR만 계산합니다. TAB_META·APP_META는 실제 제목만 별도 판정하고, 불충분하면 DOM/OCR을 요청합니다.\n"
                    "- Graph·OpenAI 생성·ChromaDB는 사용하지 않습니다. 화면 본문·결과는 파일·DB에 자동 저장하지 않습니다.\n"
                    "- 목표·문단 벡터만 해시 키로 최대 5분 재사용하며 계산 규칙을 바꾸면 판정을 다시 계산합니다.")
    # Every rerun hides old results. No model work until explicit submit.
    if not submitted:
        return
    try:
        goal, cases = parse_inputs(goal_raw, content_raw, source, page_title=page_title,
                                  ocr_confidence=confidence, capture_status=capture_status)
        settings = EmbeddingSettings(base_url=base_url.strip().rstrip("/"), revision=revision.strip(), timeout_seconds=float(timeout))
        calibrated_embedding = calibration.get("embedding")
        if calibrated_embedding and (calibrated_embedding["model"] != settings.model
                                     or calibrated_embedding["dimensions"] != settings.dimensions
                                     or calibrated_embedding["configuredRevision"] != settings.revision):
            st.error("보정에 사용한 모델·차원·revision 설정과 다릅니다. 같은 설정을 사용하거나 다시 보정하세요.")
            return
        with st.spinner("키워드·임베딩·본문 커버리지 계산 중…"):
            runtime = st.session_state.get("graphless_runtime")
            if runtime is None or runtime.client.settings != settings:
                if runtime is not None:
                    runtime.close()
                runtime = GraphlessSession(OllamaEmbeddingClient(settings))
                st.session_state.graphless_runtime = runtime
            report = compare_graphless(goal, cases, runtime.client, config=config, session=runtime)
    except ValueError as error:
        if str(error) == "DATASET_GOAL_MISMATCH":
            st.error("목표 칸과 JSON의 goal.text가 다릅니다. 같은 주제의 목표와 데이터셋을 넣은 뒤 다시 비교하세요. 이번 입력은 모델로 보내지 않았습니다.")
        else:
            st.error("목표·JSON 형식·출처·API 설정을 확인하세요. OCR JSON에는 ocr 정보가 필요합니다.")
        return
    except Exception:
        st.error("실행하지 못했습니다. 임베딩 API 연결과 입력 설정을 확인하세요.")
        return
    st.subheader("비교 요약")
    st.caption(f"본문 점수 버전: {report.get('scoringVersion', SCORING_VERSION)} · 메타데이터: {report.get('metadataScoringVersion', METADATA_SCORING_VERSION)}")
    st.caption(f"전체 {report['totalMs'] / 1000:.3f}초 · Graph 생성 없음 · 임베딩 요청 {report['embeddingCalls']}회")
    st.caption(f"임베딩 API 왕복·벡터 검증 {report.get('embeddingMs', 0) / 1000:.3f}초 · 로컬 전처리·판정 {report.get('localMs', 0) / 1000:.3f}초")
    st.caption(f"OpenAI API: 미호출 · {report.get('openaiCalls', 0)}회 · {report.get('openaiMs', 0) / 1000:.3f}초 "
               "(현재 Graph 없는 분석 경로)")
    st.dataframe(report["summary"], hide_index=True, use_container_width=True)
    st.caption("일치율은 입력된 기대 라벨 기준의 참고값입니다. 오류·미실행을 정답으로 세지 않습니다. "
               "시간에는 API 왕복과 계산이 포함됩니다. 목표·문단 벡터는 5분간 재사용하며 커버리지와 라벨은 매번 다시 계산합니다. "
               "API 시간에는 학교 모델 처리와 네트워크가 함께 포함되므로 GPU 순수 처리 시간과 구분하세요.")
    for row in report["rows"]:
        label = row["relevanceLabel"]
        verdict = "기대값 없음" if row["expectedLabel"] is None else "일치" if row["matchesExpected"] else "불일치/미실행"
        with st.expander(f"{row['caseId']} · {row['method']}. {row['methodName']} → {label} · {verdict} · {row['elapsedMs']:.1f}ms"):
            st.write({"상태": row["status"], "relevanceLabel": label, "기대 라벨": row["expectedLabel"],
                      "본문 최고 결합 점수": row.get("bodyScore", row["score"]),
                      "화면 문맥 최고 점수": row.get("contextScore"),
                      "메타데이터 최고 점수" if row.get("analysisPath") == "METADATA" else "제목·소제목 최고 점수 (단독 판정 안 함)": row.get("metadataScore"),
                      "분석 경로": row.get("analysisPath", "BODY"),
                      "통과한 관련 근거 점수": row.get("decisionScore"),
                      "점수 종류": row["scoreType"], "reasonCode": row["reasonCode"],
                      "다음 요청 출처": row["nextContentSource"], "임베딩 요청": row["embeddingCalls"],
                      "벡터 전체 캐시 사용": row.get("cacheHit", False),
                      "임베딩 API 시간 ms": row.get("embeddingMs"), "로컬 계산 ms": row.get("localMs")})
            st.caption(f"OpenAI API: 미호출 · {row.get('openaiCalls', 0)}회 · {row.get('openaiMs', 0) / 1000:.3f}초")
            st.write(REASONS.get(row["reasonCode"], row.get("detail", "상세 상태와 근거를 확인하세요.")))
            st.write({"핵심 용어": row["keywordTerms"], "일반 표현 (단독 미반영)": row.get("generalTerms", []),
                      "목표 구문": row.get("goalPhrases", []), "목표 용어 가중치": row.get("keywordWeights", {}),
                      "발견된 핵심 용어": row["matchedKeywords"]})
            if not row.get("coverageApplicable", True):
                st.caption("본문 커버리지: 해당 없음. 메타데이터 제목 분석에는 본문 길이·커버리지 조건을 적용하지 않습니다.")
            else:
                left, right = st.columns(2)
                left.metric("관련 본문 커버리지", f"{row['relatedCoverage']:.1%}",
                            help=f"관련 근거 {row['relatedBodyChars']}자 / 전체 분석 본문 {row['totalBodyChars']}자")
                right.metric("무관 후보 본문 커버리지", f"{row['unrelatedCoverage']:.1%}",
                             help=f"무관 후보 {row['unrelatedBodyChars']}자 / 전체 분석 본문 {row['totalBodyChars']}자")
                st.caption(f"전체 분석 본문 {row['totalBodyChars']}자 · 관련 {row['relatedBodyChars']}자 · 무관 후보 {row['unrelatedBodyChars']}자. "
                           "제목·소제목 제외, 설명+코드 묶음의 원본 글자는 한 번만 셉니다. 관련·무관 후보가 함께 있으면 UNCERTAIN입니다.")
            for evidence in row["evidence"]:
                st.text(f"근거 {evidence['id']}")
                st.code(evidence["text"], language=None)
            if row["matches"]:
                st.caption("메타데이터 제목별 점수입니다. 본문 길이·커버리지 미적용." if row.get("analysisPath") == "METADATA" else "문단·설명+코드 묶음별 키워드 점수·코사인·결합 점수와 근거 통과 여부입니다. "
                           "RELATED는 본문 관련 근거와 충돌 여부로 판단합니다. 관련 커버리지는 참고값이며, UNRELATED에는 기존 커버리지·길이 조건을 적용합니다.")
                st.dataframe(row["matches"], hide_index=True, use_container_width=True,
                             column_config={name: st.column_config.NumberColumn(format="%.4f")
                                            for name in ("cosine", "hybridScore", "keywordScore", "vectorBase", "keywordBoost")})
    with st.expander("이번 실험 설정"):
        st.json({"body": report["config"], "metadata": report.get("metadataConfig", asdict(METADATA_CONFIG))})


if __name__ == "__main__":
    main()

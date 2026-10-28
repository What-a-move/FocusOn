# FocusOn AI

현재 실행 경로는 **Kiwi 키워드 + Qwen 벡터 Hybrid 관련성 평가**다. 사용하지 않는 목표 Graph 생성 Prompt, OpenAI 생성 Client, ChromaDB 저장소, Graph 분석기와 7가지 이전 비교 경로는 2026-10-28 제거했다.

## 실행

저장소 루트에서 실행한다.

```bash
python3 -m pip install -r AI/evaluation/requirements-streamlit.txt
python3 -m streamlit run AI/evaluation/similarity_streamlit.py --server.address 127.0.0.1 --server.port 8501
```

브라우저에서 `http://127.0.0.1:8501`을 연다. 확정 목표와 추출 JSON을 넣고 **비교하기**를 누른다. Qwen API는 화면의 주소 또는 `FOCUSON_EMBEDDING_URL`로 지정한다. 실제 학교 연결은 [학교 임베딩 연결 지침](docs/features/school-ollama-embedding-SETUP.md)을 따른다.

## 현재 처리 과정

1. 목표·입력 형식, 제외·민감정보·현재 관측 문맥·추출 품질을 검사한다.
2. Kiwi가 목표·제목·본문의 명사·고유명사·기술 식별자를 정규화한다. 조사·어미·불용어를 제외하고 같은 토큰을 비교한다. 동의어·별칭·선수 개념을 생성하지 않는다.
3. 학교 Qwen API의 `/api/embed`로 목표 질의와 정제 본문·제목/소제목 문맥·설명+코드 묶음의 벡터를 받는다. 모델 메타데이터 API를 반복 호출하지 않는다.
4. 원래 본문의 코사인 유사도에 목표 키워드 적중을 보강하고, 관련·무관 근거와 커버리지를 계산한다.
5. `RELATED`·`UNRELATED`·`UNCERTAIN`, 근거, 점수, 사유, 다음 입력 출처, 처리 시간을 표시한다.

현재 점수·근거 정책과 고정 보정값은 이번 정리에서 변경하지 않았다.

- 본문: `body-context-hybrid-v3`, `kiwi-weighted-terms-v3`, `dependent-followup-v2`.
- `K = 적중한 목표 용어 가중치 합 / 전체 목표 용어 가중치 합`. 넓은 용어는 0.25, 나머지는 1.0인 제한된 규칙이다.
- `S = max(0, cosine)`, `H = S + 0.20 × K × (1 − S)`.
- 현재 합성 개발 자료에서 선택한 관련 기준은 원래 본문 V≥0.30 및 본문/문맥 H≥0.35다. 무관 후보는 본문 V≤0.25, UNRELATED는 무관 커버리지≥0.70·본문80자·완전 추출과 충돌 없음이 필요하다. 관련 커버리지 60%를 최소 판정 조건으로 쓰지 않는다.
- 메타데이터는 `title-hybrid-v3-mvp` 별도 경로다. 실제 제목 V≥0.55/H≥0.60의 RELATED만 확정하고, 정상 non-RELATED 입력은 Chrome DOM 또는 Desktop OCR로 재확인을 요청한다. 본문 길이·커버리지는 적용하지 않는다.
- 점수는 검증된 판정 confidence가 아니다. 수치의 선택·한계·합성 평가 이력은 [Hybrid 평가 보고서](docs/features/graphless-hybrid-calibration-REPORT.md)에 있다.

## 입력·출력 범위

- 단일 JSON·배열·`cases` 최대 30개, 사례당 현재 화면 observation 1개를 지원한다. 데이터셋 `goal.text`와 목표 칸이 다르면 모델 호출 전에 거부한다.
- 공개 계약의 중첩 `metadata`/`contentPayload.text` 입력을 변환한다. DOM/OCR 본문은 최대 2,000자다.
- APP_META·TAB_META → RELATED면 종료, 그 외 정상 결과는 Desktop OCR·Chrome DOM 재확인을 표시한다. Chrome viewport OCR은 P1이며 현재 입력으로도 모델을 호출하지 않는다.
- Desktop OCR에는 인식 품질 정보가 필요하다. 공개 계약에 없는 OCR 품질을 임의 상수로 채우지 않는다. 캡처·OCR 실행과 민감정보 사전 제거는 Client 담당이다.
- 내부 `templateId`·`goalProfile`은 이전 입력의 호환을 위해 받아도 점수나 목표 확장에 사용하지 않는다. 새로운 공개 필드로 요구하지 않는다.
- 기대 라벨은 판정·모델 입력에서 제외한다. 실패·분석 불가를 정답 UNCERTAIN으로 집계하지 않는다.
- 화면 원문·결과는 파일·DB에 자동 저장하지 않는다. 벡터는 해시 기반 메모리 캐시(512개/300초)에서 재사용하고 매 실행 점수와 판정을 다시 계산한다. OpenAI·Graph·ChromaDB 호출이 없다.

## 구현 상태와 검증

입력 전처리, Qwen Client, Kiwi Hybrid, Streamlit, 공개 요청 변환·응답 검증 도구가 있다. 공개 FastAPI 라우트와 실제 Backend 연결, 보정된 confidence, 운영 중복/역순 결과 적용, 이탈 집계·알림은 후속 작업이다. 이전 Graph 분석기의 캐시·단계별 callback은 제거됐으며 Streamlit의 다음 출처 표시는 실제 수집 실행이 아니다.

```bash
PYTHONPATH=AI:. python3 -m pytest AI/tests -q
```

회귀 테스트는 합성 벡터·HTTP Mock·Streamlit UI를 검증한다. 실제 Qwen 정확도·운영 성능 평가는 별도로 수행한다. 개발 자료 보정 CLI는 유지한다.

```bash
PYTHONPATH=AI:. python3 -m AI.evaluation.calibrate_graphless --help
```

## 문서

- [AI 문서 안내](docs/README.md)
- [현재 상태](docs/CONTEXT.md) / [다음 작업](docs/NEXT_TASK.md) / [결정 기록](docs/DECISION_RECORD.md)
- [현재 화면 분석 계약 경계](docs/features/screen-analysis-contract-v1-REPORT.md)
- [평가 절차](evaluation/SIMILARITY_EVALUATION_GUIDE.md)
- [공개 계약 요약](../docs/API_CONTRACT.md) / [개인정보 기준](../docs/DATA_PRIVACY.md)

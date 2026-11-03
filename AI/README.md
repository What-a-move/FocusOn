# FocusOn AI

현재 AI 영역은 **Kiwi + Qwen Hybrid 화면 관련성 분석**과 별도의 **JEV + OpenAI 목표 보조**를 포함한다. 관련성 경로의 미사용 목표 Graph와 이전 비교 코드는 제거했으며 목표 보조의 OpenAI 코드는 유지한다.

## 실행

## 이 브랜치의 목표 보조 실행 (#12)

JEV·OpenAI·LangGraph 목표 보조와 Streamlit 실행 코드가 포함돼 있다. 아래 공통 scaffold 설명은 이 기능 이외 영역의 상태와 구분한다. 공개 decision 4종과 내부 clarityStatus 6종의 변환, 실제 Backend 연결은 아직 미검증이다.

```bash
LANGSMITH_TRACING=false LANGCHAIN_TRACING_V2=false LANGSMITH_TRACE_RAW_GOAL_TEXT=false PYENV_VERSION=what-a-move PYTHONPATH=AI pyenv exec python -m pytest AI/tests -q
PYENV_VERSION=what-a-move pyenv exec python -m streamlit run AI/streamlit_app.py
```

실행에 필요한 TYPESAFE_API_KEY와 OPENAI_API_KEY는 로컬 AI/.env에만 설정한다. Laya는 비교 전용이며 실제 상태·저장을 결정하지 않는다. 세부 구현은 [현재 상태](docs/CONTEXT.md)와 [공개/내부 계약](docs/api_spec.md)을 따른다. 질문 생성은 최대 2건 병렬 실행하고, 모델 초안을 스트리밍 표시하며 유효한 응답이 없을 때 기본 10초 이후 로컬 질문을 사용한다. 공유·배포 환경에서 원문 trace opt-in을 사용하지 않는다.

## 담당 기능

Backend 전용 FastAPI는 `POST /internal/v1/analysis-runs`에서 수신을 확인하고, `POST /internal/v1/analysis-runs/analyze`에서 Kiwi + Qwen Hybrid 후보를 계산한다. 판정 confidence가 아직 보정되지 않아 공개 `decision`은 UNCERTAIN이며, `candidateDecision`은 미보정 실험값이다. [분석 경로와 한계](docs/features/fastapi-relevance-analysis-REPORT.md) 및 [실행·인증·입력 예제](docs/features/fastapi-reception-REPORT.md)를 따른다.

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

입력 전처리, Qwen Client, Kiwi Hybrid, 공개 요청 변환·응답 검증 및 내부 FastAPI 실험 분석 경로가 있다. 실제 Backend 연결, 보정된 confidence, 운영 중복/역순 결과 적용, 이탈 집계·알림은 후속 작업이다. 이전 Graph 분석기의 캐시·단계별 callback은 제거됐으며 실험 코드의 다음 출처 표시는 실제 수집 실행이 아니다.

```bash
PYTHONPATH=AI:. python3 -m pytest AI/tests -q
```

회귀 테스트는 합성 벡터·HTTP Mock를 검증한다. 실제 Qwen 정확도·운영 성능 평가는 별도로 수행한다. 개발 자료 보정 CLI는 유지한다.

```bash
PYTHONPATH=AI:. python3 -m AI.evaluation.calibrate_graphless --help
```

### #29 로컬 시연

합성 데이터로 종료 후 정리 흐름과 근거 부족·중복·제외·실패를 직접 확인한다. 입력 형식은 `pageType`, `title`, `text`, `ocr`, `extractionMethod`, `extractionStatus`, `extractorVersion`이다. 별도 최종 관련성 판정은 추출 객체와 분리해 선택한다. 같은 페이지의 서로 다른 문단도 각각 근거로 인정한다. OpenAI 버튼은 편집한 합성 DOM 입력 중 필터를 통과한 문단만 전송한다. 실제 화면 데이터는 입력하지 않는다.

```bash
python3 -m venv /tmp/focuson-summary-demo-venv
/tmp/focuson-summary-demo-venv/bin/python -m pip install -r AI/demos/requirements.txt
/tmp/focuson-summary-demo-venv/bin/python -m streamlit run AI/demos/learning_summary_streamlit.py
```

선택적 OpenAI 시험은 환경변수 → 현재 작업 폴더 AI/.env → Git 기본 작업 폴더 AI/.env 순서로 `OPENAI_API_KEY`만 확인한다. 실제 호출은 `현재 합성 DOM 근거로 OpenAI 생성` 버튼을 눌렀을 때만 일어난다. 기본 모델은 `gpt-5-mini` low 추론이며 `FOCUSON_SUMMARY_MODEL`로 바꿀 수 있다. `store=false`, 20초 제한·자동 재시도 0회다. 실제 화면 데이터는 미연결이며 제공자·보존·고지 승인은 제품 연동 시점에 진행한다.

현재 pyenv 실행(이 #29 worktree 루트에서):

```bash
PYENV_VERSION=what-a-move pyenv exec streamlit run AI/demos/learning_summary_streamlit.py --server.port 8504 --server.address 127.0.0.1 --server.headless true
```

최종 결과는 `overview`, 최대 5개의 `keyConcepts`(`name`, `briefExplanation`, `sessionEvidence`), `nextStartPrompt`뿐이며 전체 1,200자 이하다. 퀴즈와 추천 개념은 생성하지 않는다. 다음 시작 지점은 검증된 세션 개념을 이어 보는 한 문장이다.

단위 테스트:

```bash
PYTHONPATH=AI python3 -m unittest discover -s AI/tests -p 'test_learning_summary*.py' -v
```



## 문서

- [AI 문서 안내](docs/README.md)
- [현재 상태](docs/CONTEXT.md) / [다음 작업](docs/NEXT_TASK.md) / [결정 기록](docs/DECISION_RECORD.md)
- [현재 화면 분석 계약 경계](docs/features/screen-analysis-contract-v1-REPORT.md)
- [평가 절차](evaluation/SIMILARITY_EVALUATION_GUIDE.md)
- [공개 계약 요약](../docs/API_CONTRACT.md) / [개인정보 기준](../docs/DATA_PRIVACY.md)

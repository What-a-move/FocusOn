# FocusOn AI

AI 서버는 **10,168건 E5 화면 활동 분류**와 **JEV + Claude 목표 보조**를 제공한다. 화면 판정은 로컬 모델에서 실행하고 목표 보조는 해당 제공자의 API를 사용한다.

## 실행

### 목표와 페이지 관련성 비교 (#277)

로컬 평가에서 직접 입력한 목표와 페이지의 관련성을 비교한다. 같은 페이지를 목표 한 줄과 확장 목록으로 각각 판정하고, 문단별 근거와 목표 생성 시간을 확인한다. 결과 CSV에는 페이지 원문을 넣지 않는다.

```bash
python -m pip install -r AI/requirements.txt -r AI/evaluation/requirements-goal-profile.txt
PYTHONPATH=AI:. python -m streamlit run AI/evaluation/goal_profile_streamlit.py --server.address 127.0.0.1 --server.port 8511
PYTHONPATH=AI:. python -m streamlit run AI/evaluation/goal_profile_upstage_streamlit.py --server.address 127.0.0.1 --server.port 8512
```

두 실행 명령은 각각 별도 터미널에서 사용한다. OpenAI는 `OPENAI_API_KEY`, Upstage는 `UPSTAGE_API_KEY`를 로컬 `AI/.env`에서 읽는다. 비교 화면에는 합성 또는 비민감 공개 테스트 입력을 사용한다. 후보 점수와 임계값은 로컬 평가용이다.

같은 화면에서 **Jev로 평가**를 선택하면 `AI/.env`의 `TYPESAFE_API_KEY`로 목표 한 줄과 확장 목표를 각각 평가한다. **Hybrid도 함께 평가**를 끄면 TypeSafe 키 하나로 실행할 수 있으며, 이 경우 목표 확장 항목을 직접 입력한다. Jev의 기본 모델은 `jev-latest`이고 `TYPESAFE_MODEL`로 고정할 수 있다.

Jev는 직접 관련 콘텐츠와 필요한 선수 지식·문제 해결을 `RELATED`로 본다. 관련·무관 선택지 확률이 기본 0.65 미만이면 `UNCERTAIN`으로 보류한다. 제목 또는 불완전 추출로는 `UNRELATED`를 확정하지 않는다. 선택지 확률의 최솟값은 화면에서 조정할 수 있다. Jev 결과와 처리 시간은 비교 표·CSV에 표시된다. 제외·민감·추출 실패 입력은 기존 검사에서 걸러진다.

## 이 브랜치의 목표 보조 실행 (#12)

JEV·Claude·LangGraph 목표 보조와 Streamlit 실행 코드가 포함돼 있다. 아래 공통 scaffold 설명은 이 기능 이외 영역의 상태와 구분한다. 공개 decision 4종과 내부 clarityStatus 6종의 변환, 실제 Backend 연결은 아직 미검증이다.

```bash
PYENV_VERSION=what-a-move pyenv exec python -m pip install -r AI/requirements.txt
LANGSMITH_TRACING=false LANGCHAIN_TRACING_V2=false LANGSMITH_TRACE_RAW_GOAL_TEXT=false PYENV_VERSION=what-a-move PYTHONPATH=AI pyenv exec python -m pytest AI/tests -q
PYENV_VERSION=what-a-move pyenv exec python -m streamlit run AI/streamlit_app.py
```

실행에 필요한 TYPESAFE_API_KEY와 ANTHROPIC_API_KEY는 로컬 AI/.env에만 설정한다. Laya는 비교 전용이며 실제 상태·저장을 결정하지 않는다. 세부 구현은 [현재 상태](docs/CONTEXT.md)와 [공개/내부 계약](docs/api_spec.md)을 따른다. 질문 생성은 최대 2건 병렬 실행하고, 모델 초안을 스트리밍 표시하며 유효한 응답이 없을 때 기본 10초 이후 로컬 질문을 사용한다. 공유·배포 환경에서 원문 trace opt-in을 사용하지 않는다.

`AI/.env` 설정 예시(실제 키는 커밋하지 않는다):

```dotenv
TYPESAFE_API_KEY=발급받은_TypeSafe_키
ANTHROPIC_API_KEY=발급받은_Claude_키
# 선택 설정이며 생략해도 된다.
CLAUDE_MODEL=claude-haiku-4-5-20251001
```

이미 `CLAUDE_API_KEY`를 사용하고 있다면 그대로 인식한다. 두 이름이 모두 있으면 `ANTHROPIC_API_KEY`가 우선이므로 하나만 설정한다. 이 목표 보조 흐름은 Claude 설정을 사용한다. Streamlit Cloud에서는 같은 값을 앱 Secrets에 설정하고 Reboot한다. 로컬에서도 키·모델 변경 후 Streamlit 서버를 재시작한다.

## 백엔드 화면 분석

`POST /internal/v1/analysis-runs`는 검증된 현재 화면을 로컬 10,168건 E5 분류기로 판정한다. 탭·앱 메타데이터를 먼저 보내고 보류되면 Chrome DOM 또는 데스크톱 OCR 텍스트를 보낸다. 학습된 분류기의 공부 중·딴짓·보류를 기존 계약의 `RELATED`·`UNRELATED`·`UNCERTAIN`으로 전달한다. 요청의 목표 스냅샷은 세션 검증에 사용한다.

저장소 루트에서 Python 3.11 이상으로 실행한다. 최초 설치 단계에서 고정된 E5 인코더를 준비한다. 설치 이후 화면 추론은 로컬에서 실행하고, 시작 시 워커당 모델을 한 번 로드하고 예열한다. `/health`가 503이면 설치 및 무결성을 확인한 뒤 서비스를 다시 시작한다.

```bash
python3 -m pip install -r AI/requirements.txt
python3 -m AI.src.models.screen_activity.setup_encoder
PYTHONPATH=AI:. python3 -m uvicorn AI.src.main:app --env-file AI/.env --host 127.0.0.1 --port 8001 --http h11 --no-access-log
```

`AI/.env`의 `FOCUSON_AI_API_KEY`를 Backend 환경의 같은 키와 맞추고 Backend의 기존 `AI_ANALYSIS_URL`에 AI 서버 주소를 사용한다. AI에서 분류 점수 차이가 1%p 이하이거나 최고 점수가 0.60 미만이면 보류한다. 확정 판정은 기존 미보정 응답 계약에 따라 `confidence: null`과 `e5-activity-v1-uncalibrated`를 반환한다.

이전 Kiwi·임베딩 비교는 [오프라인 평가 보고서](docs/features/graphless-hybrid-calibration-REPORT.md)에 남아 있다.

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

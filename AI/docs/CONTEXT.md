# AI 작업 컨텍스트

> AI 담당자가 작업 시작 시 가장 먼저 읽는 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 담당 영역

### 2026-11-08 베타 화면 판정 현재 상태

- AI 경로는 `POST /internal/v1/analysis-runs` 하나다. 입력 검증 뒤 Kiwi + Qwen Hybrid 판정을 동기 반환하고 과거 `/internal/v1/analysis-runs/analyze`는 제거했다.
- `FOCUSON_ANALYSIS_DECISION_MODE=candidate`에서는 미보정 후보 점수가 0.75 이상일 때 후보를 `decision`으로 반환하고 `promptVersion`에 `-uncalibrated`를 표시한다. 낮거나 없는 점수는 `UNCERTAIN`이다. 이 점수는 보정된 확률이 아니다. `AI/.env.example`은 베타 테스트를 위해 candidate를 명시한다.
- 앱·탭 메타데이터의 `UNRELATED`는 `UNCERTAIN`으로 유지하고 글자 기반 보완을 요청한다. 모델 실패는 `UNCERTAIN` / `ANALYSIS_UNAVAILABLE`; 제외·민감 입력은 모델 호출 전 409 차단이다.
- Desktop OCR은 `contentPayload.text`만으로 처리하며 `ocr.confidence`·`captureStatus` 누락 시 값을 만들어 넣지 않는다.
- `/health`는 실제 임베딩 요청과 2560차원 검증 결과를 알린다. 최신 dev의 Java Backend에는 AI API 호출 코드가 있지만 실제 모델·제품 알림 왕복은 미검증이다. AI와 Backend는 숫자 confidence 0.75 미만을 UNCERTAIN으로 처리한다.
- 학교 Ollama의 합성 HTTP 40건 p95는 2.32초였고, DOM 20건 p95는 3.76초였다. DOM 경로의 3초 목표는 미달이며 동시 부하 및 제품 전체 성능은 검증되지 않았다. 실측 범위는 [SETUP 보고서](features/school-ollama-embedding-SETUP.md)를 따른다.

- 실제 HTTP FastAPI→Tailscale→Qwen 순차 합성 40건에서 p95 2.32초, 최대 4.04초, 3초 초과 2건이었다. DOM 20건 p95는 3.76초로 목표 미달이다. 측정 범위·제한은 학교 Ollama SETUP의 실측 절을 따른다.

- 2026-11-03 사용자 요청으로 AI용 Docker 배포 구성과 이미지를 제거했다. 배포 백엔드 호스트에서 학교 PC로 Tailscale 연결을 설정하고 실제 FastAPI 실행 위치와 호출 경로를 확인해야 한다.

- 학습 목표와 화면·페이지 정보의 관련성 판단
- OCR·페이지 텍스트 정제와 입력 품질 판단
- 페이지 이동·체류 시간을 이용한 목표 이탈 및 학습 흐름 분석
- MediaPipe가 클라이언트에서 계산한 상태값의 보조 분석
- 분석 결과의 상태값·점수·신뢰도 반환
- AI API 입력·출력 계약 관리
- 평가 데이터와 모델·프롬프트 품질 확인

## 기능별 PR 상태와 충돌 해소 기준

이 문서는 목표 보조 PR [#137](https://github.com/What-a-move/FocusOn/pull/137)과 학습 정리 PR [#138](https://github.com/What-a-move/FocusOn/pull/138)의 구현 상태를 함께 기록한다. 각 기능의 코드는 해당 PR 브랜치에 있으며 한쪽 브랜치가 다른 쪽 실행 코드까지 포함한다는 뜻은 아니다. 두 PR은 최신 dev를 각각 반영했고, 이 공통 문서는 동일하게 유지해 병합 순서에 따른 충돌을 피한다.

- 공개 목표 decision 4종·화면 개입 v1·Client별 OCR·개인정보 계약은 최신 dev 기준을 보존한다. 2026-11-03 Notion 조회는 404였으므로 새 제품 계약을 확정하지 않았다.
- #12 내부 clarityStatus 6종과 공개 decision 4종의 변환, 목표 길이 200자 내부 설정과 제품 2~40자의 정합성, 서비스 인증·Backend 연결은 후속 검증이다.
- #29는 합성 DOM 기반 생성 코어와 Streamlit 개발 시연 단계다. Desktop 개발 데모는 이 PR에서 제거했다. 실제 수집·최종 관련성·Server Worker/저장·제품 조회 통합 완료가 아니다.
- 원문 trace opt-in은 #12의 로컬 비민감 수동 테스트에 한정한다. 제품 로그 정책이나 실제 사용자 데이터 전송 허가로 사용하지 않는다.

### 목표 보조 #12 브랜치 구현 이력

- 2026-11-03 목표 보조 생성 제공자를 Claude로 변경했다(GA-038). 로컬 `CLAUDE_API_KEY`를 그대로 인식하고 Streamlit·FastAPI가 같은 어댑터를 사용한다. 합성 목표의 질문 스트리밍·프로필·추천 실제 호출은 모두 Schema 검증을 통과했다. JEV 판단값·Router·제품 API 계약은 변경하지 않았다. 기존 ChatOpenAI의 자동 생성 trace는 제거됐고 JEV·Laya 비교 trace는 유지한다.
- Claude 전환 검증: what-a-move에서 `pytest AI/tests -q --ignore=AI/tests/openai_test.py` 87개 통과(6.35초), 기존 Offline Router 15건 오류 0건. tracing을 끄고 실행했다. 미추적 `openai_test.py`는 import 시 외부 호출을 하므로 사용자 파일을 유지한 채 제외했으며 커밋하지 않는다.
- PR #160 충돌 해소: 최신 dev의 관련성 라우트와 버전 고정 의존성을 유지하고 생성 어댑터 교체 위치를 `src/api/goal_routes.py`로 옮겼다. 학습 정리의 별도 OpenAI 실험과 `demos/requirements.txt`는 변경하지 않았다.
- dev 동기화 후 최종 회귀: 같은 명령으로 **405 passed, 27 subtests passed(14.70초)**, Offline Router 15건 오류 0건. 처음 확장 실행은 로컬 Kiwi 의존성 미설치로 실패했으며 dev에 명시된 `kiwipiepy==0.23.1` 설치 뒤 전체 통과했다. PR은 [#160](https://github.com/What-a-move/FocusOn/pull/160)이며 dev 대비 수정은 AI 19개 파일로 제한했다.

- 이번 필드 정합성 변경은 `LANGSMITH_TRACING=false LANGCHAIN_TRACING_V2=false LANGSMITH_TRACE_RAW_GOAL_TEXT=false PYENV_VERSION=what-a-move PYTHONPATH=AI pyenv exec python -m pytest AI/tests -q`로 73개 통과를 확인했다. 모델은 Fake/Mock으로 검증했고 Backend·실제 모델 통합은 미검증이다. 최초 실행에서 로컬 `.env`의 tracing 설정으로 LangSmith 연결 실패 로그가 발생해 이후 검증에서는 tracing을 껐다. `.env` 값은 변경하지 않았다.
- 2026-10-25 #12 연동 필드: AI 내부 요청이 `goalText`, `conversationTurn`, `clarificationSessionId?`, `previousAnswers[{question,answer}]`를 수신·직렬화하고 정상 응답에 대화 ID를 그대로 반환한다. 추적용 내부 `requestId`와 이행용 이전 입력 별칭은 유지한다. 회차 0~2와 답변 수 일치·최대 2개를 검사하며 두 답변 뒤 직접 질문·Repair fallback 질문 모두 추가 생성하지 않는다. AI HTTP 경로의 SDK 재시도는 0이다. Backend의 공개 경로·ID 발급/소유권·목표 생성 연결·1초→3초 재시도는 미구현이며 해당 담당자의 후속 작업이다. 이번 기준은 사용자가 전달한 팀 메시지이며 Notion 연결 도구는 404로 최신 본문을 재확인하지 못했다.
- Issue #12에서 JEV 판단, Claude 구조화 생성, LangGraph 분기·1회 Repair를 결합한 학습 목표 설정 보조를 구현했다.
- `POST /internal/v1/goals/clarify`와 camelCase Pydantic 계약, 공통 422·502·503 오류 응답을 구현했다.
- 6개 `clarityStatus`, 후보별 JEV 검수, 전체 재평가, fallback, 로그 개인정보 제외를 Fake 기반 테스트로 검증했다.
- Offline 평가 Fixture와 명시적으로만 실행되는 JEV Live 평가 경로를 추가했다.
- 실제 JEV·Claude Workflow를 수동 확인하는 로컬 Streamlit 테스트 화면을 추가했다. 최초 `목표 저장`은 분석만 시작하고 `CLEAR` 결과의 최종 확인 후에만 현재 테스트 세션 메모리에 저장하며, 제품 저장소나 학습 세션은 변경하지 않는다.
- 로컬 목표 보조는 TypeSafe 공식 API의 `TYPESAFE_API_KEY`로 JEV 판단을 호출하고, Claude 공식 API의 `ANTHROPIC_API_KEY`로 구조화 생성을 호출한다. Vercel AI Gateway 설정과 우회 경로는 제거했다.
- Streamlit은 1차 JEV 판단을 먼저 표시하고, 사용자가 다음 단계 생성 버튼을 누를 때만 Claude 질문·추천·GoalProfile 생성과 후속 JEV 검수를 실행한다. 후보별 JEV 검수와 실패 후보 Repair는 최대 3개를 병렬 실행한다. 확인 질문은 Claude 생성 요청을 기본 2건 병렬 실행해 먼저 도착한 유효한 질문 하나만 사용하고, 이미 답한 질문은 제외한다.
- 질문 생성은 Streamlit에 실제 Claude 문구 초안을 스트리밍 표시한다. 5초는 성능 목표이며, 유효한 모델 질문은 즉시 사용하고 로컬 기본 질문은 질문 생성 시작 후 10초 이상 지났을 때만 사용한다. 두 호출이 일찍 실패하거나 기존 질문만 반복해도 기본 질문을 조기 표시하지 않는다. 설정·인증 오류는 바로 표시한다. 이전 OpenAI의 합성 목표 1회 Live 확인에서 첫 초안 1.933초, 최종 질문 2.748초였으나 최초 JEV 판단·화면 렌더링을 포함한 P95 지연과 실제 비용은 검증되지 않았다.
- Streamlit에서 질문에 답하면 답변 문구가 목표 입력값에 반영되고, 해당 문장으로 다시 판단한다. 목표 입력은 계속 편집할 수 있으며 검증된 `CLEAR` 결과를 사용자가 최종 확인하기 전에는 저장하지 않는다.
- Streamlit의 JEV 판단 결과는 확률·구체성 수치만 있는 JSON 대신 판단 항목, 값, 한국어 설명이 나란히 표시되는 표로 요약하며 원본 JSON은 펼침 영역에 유지한다. `CLEAR`의 최종 확인 정보가 생성된 뒤에도 같은 판단 표를 계속 표시한다.
- 목표 명확성 Router는 최초 구현의 4개 JEV 질문과 Score Threshold를 사용한다. 강한 `INVALID`·복수 목표·불명확 용어 판정 후 `is_usable_goal` 0.60, 구체성 신뢰도 0.45, 구체성 점수 2.00/1.00 기준으로 `CLEAR`·추천·질문으로 분기한다.
- 최초 JEV 판단의 영어 instructions와 criteria, snake_case state(`goal_text`, `selected_goal_text`, `clarification_answers`)를 복원했다. 질문 답변에 주제·활동·범위가 있으면 최종 GoalProfile과 추천 후보에 반영한다.
- Claude 공식 SDK `messages.parse`·`messages.stream`을 사용한다. 기본 모델은 `claude-haiku-4-5-20251001`, 최대 1,200 출력 토큰이며 OpenAI 전용 설정과 생성 의존성을 제거했다. `CLAUDE_API_KEY`도 인식하며 표준 `ANTHROPIC_API_KEY`가 우선이다.
- Streamlit 목표 입력 시 로컬 `convaiinnovations/laya-multilingual`도 JEV와 동일한 state·네 typed question으로 비교 판단한다. Laya는 비교 표에만 쓰고 실제 상태, Workflow, GoalProfile, 저장에는 연결하지 않는다. 최초 비교는 패키지 설치와 공개 체크포인트 다운로드가 필요하다.
- LangSmith 로컬 수동 테스트는 `LANGSMITH_TRACE_RAW_GOAL_TEXT=true`일 때 목표 원문·선택 목표·후속 답변을 표시한다. `goal_assessment_comparison` 부모 trace 아래에 JEV와 Laya 판단을 함께 남기며, 각 모델 output에는 현재 Router 상태가 포함된다. 이 설정은 로컬 테스트 외 환경에서는 꺼야 한다.
- 2026-09-28 로컬 Live 확인에서 Laya 호출·응답 매핑은 정상이나, 단일 목표 `React Query 캐시 무효화 구현하기`와 `Spring Security JWT 인증 구현하기`도 복수 목표 Noul이 각각 86.57%, 97.12%로 나왔다. 현재 기본 모델의 목표 분류 zero-shot 결과는 원시 실험값으로만 표시하며 JEV 확률과 비교하거나 정책에 사용하지 않는다.

### 학습 정리 #29 브랜치 구현 이력

- 2026-10-27 공개 DOM 단건 평가: 사용자 지정 Databricks MemCurrent 글을 브라우저 DOM에서 직접 추출했다. 본문 14,816자에서 캡션 등을 제거한 11,790자에 현재 전처리 규칙을 적용한 결과 120자 초과 문장 39개가 탈락하고, 후보 80개 중 앞 20개(1,136자)가 선택됐다. 핵심 episodic/derived memory 정의와 검색·조회 도구 설명이 누락됐다. 공개 글임을 표시한 별도 OpenAI 1회 응답은 8.29초 completed였으나 영문 근거에 대한 한국어 개념명이 문자열 기반 검증을 통과하지 못해 최종 FAILED였다. 제품 코드·전송 설정은 변경하지 않았다. 원문/거절된 출력은 보관하지 않고 `AI/evaluation/learning_summary_public_dom_2026-10-27.json`에 메타데이터만 기록했다.
- 2026-10-27 추가 평가: 서로 다른 합성 DOM 8건(각 허용 근거 3개)을 `learning-summary-mvp-v3`·gpt-5-mini low로 각 1회 호출했다. 8/8 READY, 개별 4.87~12.49초·평균 7.75초, 동시 2건 묶음 총 31.20초, 재시도 0. 공개 세 필드·최대 5개·1,200자 한도는 통과했지만 Python의 근거 밖 메모리 효율 보충, Git의 가능성 표현 단정·상세 지식 보충, 통계 개요의 행동 권유가 보였다. 의미 정확도 100%로 해석하지 않는다. 입력·전체 출력·검토는 `AI/evaluation/learning_summary_eight_results_2026-10-27.md`와 JSON에 기록했으며 생성 코드는 변경하지 않았다.
- 2026-10-27 #29 MVP 형식을 재확인했다. 공개 결과는 `overview`, `keyConcepts[{name,briefExplanation,sessionEvidence}]` 최대 5개, `nextStartPrompt` 한 문장으로 고정하고 전체 1,200자 한도를 유지한다. 퀴즈 `reviewQuestions`와 별도 추천 `recommendedConcepts`는 추가 필드로 거절한다. 생성 모델은 개요·개념의 내부 근거 연결만 반환하고 다음 시작 지점은 검증된 세션 개념을 재확인하는 한 문장으로 코드에서 만든다. 원문 복사 강제 대신 짧은 확인 내용을 사용한다.
- 이전 임시 worktree의 소스·Git 연결이 사라져 `586965a`에서 #29 브랜치를 `.handoff/learning-summary`에 복구했다. 이전 미커밋 작업을 모두 복원했다고 간주하지 않는다. 환경변수 또는 현재/기본 작업 폴더 AI/.env에서 키만 읽는 연결도 복구했다. #12 변경은 건드리지 않았다. 8503은 다른 프로젝트이므로 종료하지 않고 8504로 실행했다.
- 검증: `PYENV_VERSION=what-a-move PYTHONPATH=AI pyenv exec python -m unittest discover -s AI/tests -p 'test_learning_summary*.py' -v` 23개 통과. 합성 기본 입력으로 `gpt-5-mini` low 추론을 1회 호출해 5.77초 READY, 최종 세 필드와 근거 안의 다음 시작 문장을 확인했다. 로컬 Streamlit 8504 health=ok. 의미적 사실성 전체·실제 DOM 추출·Backend 연결 검증은 아니다.

## 현재 상태

### 완료된 작업

- AI 기본 디렉터리와 평가 디렉터리가 생성되어 있다.
- AI 전용 문서 구조와 기능별 템플릿을 구성했다.
- 공통 API 응답 초안은 루트 `docs/API_CONTRACT.md`에 정의되어 있다.
- 현재 활동 관련성 API, 분석 기준, Prompt 가이드를 초안으로 작성했다.
- 관련성, 학습 흐름, MediaPipe 사용자 상태를 분리된 기능으로 개발하는 방향을 정리했다.
- 현재 Notion의 목표 보조 4개 판정값, Client별 OCR 경로, `EXCLUDED`·`PRIVACY_BLOCKED` 분석 상태를 문서에 반영했다. 최근 7일 개인화 휴식은 P1 후보 초안으로 분리했다.
- Issue #3에서 AI 개발 불변 규칙, 다중 상태 모델, 데이터 수명, 평가 기준 문서 초안을 작성했다.
- Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로, ColPali 시각 콘텐츠 비교 실험 경로를 문서로 구분했다.
- 목표·논리 세션·회차·이벤트·시간 집계 계약을 `goal_session_spec.md`로 분리했다.
- 콘텐츠 감지·Chrome 내부 OCR·Desktop Apple Vision OCR·ColPali 입력 경계를 `content_acquisition_spec.md`로 분리했다.
- FocusSessionAgent, 피드백·개인화, 비동기 근거 기반 노트의 상세 계약 초안을 추가했다.
- Issue #16에서 PR #15의 최신 문서를 기준으로 MVP 이탈·카메라 정책, OCR `text`·`ocr` 입력, Hybrid 120건 팀원 보고를 문서에 반영했다. 이 문서화는 관련성·카메라 기능 구현 또는 팀의 최종 승인이 아니다.
- 2026-10-24 사용자 확정과 최신 화면 분석 문서를 기준으로 개입 `v1`의 연속/반복 조건·UNCERTAIN 구간 종료·5분 cooldown, 학습 정리 개념 객체 형식을 문서에 반영했다. Notion AI 분석 계약의 이전 수치와의 차이는 `docs/NOTION_SYNC_AUDIT.md`에 남겼으며 구현 완료를 뜻하지 않는다.

### 진행 중인 작업

- 루트 `docs/ARCHITECTURE.md`의 Desktop 중심 흐름과 Notion의 Extension 중심 기본안 차이 검토
- Server·Extension·공유 타입 담당자와 다중 상태 및 API 경로 검토
- 목표·세션·회차·이벤트 관계와 사용자당 동시 활성 회차 정책 검토
- Agent 도구 Schema·예산·Checkpoint 저장소 검토; 화면 판정 피드백 적용은 MVP 밖의 후속 기능
- 학습 노트 Queue·근거 Schema·재생성·삭제 정책 검토
- 기능별 PLAN·ERROR·REPORT와 GitHub Issue·PR 역할 정리
- ColPali 실행 위치와 개인정보·자원 예산 검토
- 카메라 MVP 로컬 분석과 AI 서버 입력 경계 확인

### 공통 통합에서 남은 작업 (#12·#29 내부 구현과 구분)

- #12 이외 AI 기능의 서버 진입점 및 제품 배포 연결
- 공개 계약과 내부 Schema 변환·서비스 인증·통합 검증
- OCR·페이지 텍스트 정제와 품질 판단 구현
- 규칙 기반 관련성 판단 구현
- 임베딩 기반 유사도 판단 구현
- LLM Fallback 구현
- Server 연동
- 성능·정확도 평가 자동화
- 목표·세션·회차·이벤트·집계 Schema 구현
- FocusSessionAgent 상태 그래프와 제한 도구 구현
- 화면 판정 피드백 저장·캐시·알림·집계 무효화 구현 (후속 기능, MVP 제외)
- #29 생성 코어의 Server 비동기 작업·저장·삭제 전파 연결
- 페이지 이동·체류 시간 기반 학습 흐름 분석
- MediaPipe 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동

### 확인된 문서·코드 차이

- `packages/shared-types`는 Notion 기준 공개 상태 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 정의한다. 내부 AI 세부 상태는 API 경계에서 변환한다.
- Extension Manifest에는 아직 `activeTab`, `nativeMessaging` 권한이 없고 Content Script·Service Worker는 골격 상태다.
- Server에는 공통 응답·Access JWT·인증 5개 테이블의 Entity·Repository와 Google 교환·`auth/me`·요청 인증·초기 토큰 발급(Issue #49, PR #51, dev 병합 전)이 있다. 목표·세션·이벤트·AI 연동 코드는 없으며 refresh·logout·pairing API는 후속이다.
- #12 브랜치에는 목표 보조 API·모델·Workflow·테스트가, #29 브랜치에는 합성 DOM 학습 정리 코어·테스트가 있다. 다른 AI 모듈과 제품 통합의 구현 여부는 현재 브랜치의 실제 코드에서 확인한다.
- 목표 보조 #12와 학습 정리 #29는 별도 PR이다. 각 브랜치의 검증 결과를 제품 전체 또는 다른 브랜치의 완료 상태로 해석하지 않는다.
- 팀원 보고 Hybrid 99/120, Macro F1 0.732의 원자료·결합식·독립 holdout은 확인되지 않았다. 현재 `dev`에는 Hybrid 관련성 구현이 없다.
- 루트 아키텍처는 Desktop 화면 분석을 기본으로 설명하고, 최신 AI 기획은 Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR을 Client별로 분리해 제안한다. 팀 합의 후 공통 문서를 갱신해야 한다.

## 현재 기술

- 언어: Python
- API 서버: #12 내부 FastAPI 구현, #29 제품 API 어댑터 미연결
- Schema 검증: #12·#29 Pydantic 사용, 공개 계약 변환 별도
- 테스트: #12 pytest, #29 unittest·Streamlit AppTest
- 모델: 임베딩 모델과 LLM 공급자 검토 필요
- 분석 대상: 화면·페이지에서 추출한 텍스트와 메타데이터
- OCR: Chrome Extension 내부 로컬 OCR과 macOS Desktop Apple Vision OCR의 독립 경로 제안; AI는 허용된 정제 텍스트만 평가
- 시각 분석: ColPali는 PDF·Canvas·이미지·슬라이드 대상 비교 실험 단계
- 카메라 분석: 선택 동의 기반 MVP 로컬 기능; 원본은 AI 서버로 보내지 않고 최소 상태 결과만 사용
- 원본 저장: 원본 화면·카메라 영상은 기본 저장하지 않음

## 예상 데이터 흐름

```text
Client별 사전 제외·권한 검사
  → Chrome DOM 우선 추출 → 부족할 때 Extension 내부 로컬 OCR
  → macOS Desktop 허용 화면 → Apple Vision OCR
  → 개인정보·품질 검사
  → Spring Server 인증·소유권·최신 버전 검증
  → AI 텍스트 정제
  → 규칙 기반 판단
  → 임베딩 판단
  → 애매한 경우 LLM 또는 FocusSessionAgent 판단
  → 관련성·흐름·행동 상태 분리
  → Server 결과 저장
  → Extension·웹 대시보드 상태·알림·기록 표시
```

## 반드시 지켜야 하는 조건

- 입력 데이터에 비밀번호·결제 정보·개인 메시지가 포함되지 않도록 확인한다.
- 원본 화면·카메라 영상을 기본 저장하지 않는다.
- OCR 실행과 원본 이미지 처리는 AI 서버 담당 범위에 포함하지 않는다.
- Server 또는 외부 ColPali로 원본 이미지를 전송하려면 현재 개인정보 결정 변경이 먼저 필요하다.
- 관련성 분석 API에는 체류 시간과 MediaPipe 상태값을 섞지 않는다.
- 학습 흐름과 사용자 상태는 별도 분석 기능으로 구현한다.
- 추출·분석·관련성·흐름·행동 상태를 서로 다른 필드로 관리하는 안을 검토한다.
- `OFF_TASK`만으로 `DRIFT_RISK`, 알림 또는 차단을 결정하지 않는다.
- 판단이 어렵거나 실패한 경우 목표 무관이나 이탈로 단정하지 않는다.
- 규칙이나 임베딩으로 충분히 판단 가능한 요청에는 LLM을 호출하지 않는다.
- 모델·프롬프트·임계값 변경은 `DECISION_RECORD.md`에 기록한다.
- 분석 결과에는 최소한 상태값과 신뢰도 기준을 포함한다.
- 오래된 목표·회차·탭 결과는 적용 전에 폐기한다.
- 같은 이벤트·세션 명령·노트 작업의 중복 처리는 멱등해야 한다.
- 목표 문장 수정은 화면 판정 정정 피드백으로 저장하지 않는다. 화면 판정 수정·삭제·개인화는 MVP에서 제공하지 않는다.
- 학습 시간은 Server가 계산하고 LLM이 추정하지 않는다.
- 학습 노트는 관찰 근거 없이 이해·해결 완료를 주장하지 않는다.
- 회차 종료와 시간 저장은 학습 노트 생성 성공에 의존하지 않는다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 작성한다.
- `EXCLUDED`·`PRIVACY_BLOCKED`는 공개 `relation`의 비분석 상태로 처리하고 콘텐츠 payload를 AI에 보내지 않는다. 확인된 `EXCLUDED`에서 별도 동의·권한이 유효하면 로컬 카메라 분석만 허용하지만 집계·전송은 별도 승인 전 금지한다. 최신 제외 상태 확인 불가 또는 `PRIVACY_BLOCKED`에서는 화면·카메라 분석을 중지하고 타이머를 유지한다.
- 목표 보조는 사용자의 최종 확인 전 확정 목표로 사용하지 않는다.

## 작업 종료 시 갱신

- 완료·진행 중·미착수 작업
- 입력·출력 계약 변경
- 모델·프롬프트·임계값 결정
- 발생한 오류와 평가 결과
- 다음 작업과 선행 조건

## Issue #11 AI 구현 상태 (PR #43)

### 2026-11-03 dev 통합

- 목표 보조 PR #137의 실행 흐름과 화면 관련성 실험 경로를 보존한다. 목표 생성은 GA-038에서 Claude로 교체했다. FastAPI에 두 라우터를 연결하고 공통 스키마와 의존성을 통합했다. 목표 보조 내부 API의 서비스 인증과 공개 응답 변환, Backend 연동은 여전히 후속 검토 대상이다.
- 화면 관련성 UI만 제거했으며 목표 보조 Streamlit 테스트 화면은 유지한다. 실제 모델·Backend 왕복은 이번 통합 검증에 포함하지 않는다.
- 별도 임시 Python 환경에서 두 기능의 전체 회귀 363 passed, 16 subtests passed. 합성 입력·Mock 기반이며 실제 외부 모델 호출은 하지 않았다.

### 2026-11-03 FastAPI 실험 분석

- `POST /internal/v1/analysis-runs/analyze`에서 기존 입력 검증 뒤 Kiwi + Qwen Hybrid를 실행한다. `FOCUSON_ANALYSIS_DECISION_MODE=candidate`이면 후보를 decision으로 반환하고 promptVersion에 `-uncalibrated`를 붙인다. 메타데이터 무관 후보는 UNCERTAIN, 분석 실패는 UNCERTAIN/ANALYSIS_UNAVAILABLE이다. 미보정 confidence는 본문 RELATED에 `0.7 * decisionScore + 0.3 * relatedCoverage`, 본문 UNRELATED에 `unrelatedCoverage * (1 - relatedCoverage)`, 메타데이터 RELATED에 decisionScore를 쓴다. 점수 누락 시 null이며 베타 후보를 강등하지 않는다. 기본 safe 모드는 보정 confidence 누락 시 UNCERTAIN이다. 정식 수신 경로와 서버 소비 방식의 연동은 후속 작업이다. [상세](features/fastapi-relevance-analysis-REPORT.md).
- 합성 벡터 기반 전체 회귀 289 passed, 16 subtests passed. 실제 학교 Qwen은 Tailscale 호스트 DNS 실패로 호출하지 못했다. Backend 연동·confidence 보정은 남았다.

### 2026-11-03 Streamlit 화면 제거

- 사용자 요청으로 Streamlit 화면 및 UI 전용 테스트/의존성을 제거했다. 기존 Hybrid 분석 runner는 `evaluation/analysis_runner.py`로 보존했고 관련 테스트와 보정 CLI 참조를 갱신했다. FastAPI는 기존 수신·검증 경로이며 Backend 연결 및 분석 실행은 후속 작업이다.
- Kiwi 0.23.1을 시스템 Python과 프로젝트 Python에 설치하고 AI requirements에 명시했다. 프로젝트 Python의 AI 회귀는 284 passed, 16 subtests passed다. 실제 Qwen/Backend 왕복은 이번 회귀에 포함되지 않는다.


### 2026-10-29 FastAPI 수신

- `POST /internal/v1/analysis-runs`의 서비스 인증·수신·기존 Schema/Adapter 연결·목표/관측 대조·활성/제외/민감정보 검사를 구현했다. 응답은 `200 RECEIVED`, `analysisExecuted: false`. 저장·Queue·모델 호출은 없다. Client 공개 컬럼은 유지한다.
- 내부 request/goal/context/extractionStatus/선택 ocr envelope는 Backend 합의가 필요하다. `FOCUSON_AI_API_KEY` 필수. 실행법과 합성 예제는 `features/fastapi-reception-REPORT.md`를 따른다.
- 새 수신+화면 계약69건 통과. 실제 임시 Uvicorn HTTP에서 health/수신/OpenAPI200·미인증401 확인 후 종료했다. 전체 회귀는209 passed,75 failed(기본 환경 Kiwi 미설치),1 skipped,16 subtests passed다.
- 아래 10/28 이전의 "HTTP route 없음"은 이 작업 전 이력이다. 실제 Backend·분석기 연결과 공개 판정 응답은 계속 미완료다.

- 이 PR에는 AI 내부 입력 검증·전처리·관련성 분석, Streamlit 평가 경로, 공개 계약 변환/응답 검증 코드가 포함되어 있다. 실제 Server HTTP route와 Client 수집부터 결과 적용까지의 운영 왕복은 연결되지 않았다.
- 현재 실행 경로는 `graphless_hybrid`와 `streamlit_runner.compare_graphless`의 Kiwi + Qwen Hybrid다. 2026-10-28 미사용 Graph 분석기·생성 Prompt·OpenAI Client·ChromaDB 저장소·이전 7방식 비교/평가 CLI를 제거했다. 공용 벡터 수학은 `src/analysis/vector_math.py`로 분리했다. 고정 retrieval 지침·점수·임계값·입력/기대 라벨은 변경하지 않았다.
- 최신 전달 계약 기준 메타데이터는 RELATED만 확정한다. 그 외의 정상 관측은 Chrome DOM 또는 Desktop OCR로 확인하고, Chrome viewport OCR은 P1로 보류한다. DOM/OCR text 한도는 2,000자다. Client 캡처·민감정보 제거와 Server의 실행 문맥 제공은 각 담당 영역이다.
- 공개 응답 confidence 보정/독립 검증, 실제 Backend·Client 연결 및 비동기 결과 순서 검증은 남아 있다. 상세 범위와 기존 점검 기록은 `features/screen-analysis-contract-v1-REPORT.md` 및 `NEXT_TASK.md`를 따른다.

- Graph 분석기에만 있던 운영 후보의 이벤트 캐시/단계별 수집 callback은 현재 실행 경로로 이식하지 않았다. 현재 평가의 해시 기반 벡터 재사용과 제품 이벤트 멱등·역순 결과 적용은 별개다. Backend 통합 시 후자를 구현·검증해야 한다. 과거 Graph 평가 결과는 이력으로 남긴다.

### 2026-10-28 Graph 미사용 코드 정리 검증

- 임시 Python 3.11 환경의 전체 AI 회귀: 262 passed, 16 subtests passed. 실제 Qwen/OpenAI 요청 없이 HTTP Mock·합성 벡터·Kiwi·Streamlit UI를 검사했다.
- 기존 고정 48건을 삭제 전 HEAD와 정리 후 코드에 동일한 합성 벡터로 실행했다. 시간/삭제한 Graph 진단 필드를 제외한 라벨·점수·근거·커버리지·임베딩 입력 해시가 모두 동일했다. 실제 모델 정확도 재평가를 뜻하지 않는다.
- 기존 임시 Streamlit 의존성 경로의 소스가 없어 첫 UI 수집에 실패했고, 저장소 requirements로 별도 임시 venv를 구성해 전체 검사를 통과했다. 기존 .env·DB·LangSmith 파일은 변경하지 않았다.

### 2026-10-28 공유 입력 컬럼 정합성 점검

- Client → Server 기준은 `packages/shared-types/src/index.ts`의 `AnalysisRunRequest`다. AI는 `guardStatus` 필수(nullable), `sequence >= 1`, nullable `windowTitle`을 반영하며 내부 정규화 시 제목 null은 빈 문자열로 바꾼다. DOM/OCR 성공 요청은 본문 텍스트를 요구한다.
- `contentType`은 공개 요청에 없어서 Adapter가 출처 기반으로 `CHROME_DOM → WEB_PAGE`, 그 외 `UNKNOWN`으로 설정한다. `qualityScore`/`contentHash`는 전처리 산출값이지 Client 입력 컬럼이 아니다.
- Server 전용 `runId`·목표 버전·탐색/세션 문맥은 `VerifiedContext`로 들어오도록 설계되어 있으나 실제 Server route는 연결되지 않았다. Desktop OCR의 `ocr.confidence`·`captureStatus` 및 `extractionStatus` 전달 위치도 담당자 합의/연동이 남았다.
- 2026-10-28 회귀: 화면 계약 47 passed; Kiwi 평가 모듈 2개를 제외하면 158 passed, 1 skipped, 16 subtests passed. 전체 실행은 환경에 `kiwipiepy==0.23.1`이 없어 해당 75건이 실패했고, 임시 설치도 네트워크 제한으로 불가했다. 실제 모델/API 왕복은 하지 않았다. 상세는 `features/screen-analysis-contract-v1-REPORT.md`를 따른다.

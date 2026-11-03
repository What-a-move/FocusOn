## 병행 진행: 학습 정리 Issue #29

- 공개 영문 DOM 평가에서 확인한 우선 개선: 120자 초과 문장을 통째로 버리는 처리, 문서 앞부분 20개만 선택하는 처리, 표 제목·헤딩 조각의 근거 인정, 영문→한국어 개념명의 문자열 포함 검증을 재설계한다. 핵심 정의 보존·문맥 연결·다국어 근거 검증을 함께 평가하고 기존 민감값/근거 밖 생성 방어를 유지한다. 공개 글 단건 실험은 실제 사용자 화면 외부 전송 승인이나 Extension 연동 완료를 의미하지 않는다.
- 8건 평가 후속: `AI/evaluation/learning_summary_eight_results_2026-10-27.md`에서 Python의 근거 밖 설명 보충, Git의 가능성→단정 변화, 통계 개요의 권유 문장, 정형적인 다음 시작 문장을 검토한다. 이번에는 각 1회·총 8회 호출과 기록만 했으며 Prompt/생성 코드 수정은 하지 않았다. READY 8/8을 의미적 사실성 통과율로 오해하지 않는다.
- 2026-10-27 최종 형식은 `{overview,keyConcepts[],nextStartPrompt}`로 고정했다. 복습 퀴즈·별도 추천 개념을 MVP에 추가하지 않는다. 다음 시작 지점도 검증된 세션 개념 안에서만 안내한다. 코드 변경 시 추가 필드 거절·최대 5개·전체 1,200자·근거 밖 개념 배제를 회귀 검사한다.
- 복구한 실행 폴더: `/Users/kshi3430/what-a-move/FocusOn/.handoff/learning-summary`. 실행은 `PYENV_VERSION=what-a-move pyenv exec streamlit run AI/demos/learning_summary_streamlit.py --server.port 8504 --server.address 127.0.0.1 --server.headless true`. 키는 환경변수 → 현재 AI/.env → Git 기본 작업 폴더 AI/.env 순서다. 8503의 다른 프로젝트와 혼동하지 않는다.
- 이번 검증은 자동 23개·합성 OpenAI 1회 READY(5.77초)다. 실제 DOM·#11 관련성·Server·개인정보 승인·Desktop '이어서 집중하기' 제품 연결과 통합 검증은 후속 작업이다. 시연의 합성 입력 버튼은 제품의 수동 재생성 기능이 아니다.
- Branch: `feat/29-ai-learning-summary` (최신 `origin/dev`에서 분리; #12의 미커밋 변경과 격리)
- 현재 상태: Streamlit에서 DOM 계약형 합성 입력(pageType, title, text, ocr, 추출 상태·방식·버전)을 편집하고, RELATED 페이지의 서로 다른 문단을 허용 근거로 만드는 로컬 전처리를 구현했다. 사용자가 선택한 경우 해당 합성 근거를 OpenAI에 보낸다. 이전 형식 탐색 3회 뒤 출력 한도를 올려 재호출했고 기본 합성 DOM 입력에서 READY 결과를 확인했다.
- 다음 단계: 성공 응답과 근거 검증을 다양한 합성 DOM 입력으로 평가하고, 실제 Extension 추출 구현 시 제안 JSON 형식을 담당자와 대조한다. 이후 Server·AI 내부 요청 Schema와 서비스 인증을 담당자와 확정한 뒤 FastAPI 어댑터를 연결한다. Server 세션 ENDED/소유권/동의·30초 입력 마감·단일 Worker·20초 Timeout·1분/5분 재시도·보존/삭제를 구현하고 Desktop 조회 UI를 연결한다.
- 선행: #11 최종 관련성 결과, Server 세션/인증, Extension/Desktop 제외·추출 경로. Notion 기획은 `REVIEW`이며 미합의 API/ERD를 확정으로 취급하지 않는다.
- 후속 모델 선택: DOM 추출·실제 화면 데이터 연동 시 외부 제공자·지역·보존·학습 이용·사용자 고지를 논의하고 승인하기 전에는 제품 데이터를 외부 모델에 보내지 않는다. 현재 Streamlit 시험은 합성 입력만 대상으로 한다.

# AI 다음 작업

- 2026-11-03 실험 분석 API `POST /internal/v1/analysis-runs/analyze` 연결. candidateDecision은 미보정 후보, decision은 보정 confidence가 없어 UNCERTAIN이다. 실제 Qwen DNS/연결, Backend 내부 응답 계약, confidence 독립 보정과 왕복 검증이 남았다. [결과](features/fastapi-relevance-analysis-REPORT.md).

화면 관련성 Streamlit UI는 2026-11-03 제거했다. Hybrid runner는 `evaluation/analysis_runner.py`에 유지하며, 목표 보조의 별도 Streamlit 테스트 화면은 유지한다.

## 가장 먼저 진행할 작업

- 작업명: 화면 분석 입력 계약을 실제 Server 경로에 연결하고 OCR 품질 필드 전달을 합의한다.
- 담당 영역: AI + Server + Desktop/Extension 계약 담당자
- 상태: AI Adapter와 내부 FastAPI 수신·실험 분석 구현. 실제 Backend 왕복은 미연결이다. `features/fastapi-reception-REPORT.md` 참고.

## 현재 계약 기준

Client → Server 요청은 `packages/shared-types/src/index.ts`의 `AnalysisRunRequest`를 따른다. 본문은 `clientRunId`, `contentSource`, `observedAt`, `sequence`(세션·출처별 1부터 증가), `guardStatus`(필수 nullable), 선택 `metadata`, 선택 `contentPayload.text`다. `sessionId`는 URL path에 있다.

Server → AI에서는 인증된 세션·목표·실행 문맥을 조회해 `VerifiedContext`와 확정 목표를 함께 제공해야 한다. Client가 보내지 않는 `runId`, `goalId`, `goalVersion`, `navigationId`, `isActiveTarget`, `sessionStatus`, `exclusionMode`를 Client JSON에 임의로 추가하지 않는다.

## 남은 작업

1. Server가 공개 요청을 검증하고 현재 `VerifiedContext`·확정 목표를 조립해 AI Adapter에 연결한다.
   - `POST /internal/v1/analysis-runs`의 내부 envelope와 서비스 Bearer 키를 합의한다. 이후 실제 분석 실행·공개 판정 응답을 연결한다. 현재 수신 확인 `RECEIVED`는 관련성 라벨이 아니다.
2. `extractionStatus`와 Desktop OCR의 `ocr.confidence`·`captureStatus`를 Server → AI에서 어디에 전달할지 합의한다. 공개 Client 요청 타입에는 현재 이 필드가 없다.
3. Server가 제공하는 `navigationId`가 실제 탭/창 이동을 구분하는지, AI에 계속 제공할지 확인한다.
4. Server/AI 결과 연결에서 `RELATED`·`UNRELATED`·`UNCERTAIN`, `reasonCode`, stale 관측, 중복 요청, 순서 역전 및 모델 실패를 통합 검증한다.
5. 독립 검수 자료로 판정 confidence를 보정한다. 보정 전 cosine/Hybrid 점수나 OCR 품질을 판정 confidence로 사용하지 않는다.

## 현재 검증 결과와 제한

- `PYTHONPATH=AI:. python3 -m pytest AI/tests/test_screen_analysis_contract.py -q`: 47 passed.
- `AI/tests/test_graphless_hybrid.py`, `AI/tests/test_graphless_metadata.py`를 제외한 AI 회귀: 158 passed, 1 skipped, 16 subtests passed.
- 전체 회귀 실행 시 현재 Python 환경에 `kiwipiepy==0.23.1`이 없어 Kiwi 의존 평가 테스트 75건이 실행되지 못했다. 네트워크 제한으로 임시 설치도 완료하지 못했다.
- 실제 Qwen 임베딩 요청, 공개 Server endpoint, Client 캡처/OCR 왕복은 검증하지 않았다.

## 선행 문서

- `docs/API_CONTRACT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_PRIVACY.md`
- `AI/docs/api_spec.md`, `AI/docs/content_acquisition_spec.md`, `AI/docs/analysis_rules.md`
- `AI/docs/features/screen-analysis-contract-v1-REPORT.md`

- Server 관련성 분석 요청·결과 저장 연동
- Chrome 내부 OCR·Desktop Apple Vision OCR 왕복 PoC와 실패 경로 검증
- ColPali 시각 콘텐츠 기준선 비교 PoC
- 페이지 이동·체류 시간 기반 학습 흐름·목표 이탈 분석
- MediaPipe 얼굴·시선·자세 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동
- 평가 자동화와 모델·Prompt Version 관리

## 완료 조건

- [ ] 기능 기획서를 작성했다.
- [ ] 입력·출력 계약을 공유했다.
- [ ] Health Check와 공통 응답 Schema가 동작한다.
- [ ] 다중 상태 또는 호환 상태 계약이 문서와 Schema에서 일치한다.
- [ ] 상세 분석기·모델 없이도 테스트가 실행된다.
- [ ] 개인정보·민감 데이터 제외 기준을 확인했다.
- [ ] 실행 명령과 의존성을 문서화했다.
- [ ] 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DEVELOPMENT_RULES.md`와 적용 Rule ID를 확인했다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] Desktop·Extension·Server 담당자와 계약을 공유했다.

## PR #137 충돌 해소와 현재 구현 경계

- 기준: `dev`의 공개 목표 `decision` 4종과 최신 화면 개입 `v1`, Client별 OCR·개인정보 계약을 유지한다. 2026-11-03 Notion 조회는 404였으므로 새 계약을 확정하지 않았다.
- 이 브랜치의 목표 보조 코드는 내부 `clarityStatus` 6종을 반환하는 JEV·Claude·LangGraph 구현이다. 공개 4종으로의 Backend 어댑터는 아직 검증되지 않았고 내부 결과를 공개 응답에 그대로 사용할 수 없다.
- 내부 요청은 `requestId`, `goalText`, `conversationTurn`(0~2), 선택적 `clarificationSessionId`, `previousAnswers[{question,answer}]`다. 회차와 답변 수가 같아야 하며 답변 2개 이후 추가 질문은 생성하지 않는다.
- 대화 ID의 발급·소유권·이력 검증, 사용자 최종 확인·목표 저장은 Backend 책임이다. AI는 전달받은 ID만 반환한다. AI HTTP 경로의 제공자 SDK 재시도는 0이며 Backend 재시도와 구분한다.
- 질문 스트리밍·최대 2건 병렬 생성·10초 이후 기본 질문 전환과 최대 3개 후보 검수/Repair는 유지한다. Laya는 비교 전용이다.
- 로컬 테스트용 원문 trace opt-in은 공유·배포 환경에 적용하지 않는다. 목표 최대 200자 내부 설정과 제품 2~40자, 내부 6종/공개 4종 변환은 통합 후속 검증 대상이다.

### #12 통합 전 확인

- 목표 생성은 GA-038의 Claude Haiku 4.5로 전환했다. 로컬 `.env` 또는 Streamlit Cloud Secrets에 `ANTHROPIC_API_KEY`(또는 `CLAUDE_API_KEY`)를 설정하고 서버를 재시작한다. Cloud Reboot·실제 사용자 UI 전체 흐름·JEV 후속 검수와 생성 품질 비교는 후속 확인이다. 다른 기능 브랜치의 OpenAI 설정은 이번 작업 대상이 아니다.
- Claude 전환과 최신 dev 충돌 해소는 [PR #160](https://github.com/What-a-move/FocusOn/pull/160)에서 검토한다. 목표 API 의존성은 dev의 분리된 `src/api/goal_routes.py`에 반영했으며 관련성 라우트·별도 학습 정리 데모를 보존했다. 전체 AI 회귀 405개·하위 테스트 27개 통과 후 Cloud 재시작·Live UI 검증을 이어간다.

- 2026-10-25 전달받은 계약대로 Backend 공개 `POST /api/v1/goals/clarify` DTO와 내부 AI 어댑터를 연결한다. 공개 입력은 `goalText`, `conversationTurn`, 선택적 `clarificationSessionId`, `previousAnswers[{question,answer}]`; 내부 AI 호출에는 `requestId`를 추가한다. Backend가 첫 대화 ID를 발급하고 소유권·유효성·권위 있는 회차를 확인한 뒤 공개 응답에 돌려주며 `POST /api/v1/goals`의 같은 ID를 확인한다. AI 응답의 ID echo는 인증·상태 저장을 대체하지 않는다.
- Desktop의 자동 재전송은 사용하지 않는다. Backend가 retryable 일시 실패에만 1초·3초 후 최대 2회 재시도하고 같은 ID·회차·답변을 유지한다. AI HTTP 경로의 제공자 SDK 재시도 0 설정은 반영했으나 Backend 재시도·Desktop/Server 통합은 해당 영역에서 구현·검증해야 한다.
- 로컬 `AI/.env`에 `TYPESAFE_API_KEY`와 `ANTHROPIC_API_KEY`를 설정한 뒤 JEV 판단 표, 질문·추천 생성, 후보 JEV 병렬 검수·Repair의 Live 흐름을 각각 최소 1회 검증하고 응답 지연·Rate Limit·실패 처리를 확인한다. 이때 `React Query에서 캐시 무효화 구현하기`와 구체성 2.00 이상 목표는 `CLEAR`, 구체성 1.00 이상 2.00 미만 목표는 `NEEDS_SUGGESTION`, 핵심 주제 또는 활동이 없는 목표는 `NEEDS_QUESTION`, 한국어 약어·오타·다중 목표·무효 입력은 기존 우선순위 상태인지 확인한다. 질문 답변 뒤 `interpretedGoal`과 추천 제목이 답변의 주제·활동·범위를 반영하는지도 확인한다.
- Streamlit에서 선택지·직접 입력 답변 후 목표 입력값이 한 번만 갱신되는지, 갱신된 문장을 사용자가 편집해 재분석할 수 있는지 Live UI에서 확인한다.
- 확인 질문 생성의 병렬 2건과 단일 호출 설정을 동일한 테스트 문장으로 비교해 P50·P95 지연시간, 질문 반복률, Claude 호출량·비용·Rate Limit을 기록한다. 실제 측정 전에는 속도 개선을 확정 성과로 기록하지 않는다.
- Live Streamlit에서 첫 질문 문구 표시 시각, 유효한 최종 질문 표시 시각, 10초 이후 기본 질문 전환, 취소된 병렬 호출의 비용·연결 정리를 확인한다. 5초는 강제 종료가 아닌 성능 목표로 P50·P95를 측정하고, 첫 JEV 판단과 화면 렌더링을 포함한 전체 체감 시간은 별도로 기록한다.
- `laya` 패키지와 `convaiinnovations/laya-multilingual` 체크포인트를 설치한 뒤 비교 표가 같은 네 판단 항목을 표시하는지 확인한다. Laya 응답·실패 여부와 무관하게 JEV `clarityStatus`, 질문·추천, 저장 경로가 동일한지 확인한다.
- Laya Multilingual 기본 모델은 목표 분류 Live sanity check에서 단일 목표도 복수 목표로 높게 응답해, 현재 값은 원시 실험값으로만 표시한다. 제품 판단에 사용하려면 한국어 목표 라벨 데이터로 fine-tune·확률 보정·holdout 평가를 별도 수행한다.

- 위 브랜치 실행 이력과 공통 공개 계약을 대조해 4종 응답 변환·목표 길이·인증을 담당자와 확인한 뒤 연결한다. PR 충돌 해소 자체를 통합 완료로 간주하지 않는다.

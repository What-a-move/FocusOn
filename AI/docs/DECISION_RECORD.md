# AI 결정 기록

## 2026-11-09 사용자 요청 — 베타 기준 0.75 및 readiness 검증

- 기존 0.6 결정을 대체하여 베타 후보 및 공개 응답의 기준을 0.75로 적용한다. 점수는 미보정 값이다.
- `/health`는 분석 설정도 검증한 뒤 실제 모델 임베딩을 확인한다. 실제 통합 환경과 판정 정확도 검증은 남는다.

## 2026-11-09 사용자 요청 — 베타 기준 0.6

- 이전 0.75 결정에 우선하여 AI 베타 후보 점수가 0.6 이상이면 후보 판정을 반환하고, 낮거나 없으면 `UNCERTAIN`으로 처리한다. 미보정 점수의 정확도는 평가가 필요하다.
- 루트 API 계약은 아직 0.75이므로 실제 Server 정책과 정합성은 Backend 담당자가 확인해야 한다.

## 2026-11-09 베타 응답과 Server 기준 맞춤

- 사용자 요청에 따라 AI가 베타 후보 점수를 루트 `docs/API_CONTRACT.md`의 0.75 기준에 맞춰 반환한다. 점수가 0.75 미만이거나 없으면 `UNCERTAIN`으로 처리한다. 이 점수는 미보정 값이며 정확도를 뜻하지 않는다.
- 기존에 적은 Server 측 미보정 예외 제안은 대체한다. `server/docs/DEVELOPMENT_RULES.md` 작업본의 0.6과 루트 계약의 0.75는 서로 달라 담당자 확인이 필요하다. Server 호출 연결과 전체 알림 검증은 별개로 남는다.

## 2026-11-03 모델 readiness 및 백엔드 Tailscale 연결

- 사용자 요청으로 AI Docker 구성은 제거한다. 배포 백엔드 호스트에서 학교 PC 모델에 Tailscale로 연결하는 방식으로 진행한다. 실제 FastAPI 실행 위치와 백엔드 호출 코드 연결은 확인이 필요하다.
- `/health`는 실제 임베딩과 기존 벡터 검증을 수행해 성공 200, 실패 503을 반환한다. 실패 로그는 공용 로거의 request.fail/WARN에 코드만 기록한다. 모델 프로브는 3초 timeout·재시도 0회이다.
- 학교 모델 프록시가 허용하는 keep_alive=10m을 사용한다. 전체 요청 timeout 및 UNCERTAIN fallback은 백엔드에서 확인한다.

## 2026-11-03 사용자 요청 — 베타 후보 판정 반환

- 당시 별도 분석 경로 `/internal/v1/analysis-runs/analyze`에서 `FOCUSON_ANALYSIS_DECISION_MODE=candidate`일 때 미보정 후보를 decision으로 반환하고 promptVersion에 `-uncalibrated`를 붙였다. 이 경로 분리는 2026-11-08 결정으로 폐기했다.
- 사용자와 정한 미보정 점수식을 사용하며 점수 누락 시 null로 반환한다. candidate 모드에서는 confidence 기준으로 후보를 강등하지 않는다. 기본 safe 모드의 confidence 통과 기준은 사용자 요청으로 0.6이다.
- 실제 작업본 반영과 서버 배포·정식 경로 연결은 별개다. Backend의 confidence 처리와 OCR 품질 정보 누락 처리는 확인 및 후속 구현이 필요하다.

## 2026-11-08 사용자 요청 — 베타 단일 판정 경로

- `POST /internal/v1/analysis-runs`가 검증 후 Hybrid 판정을 동기 반환한다. 과거 `/internal/v1/analysis-runs/analyze` 실험 경로는 제거한다.
- 베타 설정 `FOCUSON_ANALYSIS_DECISION_MODE=candidate`에서 보정되지 않은 후보 라벨을 `decision`에 반환하고 `promptVersion`에 `-uncalibrated`를 붙인다. 이 점수는 확률 보정 confidence가 아니며 이후 보정 전까지 베타 표시를 유지한다.
- 앱·탭 메타데이터만으로 낸 `UNRELATED`는 `UNCERTAIN`으로 유지한다. 모델·임베딩 실패는 `UNCERTAIN` / `ANALYSIS_UNAVAILABLE`이다. 분석 제외·민감 입력은 모델 호출 전 기존 409 차단을 유지한다.
- Desktop OCR의 `contentPayload.text`만으로 분석 가능하며 `ocr.confidence`와 `captureStatus`는 선택 입력이다. 존재하지 않는 품질값을 만들어 넣지 않는다.
- 모델 준비성은 `/health`가 실제 임베딩 호출·벡터 규격 검증으로 판정한다. Backend Java 호출부는 현재 저장소에 없어 실제 Server 왕복은 미연결이다.
- Qwen 요청 제한 기본값은 2.5초·재시도 0회다. 기존 합성 HTTP 표본에서 전체 40건 p95=2.32초였지만 DOM 20건 p95=3.76초로 3초 목표를 달성하지 못했다. 측정 범위는 학교 Ollama SETUP 리포트에 고정한다.

> AI 분석 기준·모델·프롬프트·데이터 처리 결정을 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

## 결정 GA-038 - 목표 보조 생성 제공자를 Claude로 교체

- 결정일: 2026-11-03
- 상태: 사용자 요청에 따른 #12 생성 어댑터 변경; 제품 외부 전송 정책 승인과 모델 품질 전체 평가는 별도
- 이유: OpenAI 키 폐기로 질문·추천·GoalProfile 생성이 중단되어 Claude 키로 실행하도록 교체한다.
- JEV 판단·검수, Router·임계값, Prompt와 Pydantic Schema, 사용자 최종 확인 조건은 유지한다. Laya는 계속 비교 전용이다.
- 공식 `anthropic` SDK의 `AsyncAnthropic.messages.parse(output_format=...)`와 `messages.stream(output_format=...)`을 사용한다. 기본 모델은 속도를 고려한 `claude-haiku-4-5-20251001`, 기본 최대 출력은 1,200 토큰이다. 모델은 `CLAUDE_MODEL`로 변경할 수 있다.
- 표준 키는 `ANTHROPIC_API_KEY`이며 기존 로컬 `CLAUDE_API_KEY`도 허용한다. 둘 다 있으면 표준 이름이 우선이다. `OPENAI_*` 환경변수, ChatOpenAI·Responses 호출과 langchain-openai 의존성은 제거한다.
- 2건 병렬 질문·첫 유효 결과 사용·중복 질문 제외·10초 이후 로컬 fallback·취소 및 연결 정리를 유지한다. AI HTTP 경로의 SDK 재시도 0도 유지한다.
- 잘린 출력·refusal·Schema 위반을 정상 초안으로 사용하지 않는다. Schema Repair는 최대 1회다. 400/401/402/403/404는 설정 오류, 429/5xx·연결 실패는 일시 장애로 구분하고 제공자 원문은 사용자 오류에 넣지 않는다.
- JEV·Laya LangSmith trace는 유지한다. 생성은 공식 SDK를 직접 사용하므로 기존 ChatOpenAI의 자동 LangChain trace는 생성하지 않는다. 원문 자동 추적 wrapper는 추가하지 않는다.
- 공식 자료: [구조화 출력](https://platform.claude.com/docs/en/build-with-claude/structured-outputs), [Python SDK](https://github.com/anthropics/anthropic-sdk-python). 로컬 SDK 0.125.0의 parse·stream 시그니처와 실제 호출을 확인했다.
- 합성 목표로 Claude 스트리밍 질문 5.58초(초안 갱신 13회), GoalProfile 6.12초, 추천 묶음 5.79초를 각 1회 확인했다. 세 Schema 모두 검증 통과했으며 원문과 응답 전문은 기록하지 않았다. JEV 포함 전체 Workflow·P95·의미 정확도 비교 결과가 아니다.
- Notion AI 분석 계약은 이번 조회도 404였으며 외부 API·DB·상태 계약을 새로 확정하지 않았다. 다른 기능 브랜치의 OpenAI 생성은 변경하지 않는다.

## 결정 001 - AI 분석 범위

- 결정일: 2026-09-04
- 담당 영역: AI
- 상태: 확정

### 결정 내용

AI는 사용자의 학습 목표와 Desktop·Extension에서 전달한 최소 화면·페이지 텍스트 및 메타데이터의 관련성을 판단한다. 이후 페이지 이동·체류 시간 기반 학습 흐름과 클라이언트 MediaPipe 상태값을 별도 기능으로 보조 분석한다.

### 결정 이유

AI가 수집 범위를 임의로 넓히지 않고, 학습 목표 이탈 판단이라는 핵심 역할에 집중하기 위해서다.

### 영향 범위

- Desktop 화면 분석 데이터
- Extension 페이지 분석 데이터
- Server AI 요청·결과 API
- 관련성 점수와 집중 상태 표시

## 결정 002 - 원본 데이터 처리

- 결정일: 2026-09-04
- 담당 영역: AI
- 상태: 확정

### 결정 내용

원본 화면과 원본 카메라 영상은 AI 서버에 기본 전송하거나 저장하지 않는다. 분석에 필요한 최소 상태값·텍스트·메타데이터만 사용한다.

### 결정 이유

사용자 개인정보 노출과 불필요한 데이터 보관을 줄이기 위해서다.

## 결정 003 - 불확실한 판단 처리

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

판단 정보가 부족하거나 신뢰도가 기준에 미달하면 `UNCERTAIN`을 반환한다.

### 결정 이유

불확실한 결과를 집중 또는 비집중으로 단정해 잘못된 알림을 발생시키지 않기 위해서다.

## 결정 004 - OCR 처리 역할 분리

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

OCR 엔진과 원본 화면 처리는 Desktop 등 클라이언트가 담당한다. AI는 전달받은 OCR 텍스트의 정제·품질 판단과 학습 목표 관련성 분석을 담당한다.

### 결정 이유

원본 화면을 AI 서버로 전송하지 않는 개인정보 원칙을 유지하고 AI 서비스가 분석 로직에 집중하기 위해서다.

### 영향 범위

- Desktop OCR 처리
- `AI/src/preprocessing/`
- 관련성 분석 입력 Schema
- 루트 `docs/DATA_PRIVACY.md`

## 결정 005 - 단계적 관련성 판단

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

관련성 판단은 텍스트 전처리, 규칙 기반 판단, 임베딩 유사도 판단, 필요한 경우 LLM 정밀 판단 순서로 구성한다. 규칙이나 임베딩만으로 충분히 판단 가능한 요청에는 LLM을 호출하지 않는다.

### 결정 이유

명확한 사례의 결과를 안정적으로 유지하면서 LLM 비용과 응답 시간을 줄이기 위해서다.

### 후속 확인

- [ ] 규칙 기반 확정 조건
- [ ] 임베딩 모델과 유사도 임계값
- [ ] LLM Fallback 진입 조건
- [ ] 단계별 응답 시간과 비용

## 결정 006 - 관련성·학습 흐름·사용자 상태 분리

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

현재 활동 관련성, 페이지 이동·체류 시간 기반 학습 흐름, MediaPipe 결과 기반 사용자 상태 보조 분석을 별도 모듈과 API 계약으로 분리한다.

### 결정 이유

페이지가 목표와 관련 있는지와 사용자가 실제로 화면을 보고 있는지는 다른 문제다. 입력과 판단 근거를 분리해야 오판 원인을 확인하고 기능별로 평가할 수 있다.

### 영향 범위

- `AI/src/analysis/relevance_analyzer.py`
- `AI/src/analysis/learning_flow_analyzer.py`
- `AI/src/analysis/user_state_analyzer.py`
- 후속 AI API 명세

## 결정 007 - 외부 콘텐츠 분석

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 제안

### 결정 내용

YouTube 자막과 PDF 추출 텍스트는 현재 활동 관련성 API가 안정된 뒤 별도 입력 수집 기능으로 검토한다. Supadata 등 외부 API는 비용, 개인정보, 실패 처리, 데이터 보존 정책을 확인한 뒤 선택한다.

### 결정 이유

외부 콘텐츠는 분석 범위를 넓히지만 MVP 관련성 판단의 필수 조건은 아니며 외부 서비스 장애와 비용이 추가되기 때문이다.

## 결정 008 - Rule ID 기반 AI 개발 기준

- 결정일: 2026-09-12
- 담당 영역: AI
- 상태: 제안

### 결정 내용

AI의 개인정보, 실패 처리, 상태 분리, LLM 권한, 캐시, stale 결과, 시간 집계와 평가 규칙을 `DEVELOPMENT_RULES.md`의 고정 Rule ID로 관리한다. 각 구현 Issue와 PR은 관련 Rule ID와 검증 결과를 연결한다.

### 결정 이유

같은 원칙이 API·분석·Prompt 문서에 서로 다른 표현으로 반복되어 구현 시 확인 누락과 의미 충돌이 발생할 수 있기 때문이다.

### 영향 범위

- `AI/docs/DEVELOPMENT_RULES.md`
- AI 코드·테스트·평가 데이터
- 관련 Issue·PR 검토 방식

### 관련 Issue·PR

- Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)

## 결정 009 - Notion 공개 상태와 AI 내부 세부 상태 분리

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Shared·Client
- 상태: 제안

### 결정 내용

`extractionStatus`, `analysisStatus`, `relevanceLabel`, `driftState`, `recommendedAction`을 분리한다. 공개 `FocusState`와 API의 관련성 값은 Notion 기준 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`로 통일한다. `SUPPORTING`, `OFF_TASK`, `UNAVAILABLE` 같은 내부 세부 상태는 Server가 공개 상태로 변환한다.

### 결정 이유

추출 실패, 목표 무관, 지속적 이탈과 실제 사용자 행동 제안을 하나의 값으로 표현하면 실패가 이탈로 보이고 짧은 방문이 잘못된 알림으로 이어질 수 있기 때문이다.

### 고려한 대안

- 내부 세부 상태를 공개 API에 그대로 노출: 의미가 풍부하지만 Client·Notion 계약이 달라진다.
- 공개 5개 상태만 내부에서도 사용: 계약은 단순하지만 AI 정책과 실패 원인을 세밀하게 표현하기 어렵다.

### 영향 범위

- `AI/docs/state_model.md`
- `AI/docs/api_spec.md`
- `packages/shared-types`
- Server 저장 Schema와 Client 표시

## 결정 010 - DOM·Apple Vision 기본 경로와 ColPali 실험 경로

- 결정일: 2026-09-12
- 담당 영역: AI·Extension·macOS 네이티브 모듈
- 상태: 제안

### 결정 내용

브라우저 MVP는 DOM 추출을 우선하고 정보가 부족할 때 허용된 로컬 Apple Vision OCR을 사용한다. ColPali는 PDF·Canvas·이미지·슬라이드처럼 시각 구조가 중요한 콘텐츠의 검색·근거 보강 후보로 기준선 비교 후 편입한다.

이 결정의 OCR 책임 표현은 결정 016에서 Client별 경로로 구체화한다. Chrome은 Extension 내부 OCR, macOS Desktop은 Apple Vision OCR을 사용하며 Chrome이 Desktop Native Messaging으로 자동 우회하지 않는다.

### 결정 이유

DOM은 비용과 개인정보 위험이 낮고, Apple Vision은 원본 이미지를 로컬에서 텍스트로 바꿀 수 있다. ColPali는 시각 구조 이해에 장점이 있을 수 있지만 실행 위치, 모델 자원과 이미지 전송 정책 검증이 필요하다.

### 고려한 대안

- 모든 페이지에 ColPali 사용: 일반 DOM 페이지의 비용·지연이 증가하고 원본 이미지 처리 범위가 넓어진다.
- OCR만 사용: 표·수식·슬라이드의 구조 정보가 손실될 수 있다.

### 영향 범위

- Extension 추출기와 Native Messaging
- AI 시각 콘텐츠 실험
- `AI/docs/data_lifecycle.md`
- `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)

## 결정 011 - 목표·논리 세션·실제 회차 분리

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Shared·Client
- 상태: 제안

### 결정 내용

`goalId`는 학습 목표, `sessionId`는 목표를 이어 가는 논리 세션, `runId`는 실제 한 번의 공부 회차, `eventId`는 관찰 구간으로 분리한다. 시간과 회차 상태는 Server가 관리하고 AI 결과는 원래 이벤트에 귀속한다.

### 결정 이유

목표를 다음 날 이어 공부하는 흐름과 일시정지·종료·새 회차를 구분하고, 중복·역순 이벤트와 늦은 AI 결과가 시간과 현재 상태를 덮어쓰는 문제를 막기 위해서다.

### 영향 범위

- `AI/docs/goal_session_spec.md`
- Server Entity·집계·세션 API
- `packages/shared-types`
- Extension 세션·이벤트 처리

### 관련 Issue·PR

- Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)

## 결정 012 - 제한된 단일 FocusSessionAgent

- 결정일: 2026-09-12
- 담당 영역: AI
- 상태: 제안

### 결정 내용

명확한 전처리·캐시·임베딩·시간 정책은 고정 코드로 처리하고, 여러 허용 근거를 조회해야 하는 모호한 사례만 단일 상태 기반 FocusSessionAgent로 처리한다. Agent는 읽기 도구, 사용자 범위와 호출·시간 예산을 강제한다.

### 결정 이유

모든 이벤트에 Agent를 사용하면 비용과 지연, 비결정성과 권한 표면이 커진다. 반대로 모호한 보조 학습 흐름에는 제한된 문맥 조회가 필요할 수 있기 때문이다.

### 고려한 대안

- 모든 분석을 Agent로 처리: 흐름은 유연하지만 비용·안전·재현성이 나빠진다.
- Agent 없이 단일 LLM 호출만 사용: 단순하지만 피드백과 최근 흐름 조회를 안전하게 조합하기 어렵다.

### 영향 범위

- `AI/docs/focus_session_agent_spec.md`
- `AI/docs/prompt_guide.md`
- AI Worker·Checkpoint·모델 호출

## 결정 013 - 범위가 명확한 피드백 우선 적용

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Client
- 상태: 제안

### 결정 내용

사용자의 명시적 관련성 수정은 같은 사용자·목표 버전·콘텐츠 범위에서 AI 캐시보다 우선한다. 한 페이지의 수정은 Domain 전체나 다른 목표로 자동 확대하지 않으며 `계속 보기`, 닫기와 무응답은 정답 라벨로 사용하지 않는다.

### 결정 이유

사용자 선택을 즉시 반영하면서도 한 번의 수정이 과도한 허용 규칙으로 일반화되는 것을 막기 위해서다.

### 영향 범위

- `AI/docs/feedback_personalization_spec.md`
- 피드백 API·저장 Schema
- 캐시·알림·집계·노트 무효화

## 결정 014 - 비동기 근거 기반 회차 노트

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Client
- 상태: 제안

### 결정 내용

회차 종료와 시간 저장을 먼저 확정하고 학습 노트는 낮은 우선순위의 비동기 작업으로 생성한다. 노트의 학습 주장은 허용된 `evidenceId`에 연결하고, 열람만으로 이해·구현·오류 해결 완료를 주장하지 않는다.

### 결정 이유

모델 장애가 핵심 시간 기록을 막지 않게 하고, 사용자가 확인하지 않은 학습 성취를 AI가 만들어내는 문제를 방지하기 위해서다.

### 영향 범위

- `AI/docs/session_note_spec.md`
- Server 집계·Queue·노트 저장
- 노트 Prompt·Schema·평가

## 학습 정리 #29 브랜치 결정 이력

공통 결정 021과 번호가 겹치는 기존 시연 기록은 LS-021~LS-023으로 구분해 보존한다. 공개 MVP 객체와 화면 개입 정책은 공통 결정 020·021이 기준이며, 아래 내용은 #29의 합성 데이터 실험 이력이다.

### 결정 LS-021 - 학습 정리의 합성 데이터 OpenAI 시험 경계

- 결정일: 2026-10-14
- 주제: #29 실제 화면 데이터 연동 전 생성 검증
- 상태: 변경됨 — 결정 LS-022 참조
- 결정 내용: Streamlit 기본 시연은 외부 호출이 없는 모의 로직을 유지한다. 별도 버튼은 코드에 고정된 합성 목표·근거만 `gpt-5-mini` 기본 모델로 구조화 생성 시험한다. UI의 편집 가능한 입력은 공급자 호출에 들어가지 않는다. `store=false`, 20초 Timeout, SDK 자동 재시도 0회로 시험한다. 구조·길이·근거·민감값·성취 단정 표현을 사후 검사하고 부적합 출력은 `FAILED`로 처리한다.
- 결정 이유: 현재 DOM 추출과 실제 사용자 화면 데이터가 없어 제품 개인정보 결정을 선행할 필요 없이 생성 품질과 계약을 가짜 근거로 검증할 수 있다.
- 고려한 대안: 실사용자 입력을 받는 범용 호출은 승인 전 데이터 전송 위험으로 보류했다. 완전 모의 시연만 유지하면 실제 생성 호출·검증 경로를 시험할 수 없다.
- 영향받는 파일: `AI/src/learning_summary_openai.py`, `AI/demos/learning_summary_streamlit.py`, `AI/docs/features/learning-summary-PLAN.md`
- 관련 Issue·PR: [#29](https://github.com/What-a-move/FocusOn/issues/29); PR 없음

### 결정 LS-023 - 퀴즈·추천 개념을 제외한 MVP 학습 정리 형식 재확인

- 결정일: 2026-10-27
- 근거: 사용자가 전달한 10-24 확정안·리포트 설계서 1.2/5.3·Figma D18. 해당 원본을 이번에 새로 열람한 것은 아니다.
- 공개 출력은 `overview`, `keyConcepts`(각 `name`, `briefExplanation`, `sessionEvidence`; 최대 5개), `nextStartPrompt`만 사용한다. 전체 1,200자 제한을 유지한다. `reviewQuestions`와 `recommendedConcepts`는 MVP 생성·표시·공개 Schema에 넣지 않는다.
- 모델은 `SummaryDraft`에서 근거 ID를 가진 핵심 개념만 생성한다. ID·개념명·설명 단어가 인용 근거에 있는지 검사하고 ID를 공개 결과에서 제거한다. 개요와 `nextStartPrompt`는 코드가 결정적으로 구성한다. 단어 대조는 의미적 함의·조건 보존을 완전히 증명하지 못하므로 근거가 불충분하면 `FAILED`로 보류한다.
- 원문 인용 강제를 제거하고 짧은 확인 내용으로 재서술한다. 기본 `gpt-5-mini`는 low 추론, SDK 재시도 0, Timeout 20초·store=false를 사용한다. 실제 화면 입력은 여전히 미연결이며 합성 입력만 시험한다.
- #29 브랜치를 정리된 임시 경로 대신 프로젝트의 `.handoff/learning-summary`에 복구했다. #12 작업과 별도로 실행한다. 로컬 키는 내보낸 환경변수 → 현재 AI/.env → Git 기본 작업 폴더 AI/.env 순서로 읽는다.

### 결정 LS-022 - Streamlit 입력을 DOM 추출 제안 형식에 맞춤

- 결정일: 2026-10-14
- 주제: #29 합성 입력 및 최소 근거 계산
- 상태: #29 시연 구현 기준 (실제 추출기 계약 확정 아님)
- 결정 내용: Streamlit 시연은 제안된 DOM 추출 객체 pageType, title, text, ocr, extractionMethod, extractionStatus, extractorVersion를 입력받는다. 최종 관련성은 별도 필드로 흉내 낸다. 성공한 DOM·RELATED·비민감 콘텐츠를 줄 단위 문단으로 분리하고 중복을 제거한다. 한 페이지 안의 서로 다른 문단도 각각 근거로 세며 두 개 미만이면 OpenAI를 호출하지 않는다. 버튼을 명시적으로 누른 때만 합성 입력의 정제 근거를 전송한다.
- 결정 이유: 사용자가 지적한 기존 직접 개념·관찰 입력은 실제 DOM 추출 흐름을 재현하지 못했다. 실제 추출기 구현 전에도 제안 입력 형식부터 정리 생성까지 검증할 수 있도록 하기 위함이다.
- 고려한 대안: 전체 DOM 텍스트를 생성 모델에 바로 보내는 방식은 제외했다. 원본·노이즈 대신 로컬에서 고른 짧은 RELATED 문단만 정리 입력으로 사용한다.
- 영향받는 파일: AI/demos/learning_summary_streamlit.py, AI/src/learning_summary_demo.py, AI/src/learning_summary_openai.py, AI/tests/test_learning_summary_openai.py
- 관련 Issue·PR: [#29](https://github.com/What-a-move/FocusOn/issues/29); PR 없음

## 결정 015 - 제품 표시 상태와 AI 내부 상태 매핑

- 결정일: 2026-10-13
- 담당 영역: AI·Server·Client
- 상태: 확정

### 결정 내용

AI 내부는 `RELATED`, `SUPPORTING`, `UNCERTAIN`, `OFF_TASK`, `UNAVAILABLE`을 유지한다. 제품 공개 `relation`에서는 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 사용한다. AI 관련성 판정은 앞의 세 값만 반환하며 `EXCLUDED`와 `PRIVACY_BLOCKED`는 AI 호출 전 정책 차단 결과다. `SUPPORTING`은 `RELATED`의 보조 유형이고 별도 공개 상태로 노출하지 않는다. `OFF_TASK`는 `UNRELATED`, `UNAVAILABLE`은 `UNCERTAIN`과 처리 실패 상세 상태로 투영한다.

### 결정 이유

AI 분석의 의미를 잃지 않으면서 기존 제품·공유 타입의 표시값과 실패·민감정보 상태를 구분하기 위해서다.

### 영향받는 파일

- `AI/docs/state_model.md`
- `AI/docs/api_spec.md`
- `AI/docs/prompt_guide.md`
- `AI/docs/evaluation_spec.md`

## 결정 016 - Client별 로컬 OCR 경로

- 결정일: 2026-09-22
- 담당 영역: AI·Extension·macOS 네이티브 모듈
- 상태: 제안

### 결정 내용

Chrome은 DOM 우선 후 Extension 내부 로컬 OCR을 사용하고, macOS Desktop은 허용된 활성 앱 화면에 Apple Vision 로컬 OCR을 사용한다. Chrome OCR 실패를 Desktop Native Messaging이나 외부 OCR로 자동 우회하지 않는다.

### 결정 이유

현재 Notion 기획의 Client 책임을 분리하고, 권한·설치·지연·개인정보 경계를 서로 다른 경로에서 검증하기 위해서다.

### 영향받는 파일

- `AI/docs/content_acquisition_spec.md`
- `AI/docs/CONTEXT.md`
- `AI/docs/DEVELOPMENT_RULES.md`

## 결정 017 - 목표 보조 명확성 상태

- 결정일: 2026-09-22
- 담당 영역: AI·Server·Client
- 상태: 변경됨 — 결정 020 참조

### 결정 내용

목표 보조는 `CLEAR`, `NEEDS_SELECTION`, `NEEDS_SUGGESTION`, `NEEDS_QUESTION`, `INVALID`, `UNRECOGNIZED_TERM`을 사용한다. 사용자 확인 전에는 목표를 확정하지 않으며 AI 오류·무응답이어도 직접 입력 목표를 사용자가 확인하면 세션을 시작할 수 있다.

### 영향받는 파일

- `AI/docs/goal_session_spec.md`
- `AI/docs/api_spec.md`
- `AI/docs/prompt_guide.md`
- `AI/docs/evaluation_spec.md`

## 결정 018 - 분석 제외 모드와 Server 상태 투영

- 결정일: 2026-09-22
- 담당 영역: AI·Server·Client
- 상태: 변경됨 — 결정 020 참조

### 결정 내용

Server는 `sessionStatus=ACTIVE/PAUSED/ENDED`, `exclusionMode=NONE/ANALYSIS_ONLY/ANALYSIS_AND_TIME`, `pauseReason`, `resumeRequired`를 관리한다. `ANALYSIS_ONLY`는 타이머를 유지하고 `ANALYSIS_AND_TIME`은 타이머·학습 기록을 중지한다. 두 모드 모두 콘텐츠 payload를 AI에 보내지 않는다.

### 영향받는 파일

- `AI/docs/goal_session_spec.md`
- `AI/docs/content_acquisition_spec.md`
- `AI/docs/api_spec.md`
- `AI/docs/evaluation_spec.md`

## 결정 019 - 카메라 출시 여부 보류

- 결정일: 2026-09-22
- 담당 영역: AI·Desktop·Server·Client
- 상태: 변경됨 — 결정 020 참조

### 결정 내용

Notion 기획서에 카메라 MVP 포함과 출시 미정 내용이 함께 있어 제품 출시 여부는 팀 결정 전까지 보류한다. AI는 원본 영상·프레임·랜드마크를 받지 않으며, 출시되더라도 클라이언트의 최소 상태·집계값만 선택적으로 사용한다. 개인화 휴식은 카메라 없이 시간·휴식 선택 데이터만으로도 동작해야 한다.

### 영향받는 파일

- `AI/README.md`
- `AI/docs/CONTEXT.md`
- `AI/docs/DEVELOPMENT_RULES.md`
- `AI/docs/features/personalized-break-recommendation-PLAN.md`

## 결정 020 - Notion MVP·API·분석 계약 동기화

- 결정일: 2026-10-12
- 담당 영역: AI·Server·Client
- 상태: 제안 (Notion AI 분석 계약 `REVIEW`, 팀 승인 전)

화면 개입 임계값·UNCERTAIN 처리의 최신 기준은 결정 021로 대체한다. 아래 120초 등 수치는 당시 검토 이력이다.

### 결정 내용

Notion 공개 목표 명확화 경로는 `POST /api/v1/goals/clarify`이고 판정값은 `CLEAR`, `AMBIGUOUS`, `MULTI_GOAL`, `UNKNOWN_TERM`이다. 질문은 대화당 최대 2회이며 AI 실패 시 사용자가 목표를 수동 확정할 수 있다. MVP에는 화면 관련성 판정 수정·저장 공개 API가 없다.

관련성 결과 `UNRELATED`는 콘텐츠 관계를 나타내며 시간 기반 `driftState`와 분리한다. 유효 이탈은 15초 미만 화면 전환을 제외하고 `GENTLE` 120초 연속, `NORMAL` 최근 10분 내 30초 이상 2회, `ACTIVE` 30초 연속으로 판정한다. `RELATED`는 현재 구간을 초기화하고 `UNCERTAIN`은 누적하지 않는다. 카메라는 60초에 후보만 만들고 90초에 최신 화면·권한을 다시 확인한다. 최신 화면이 `RELATED`면 억제한다.

화면 `EXCLUDED`에서는 화면 콘텐츠 분석을 중지하고 타이머와 별도 동의된 로컬 카메라 분석을 유지한다. `PRIVACY_BLOCKED`에서는 화면·카메라 분석을 중지하고 타이머는 유지한다. 카메라 원본·프레임·랜드마크는 서버·AI로 보내지 않는다. 최근 7일 개인화 휴식 추천은 P1 후보이며 MVP 기본 휴식 제안과 구분한다. 최신 제외 상태를 확인할 수 없는 동안에는 화면·카메라 분석을 모두 중지한다. 확인된 `EXCLUDED`에서만 별도 카메라 동의·권한에 따라 로컬 분석을 허용하며, 카메라 집계·전송은 별도 승인 전 금지한다.

팀원 보고의 120건 비교에서 Hybrid는 99건 정답·Macro F1 0.732로 가장 높았지만 원자료와 결합식은 미확인이다. 우선 평가 후보일 뿐 구현·배포 결정이 아니다. AI 전처리는 허용된 `text`·`ocr` 입력에서 최종 `qualityScore`·`contentHash`를 생성하며, 이탈 정책은 관련성 최종 라벨·confidence를 덮어쓰지 않는다. Backend 저장 컬럼 9개는 협의 전이다.

### 결정 이유

PR #15의 최신 문서 계약을 유지하면서 Notion 제품·API 정합성과 팀원 평가 보고, 현행 MVP 분석 정책을 구분하기 위해서다. 상세 AI 분석 계약은 Notion 문서 검토와 팀 승인을 기다린다.

### 영향받는 파일

- `AI/docs/analysis_rules.md`, `AI/docs/state_model.md`, `AI/docs/api_spec.md`
- `AI/docs/content_acquisition_spec.md`, `AI/docs/evaluation_spec.md`
- `AI/docs/feedback_personalization_spec.md`, `AI/docs/goal_session_spec.md`
- `docs/API_CONTRACT.md`, `docs/DATA_PRIVACY.md`

### 관련 Issue·PR

- Issue: [#16](https://github.com/What-a-move/FocusOn/issues/16)
- Issue: [#23](https://github.com/What-a-move/FocusOn/issues/23)

## 결정 021 - 화면 개입 v1과 MVP 학습 정리 객체

- 결정일: 2026-10-24
- 상태: 확정 (사용자 공유·최종 정정 및 최신 화면 분석 5.2·5.3, 리포트 5.3 대조)
- 결정 내용: Server가 관리하는 `v1`의 연속/반복 OR 조건은 `analysis_rules.md` 9.1을 따른다. `UNCERTAIN`은 비누적·연속 구간 종료이고 이전 30초 보류는 적용하지 않는다. 실제 전송부터의 서버 5분 cooldown과 Client의 확인 후 재집중 UI를 구분한다. 세션에 고정한 버전과 분석·개입 기록의 버전은 같아야 한다.
- 학습 정리: `keyConcepts`는 `name`, `briefExplanation`, `sessionEvidence` 객체 최대 5개다. 서로 다른 근거 2개 미만이면 `INSUFFICIENT_DATA`이며 전체 결과는 1,200자 이하, 원문·URL·코드·개인정보는 제외한다.
- 이유: 최신 사용자 확정과 제품 문서에 맞추고, 이전 Notion AI 분석 계약의 임계값·버전 예시가 새 세션 정책을 덮지 않게 한다.
- 제한: AI 코드·Policy Engine 구현 완료를 뜻하지 않는다. 현재 Notion 내 차이와 rolling window 경계의 미정 부분은 `docs/NOTION_SYNC_AUDIT.md`에 기록한다.
- 영향받는 파일: `analysis_rules.md`, `state_model.md`, `evaluation_spec.md`, `NEXT_TASK.md`, `CONTEXT.md`, `api_spec.md`, `session_note_spec.md`.



## Issue #11 AI 구현·평가 기록

아래 결정 028 이후는 Issue #11 기능 브랜치에서 이어진 AI 내부 구현 기록이다. 최신 공통 정책과 충돌하면 위의 최신 shared-dev 결정 및 해당 기능 정합성 보고서를 따른다.

## 결정 028 - 콘텐츠 부족 시 출처별 다음 수집 단계 반환

- 결정일: 2026-10-17
- 상태: 통합 #11 내부 구현 선택. 공개 API 계약이나 실제 Client 수집 구현 완료를 뜻하지 않는다.
- 근거: 허재원 화면 분석 기획과 통합 PLAN의 메타데이터 → DOM → 현재 viewport OCR 순서.
- 내용: 관련성 결과가 콘텐츠 부족·충돌 때문에 `UNCERTAIN`이면 내부 `next_content_source`를 반환한다. `TAB_META → CHROME_DOM → CHROME_VIEWPORT_OCR`, `APP_META → DESKTOP_APP_OCR`만 허용하며 OCR 이후에는 종료한다.
- 안전: 개인정보·제외·비활성 대상·stale·Graph 부재·임베딩 장애는 추가 수집으로 우회하지 않는다. 다음 단계는 새 `clientRunId`와 증가한 출처별 `sequence`를 사용하는 새 관측이다.
- 경계: foreground 앱·창·탭 선택과 DOM/OCR 실행은 Client 책임이다. 내부 `next_content_source`를 팀 합의 공개 응답 필드로 확정하지 않는다.
- 임의 목표: 이 결정 시점에는 고정 템플릿 제한을 유지했다. 이후 확정된 GoalProfile 기반 Graph 경로는 결정 029에서 구현했다.
- 영향: 내부 관련성 결과·테스트와 통합 #11 PLAN/REPORT/CONTEXT/NEXT_TASK.

## 새 결정 기록

## 결정 029 - 확정된 임의 목표의 Graph 생성 경로

- 결정일: 2026-10-17
- 상태: 통합 #11 내부 구현. 생성형 LLM 제공자 연결·공개 API·Server 영속 저장 완료를 뜻하지 않는다.
- 근거: 사용자의 임의 목표 지원 및 기존 Goal Graph 구조 유지 요청, GoalProfile 정본의 사용자 확인·버전 규칙.
- 구조: 기존 `GoalGraph`·`Concept`·역할·aliases·description·connection과 Hybrid 6번 계산을 유지한다. `ConfirmedGoal.templateId`는 선택값이며, 값이 있으면 기존 검토 템플릿만 사용하고 없으면 임의 목표 생성 경로를 사용한다.
- 생성: 주입된 생성형 LLM Client는 확정 목표 문장과 확인된 GoalProfile만 받아 동일 Graph Schema의 concepts 후보를 생성한다. 현재 화면·DOM·OCR·검색 기록은 입력하지 않는다.
- 검증: 관련 개념은 DIRECT/PREREQUISITE/SUPPORTING만 허용하고 DIRECT 최소 1개를 요구한다. 무관 주제는 기존 Graph와 같이 `role: null`이며 관련 aliases와 중복될 수 없다. 개념·aliases 개수/길이·ID 중복·민감정보·추가 필드도 검사한다.
- 실패 경계: API Key·Client가 없거나 timeout·잘못된 JSON·Schema 실패이면 확정 목표/GoalProfile 범위만 사용하는 최소 Graph로 내려가 세션 시작을 막지 않는다. 최소 Graph는 무관 주제를 추측하지 않으므로 양성 무관 근거가 필요한 `UNRELATED` 대신 근거 부족 `UNCERTAIN`을 유지한다. 미확정 목표는 기존처럼 거부한다.
- 캐시: 동일 목표·버전·Profile·생성기 fingerprint는 프로세스 내부에서 한 번 생성해 재사용한다. 운영의 재시작 간 재호출 방지는 Server가 확정 Graph와 graphVersion을 영속 저장해야 한다.
- 미연동: 실제 생성형 LLM 제공자·모델·API Key·timeout/재시도·외부 전송 고지·Server 저장 Adapter는 미정이다. 이번 테스트에서 외부 LLM을 호출하지 않았다.
- 버전: 임의 목표 Graph 해석 변경을 구분하기 위해 분석기 버전을 `school-hybrid-v4`로 올린다.
- 검증: 임의 목표 최소 Graph, Profile 역할 변환, 관련·무관 LLM Schema 검증, aliases 충돌·invalid/timeout fallback, 동일 버전 단일 생성, 기존 템플릿 회귀를 포함해 전체 191 passed, 8 subtests passed.
- 영향: `AI/src/api/schemas.py`, `analysis/goal_analyzer.py`, `models/llm_client.py`, `prompts/goal_prompt.py`, 관련성 resolver·테스트·통합 문서.

## 결정 030 - 임의 목표 Graph의 OpenAI 호환 LLM 생성과 ChromaDB 재사용

- 결정일: 2026-10-19
- 상태: 통합 #11 내부 구현. 실제 API Key를 사용한 생성 품질 승인이나 공개 API·Server 저장 완료를 뜻하지 않는다.
- 생성 호출: `GoalGraphLLMClient`의 실제 Adapter는 OpenAI 호환 `/chat/completions`를 사용한다. 모델·Base URL·API Key·timeout·최대 1회 재시도는 `AI/.env`에서 주입하며 비밀 값은 Git·로그·fingerprint에 포함하지 않는다.
- 구조화 출력: JSON mode를 요청하고 기존 `GeneratedGoalGraph` Schema로 다시 검증한다. Provider 오류·timeout·HTTP 오류·잘못된 응답은 확정 목표/GoalProfile 최소 Graph로 안전하게 내려간다.
- 저장: 생성된 Graph는 목표 전체 입력과 생성기 fingerprint를 해시한 키로 로컬 ChromaDB에 저장해 프로세스 재시작 후 재사용한다. 화면 DOM/OCR 본문·스크린샷·분석 결과는 이 Collection에 저장하지 않는다.
- Chroma 사용 경계: 이 저장소는 정확한 키 조회를 위한 파생 Graph 보관소이며 Hybrid 코사인 유사도 계산용 벡터 저장소가 아니다. 로컬 `PersistentClient`는 현재 개발·단일 인스턴스 범위이며 운영 다중 인스턴스와 Server 소유권·삭제 전파는 후속 계약이 필요하다.
- 활성화: API Key와 모델이 모두 설정된 경우에만 LLM·Chroma 경로를 구성한다. 둘 중 하나만 설정되면 설정 오류로 중단하고, 둘 다 비어 있으면 기존 최소 Graph 경로를 유지한다.
- 의존성: `chromadb==1.5.9`, `python-dotenv==1.2.3`을 고정한다.
- 영향: `AI/src/config.py`, `analysis/goal_analyzer.py`, `analysis/goal_graph_runtime.py`, `models/llm_client.py`, `storage/goal_graph_store.py`, 테스트·통합 문서.

## 결정 031 - 임베딩 요청 전용 Client

- 결정일: 2026-10-21
- 상태: 사용자 지시에 따른 통합 #11 내부 구현 변경.
- 근거: 학교 프록시와의 호환성을 맞추고 요청마다 반복되는 모델 메타데이터 왕복을 제거한다.
- 호출: 기본 `OllamaEmbeddingClient`와 real 평가 CLI는 `POST /api/embed`만 호출한다. 시작 시와 임베딩 전후의 `/health`, `/api/tags`, `/api/show`, `/api/version` 조회를 수행하지 않는다.
- 응답: 반환 모델명·벡터 개수·설정 차원·유한값·0벡터 검증과 정규화, timeout·응답 크기·재시도 제한은 유지한다.
- 버전 경계: revision·F16은 캐시와 평가 기록에 사용하는 로컬 설정이며 서버 실상태 자동 검증을 뜻하지 않는다. 모델 교체 시 revision 설정 갱신과 AI 프로세스 재시작으로 이전 캐시를 폐기한다. 조회하지 않은 서비스 버전은 평가에서 null로 유지한다.
- 검증: 관련 테스트 112 passed. 기본 Client의 실제 학교 요청은 `/api/embed` 2회뿐이었고 `dev-state-direct`가 `RELATED / GOAL_CONNECTED`를 반환했다. 공개 API·Server 연결과 독립 정확도는 별도다.

## 결정 032 - 목표 Graph 생성 지침 보강 및 Hybrid 임계값 하향 실험

- 결정일: 2026-10-21
- 상태: 사용자가 요청한 통합 #11 내부 실험. 운영 품질·공개 계약 승인값이 아님.
- 근거: 생성에 성공한 Graph에서도 기존 합성 14건이 모두 UNCERTAIN이었다. 별칭 1개 시 결합 점수가 기준에 미달하고 무관 주제의 좁은 범위로 양성 무관 근거가 형성되지 않는 것을 실측했다. Notion 관련성 계획은 역할·키워드·문단 벡터의 Hybrid를 유지하며 숫자는 테스트로 조정하도록 한다.
- Graph: `goal-graph-v2`에서 직접 구성 요소와 선행 지식·보조 도구를 구분하고 정식 용어·약어·자연스러운 한/영 별칭과 목표 밖의 일반 활동 범주를 요청한다. 현재 화면·평가 라벨로 목표를 확장하지 않으며 Schema·민감정보 검증과 실패 보류는 유지한다.
- 캐시: 생성기 fingerprint에 Prompt 버전과 지침 내용을 포함해 이전 생성 Graph의 재사용을 막는다. 기존 Graph의 구조와 JSON 컬럼은 유지한다.
- 실험값: DIRECT 0.72→0.62, PREREQUISITE/SUPPORTING 0.76→0.65, UNRELATED 0.80→0.75. `school-hybrid-v5` / `school-experimental-v2`로 구분한다. 가중치 0.35/0.65, 최소 코사인 0.55, 키워드 산식·게이트, 커버리지·충돌/실패 처리는 유지한다.
- 검증: 기존 14건과 실행 전 기대 라벨을 고정한 별도 합성 20건에서 실제 Qwen 벡터를 재사용해 이전·하향 기준을 비교한다. 단위·회귀 테스트는 219 passed, 8 subtests passed. 합성 결과·한계는 평가 리포트를 따른다.

## 결정 033 - 인접한 설명과 명시 코드의 문맥 근거 보완

- 결정일: 2026-10-21
- 상태: 사용자 요청에 따른 통합 #11 내부 구현, school-hybrid-v6.
- 범위: 기존 개별 문단 분석에 인접 설명+코드 묶음을 추가한다. 전처리된 BODY/OCR와 CODE가 붙어 있고 원문에서도 같은 쌍이었던 경우만 허용한다. 소제목·표·다른 입력을 넘지 않으며 표식 없는 코드 경계를 추측하지 않는다.
- 제한: 묶음 최대 8,000 UTF-8 바이트와 임베딩 Client 제한 중 작은 값. 초과 시 원래 단위를 유지한다. 묶음과 개별 문단 벡터는 같은 콘텐츠 배치로 요청한다.
- 판정: 묶음의 키워드·코사인·결합 점수를 같은 텍스트로 계산하고, 개별 반대 근거는 충돌 처리에 유지한다. 원래 문단 ID로 근거를 반환하고 커버리지는 원본 길이를 한 번씩만 계산한다. 숫자 기준과 공개 JSON 계약은 유지한다.
- 검증: 235 passed, 8 subtests passed. 실제 학교 Qwen API의 고정 causal mask 합성 사례에서 같은 벡터·Graph·숫자 조건으로 묶음 없이 UNCERTAIN, 적용 시 RELATED를 확인했다. 요청 2회 모두 HTTP 200, 전체 7.583초. 단건 확인이며 전체 정확도 개선 검증은 아니다.
- 기록: `evaluation/code-context-grouping-smoke.json`과 트랜스포머 평가 리포트. 별칭 생성 기준에 대한 제안은 실제 생성 Prompt 변경과 구분한다.

## 결정 034 - 현재 Hybrid를 재사용하는 로컬 Streamlit 비교

- 결정일: 2026-10-21
- 상태: 사용자 요청에 따른 내부 테스트 도구. 공개 계약·운영 임계값 변경 없음.
- 기존 E5 기본 화면을 학교 Qwen API로 교체하고 6번은 RelevanceAnalyzer school-hybrid-v6를 직접 호출한다. 후보 기준선과 현재 판정기를 구분하며 임계값 미보정 방식은 최종 판정을 보류한다.
- 입력 중 모델을 호출하지 않고 명시적 비교 버튼으로만 실행한다. 기대 라벨은 입력 계산에서 분리하며 실패 UNCERTAIN을 정답으로 집계하지 않는다. 원문·결과는 파일·DB에 자동 저장하지 않는다.
- 목표 Graph는 기존 resolver를 사용해 같은 목표의 생성/저장을 재사용한다. 임베딩은 /api/embed만 호출하며 7번 재정렬 모델 준비는 선택 사항이다.
- 검증: 전체 250 passed·8 subtests passed, 로컬 UI/HTTP 확인 및 실제 학교 Qwen 공부·요리 사례 실행. 독립 정확도와 재정렬 실측은 미완료다.

- 후속 사용자 정정: 현재 Streamlit에서는 채택한 Hybrid 6번만 실행하고 후보 1~5·7의 선택·재정렬 준비 UI는 제거한다. 기존 분석 결합식·판정 기준은 유지한다. 목표 Graph 기본값은 자동 생성으로 맞춘다.

## 결정 035 - Streamlit 세션별 분석기·연결 재사용

- 결정일: 2026-10-21
- 상태: 사용자 지연 문제 제기에 따른 로컬 테스트 UI 수정. 분석 규칙 변경 없음.
- HTTP Client·RelevanceAnalyzer를 같은 브라우저 세션에서 재사용해 기존 300초 목표 벡터·결과 캐시를 활용한다. 설정 변경 시 닫고 교체한다. 원문을 캐시하지 않으며 진단 벡터는 해시 키 기반 512건/300초로 제한한다.
- UI가 채우는 clientRunId만 매 제출마다 새 값으로 발급한다. 제공된 ID와 stale/멱등성 검사는 유지한다. 모델 요청을 하지 않은 결과는 cacheHit로 표시한다.
- 검증: 254 passed·8 subtests passed. 실제 동일 JWT 자동 Graph에서 첫 분석 2.576초/2회, 동일 화면 0.049초/0회, 본문 변경 1.000초/1회. Graph 생성 제외 단건 측정이며 독립 속도 기준선·GPU 순수 처리·운영 P95가 아니다.

## 결정 036 - 목표 기반 별칭 생성 Prompt 보강만 적용

- 결정일: 2026-10-21
- 상태: 사용자 요청 1번 범위의 내부 실험.
- goal-graph-v3.1에서 짧은 정식 용어·자연스러운 작업 표현·명사 변형·단일 소유 개념별 별칭을 요청한다. 점검은 동일 생성 응답 안에서 수행하고 추가 검증 LLM·매칭/결합 산식·임계값·JSON 계약은 변경하지 않는다.
- 기존 fingerprint로 캐시를 분리한다. 최초 v3 중복 별칭 실패는 기록에 보존하며 구조 검증을 완화하지 않는다. 목표 문장만 사용한 JWT 테스트에서 새 생성 HTTP 1회·13.363초, 기존 JWT는 별칭 적중이 개선돼도 H=0.5444·커버리지 49.6%로 UNCERTAIN이었다. 개발용 기대 일치 1/4로 독립 정확도 통과가 아니다.
- 검증: 255 passed·8 subtests passed 및 실제 학교 Qwen 재평가. 자세한 이력은 기능 REPORT를 따른다.

## 결정 037 - JSON 예시를 포함한 간결한 목표 Graph 생성 프롬프트

- 결정일: 2026-10-22
- 상태: 사용자 요청에 따른 내부 프롬프트 변경. 공개 입력 계약 변경 없음.
- `goal-graph-v4`는 기존 `concepts`·`unrelated_topics` JSON 예시, DIRECT/PREREQUISITE/SUPPORTING 정의와 관련 문서의 짧은 한·영 별칭 지침으로 구성한다. 역할을 억지로 모두 생성하지 않고 전역 별칭 중복·임의 범위 확장 금지는 유지한다.
- 별칭은 확정 목표를 기준으로 DOM/OCR 본문에 등장할 법한 표현을 생성한다. 실제 현재 본문·기대 라벨을 OpenAI에 전달하거나 판정 대상에 맞춰 목표를 확대하지 않는다.
- 사용자 지시에 따라 OpenAI 프롬프트 입력의 `goalProfile`은 제거한다. 내부 Schema·최소 Graph 폴백의 profile 지원까지 제거하는 작업은 포함하지 않는다.
- 기존 Schema 검증·매칭·가중치·임계값은 유지한다. 프롬프트 fingerprint로 기존 저장 Graph와 구분하며 과거 평가 결과를 새 버전 성능으로 사용하지 않는다.
- 입력 지침은 679단어에서 255단어로 줄었다. 생성 지연·의미 정확도 개선은 실측 전이다.
- 검증: 생성기·LLM Client·런타임 관련 테스트 26 passed, 변경 파일 `git diff --check` 통과. 새 필드 생성 없이 기존 JSON 출력과 프롬프트 변경 시 캐시 분리, profile 비전송을 확인했다. 실제 LLM을 재호출한 성능 평가는 아니다.

다음 결정은 `결정 046`부터 추가한다.


## 결정 038 - Streamlit 전용 Graph 없는 키워드·벡터 Hybrid와 본문 커버리지 실험

- 결정일: 2026-10-22
- 상태: 사용자가 명시한 Streamlit 테스트 범위. 운영 판정기·공개 계약·Notion 확정 정책의 교체가 아니다.
- 현재 화면은 목표 text → 결정적 키워드 추출 → 목표/정제 문단 실제 Qwen 벡터 → K/V 결합 → 문단 근거 → 본문 커버리지 → 3라벨 순서다. GoalProfile·템플릿·실제 본문에서 목표 키워드를 생성하지 않고, Graph 생성·OpenAI·ChromaDB를 호출하지 않는다.
- K는 목표의 고유 키워드 중 문단 적중 비율이다. H의 초기값은 K 0.20 + max(0, cosine) 0.80이다. 기존 Graph의 0.35/0.65와 역할별 기준은 가져오지 않는다. K=0인 의미상 관련 자료도 cosine 0.75 이상이면 H 0.60에 도달하도록 실험 시작점을 둔다. 이 숫자의 정확도 검증은 별도다.
- 초기 관련 문단 조건은 H≥0.60·cosine≥0.55, 무관 후보는 K=0·H≤0.20이다. 관련 커버리지≥0.60 또는 무관 커버리지≥0.70·본문≥80자·완전 추출 조건으로 각각 RELATED/UNRELATED를 정한다. 두 종류의 근거가 함께 있으면 커버리지 통과 여부와 무관하게 UNCERTAIN이며, 부족한 근거·실패도 보류한다. 낮은 목표 유사도 기반 무관 판정의 위험을 UI에 명시한다.
- 제목/소제목은 점수만 표시하고 본문 커버리지에서 제외한다. 설명+코드 묶음은 원본 ID의 합집합으로 세며 기존 본문 글자 수를 중복 합산하지 않는다. 입력·OCR·목표 버전·민감/제외/상태 검사와 실패 보류를 거친다. 품질 검사에서 잘린 본문은 판정을 보류하고 PARTIAL은 UNRELATED를 확정하지 않는다.
- 기존 HTTP Client와 Qwen 형식을 사용한다. 목표/본문 벡터만 해시 키 512개/300초 캐시에 두며 입력·기대 라벨·임계값 변경 때 판정을 새로 계산한다. 관련성 결과·원문을 파일/DB에 자동 저장하지 않는다. 실시간 문맥 재검증·이벤트 멱등 저장은 실제 서비스 분석기의 별도 기능이다.
- 검증: 합성 벡터 기반 스코어/커버리지·기대 라벨 격리·실패/정책 검사·캐시·기존 분석기 및 Streamlit 화면 회귀 41 passed. 이는 Qwen의 독립 정확도 검증이 아니다.

- 실제 연결 확인: 변경 전 고정한 합성 공부/요리/혼합 3건을 학교 Qwen API로 실행해 RELATED/UNRELATED/UNCERTAIN을 각각 확인했다. Graph 생성 0회, 임베딩 HTTP 2회, 전체 1.600초·API 누적 1.586초였다. 공부 1.241초·요리 0.353초·혼합 0.006초이며 혼합은 앞 두 본문 벡터를 재사용했다. 사전 별도 임베딩 요청으로 모델이 예열된 상태의 단회 측정이다. 합성 3건 기대 일치는 독립 정확도·평균 화면 지연·P95의 근거가 아니다.


## 결정 039 - Streamlit 실험 기준 고정과 평가 목표 불일치 차단

- 결정일: 2026-10-22
- 근거: 사용자가 현재 기준 고정과 QueryDSL 불일치 분석을 요청했다.
- 결정: 결정 038의 수치를 그대로 유지하고 현재 Streamlit에서 가중치·임계값 변경 컨트롤을 제거해 읽기 전용으로 표시한다. 기존 GraphlessConfig 산식과 점수·커버리지 조건은 변경하지 않는다.
- 평가 보호: `cases` 데이터셋에 `goal.text`가 있으면 사용자가 입력한 목표와 정규화 공백/영문 대소문자를 제외하고 일치해야 한다. 서로 다른 목표를 비교하면서 원래 정답 라벨로 일치율을 계산하지 않도록 임베딩 호출 전에 오류를 표시한다. 단일 실제 추출 payload에 새 필드를 요구하지 않는다.
- 분석: 화면에 보였던 25%는 QueryDSL 목표와 로드 밸런싱 데이터의 조합이었다. 올바른 QueryDSL 목표/데이터의 별도 재현은 7/12(58.33%)이며 자세한 점수는 `evaluation/querydsl-fixed-diagnostic.json` 및 성능지표 8절에 기록한다. 이는 사람 검수 전 합성 자료의 단회 결과다.

## 결정 040 - 동일 개념 중심의 별칭 생성 지침

- 결정일: 2026-10-22
- 사용자 요청에 따라 `goal-graph-v4.1`에서 정식 명칭·한영 동의어·표준 약어·표기 변형을 우선한다. 관련 도구·선수 지식·상위 주제를 같은 개념의 별칭으로 생성하지 않고, 조사·작업 지시·일반어를 제외한다. 개념별 2~5개를 권장하되 개수를 채우기 위한 표현은 만들지 않는다.
- 확정 목표와 개념 정의만 사용한다. 현재 본문·기대 라벨 비전송, 전역 별칭 중복 금지·JSON Schema·기존 fingerprint 캐시 분리는 유지한다.
- 현재 Graph 없는 Streamlit은 이 프롬프트를 호출하지 않는다. 실험의 키워드 추출·고정 가중치·임계값·입력 계약을 변경하지 않으며, 실제 LLM 생성 품질·속도 개선은 검증 전이다.
- 검증: 기존 생성기·LLM Client 테스트 24 passed, 단일 Desktop OCR 예시의 Streamlit 입력 파싱 통과. 실제 LLM·임베딩 호출은 수행하지 않았다.

## 결정 041 - Streamlit 로컬 키워드 정규화와 OpenAI 미호출 시간 표시

- 결정일: 2026-10-22
- 사용자 요청에 따라 실험용 키워드 추출에 Kiwi 0.23.1을 추가한다. 목표·본문 모두 NFKC 정규화 후 같은 명사/고유명사·영문 식별자 규칙으로 분석해 토큰을 비교한다. 기술 식별자를 먼저 보호하고 조사·어미·불용어를 제외하며, 임의 접미사 절단과 한글 부분 문자열 매칭을 제거한다. 별칭 생성이나 목표 확장은 하지 않는다.
- 입력 계약·Qwen 전송 문장·가중치·문단 통과 조건·커버리지 기준은 유지한다. 프로세스에서는 분석기만 재사용하며 원문/키워드 목록을 전역 캐시하지 않는다. 설치 의존성은 Streamlit 실험 requirements에만 추가한다.
- 현재 경로에는 OpenAI Client가 없으므로 미호출 0회/0초로 표시한다. Qwen 왕복·로컬 시간과 구분하고 Kiwi 최초 초기화는 로컬 시간에 포함한다. 앞으로 OpenAI 호출을 추가할 경우 실제 요청 경계의 계측도 함께 추가해야 한다.
- 검증: 관련 로컬 테스트 48 passed. 기존 QueryDSL 단일 합성 화면을 실제 학교 Qwen API 1회로 실행해 관련 커버리지 70.22%·RELATED를 확인했다. 두 번째 문단의 코사인은 0.6820으로 같고 키워드 점수 0.25→0.4286·H 0.5956→0.6313이다. 전체 6.094초·임베딩 5.211초·로컬 0.883초·OpenAI 0회다. 단건 재검증으로 전체 정확도 개선을 확정하지 않는다.
- Streamlit 재시작 후 같은 입력으로 UI에서도 RELATED·70.2%·OpenAI 미호출 0회/0초를 확인했다. 이 별도 실행은 전체 2.441초·임베딩 1.471초·로컬 0.970초(분석기 최초 초기화 포함)였으며 두 실행을 합친 운영 평균/P95로 사용하지 않는다.

## 결정 042 - Streamlit 핵심 용어·일반 표현·목표 구문 구분

- 결정일: 2026-10-22
- 사용자 승인으로 `kiwi-core-phrases-v2`를 적용한다. 작은 명시 일반 표현 목록을 목표의 나머지 명사/기술 식별자와 분리한다. 나머지는 이 실험의 핵심 용어 후보이며 의미적으로 핵심임을 모델이 검증한 결과는 아니다. K는 핵심 용어 적중 비율로 계산하고 단독 일반 표현은 점수/무관 후보 차단에 쓰지 않는다. 일반 표현만 있는 목표는 UNCERTAIN으로 보류한다.
- 목표의 일반 표현+핵심 용어 인접 순서쌍을 구문으로 보존한다. 본문은 같은 정규화 후 조사 연결만 허용하고 문장/줄/동사 경계를 넘지 않는다. `선택 정렬`도 구문으로 유지한다. 구문은 문맥과 근거를 표시하며 별도 가산하지 않는다. 한영 동의어나 선수 개념을 생성하지 않는다.
- 입력 JSON·임베딩 전송 문장·고정 가중치와 수치·커버리지·개인정보/품질 검사는 유지한다. 공개 API 필드가 아닌 Streamlit 내부 진단에 핵심/일반/구문과 무시한 단독 일반 적중을 표시한다. OpenAI·지식 DB는 호출하지 않는다.
- 검증: 관련 테스트 55 passed. 실제 Qwen 임베딩 36회로 합성 3목표/36건을 실행하고, 동일 벡터를 캐시에서 재사용해 v1 전체 명사 K/무관 조건을 재현 비교했다(재현 추가 임베딩 0회). TCP/UDP 8→9/12, 로드 밸런싱 8→8/12, QueryDSL 9→9/12, 전체 25→26/36(69.44→72.22%)이며 실패 0건이다. 변경 라벨은 tcpudp-35m 1건, 기대 라벨·본문·임계값은 변경하지 않았다. 새 방식 실행 시간 합계 18.656초는 단회 환경값이다. 이미 확인한 합성 개발 자료이며 독립 운영 정확도 개선의 증거로 쓰지 않는다.
- 파일 감시를 끈 새 Streamlit 프로세스에서 TCP/UDP 12건을 별도로 재실행해 9/12 일치·tcpudp-35m UNRELATED·K=0·H=0.1886·무관 커버리지 100%와 핵심/일반/구문 표시를 확인했다. 웹 재반영 종료 139는 [오류 기록](features/hybrid-streamlit-reload-ERROR.md)에 원인 미확정으로 남긴다.

## 결정 043 - 벡터 기본점수와 키워드 보강을 분리한 Streamlit 실험

- 결정일: 2026-10-22. 사용자가 새 결합식 적용을 명시적으로 승인했다.
- `vector-base-keyword-boost-v1`에서 S=max(0,V), H=S+0.20×K×(1−S)를 사용한다. 키워드 부족으로 벡터 점수를 낮추지 않으며 점수는 0~1 범위에 둔다. K는 기존 핵심 용어 적중 비율이고 일반 표현·목표 구문 규칙은 유지한다.
- 설정은 keyword_boost=0.20으로 표시하며 keyword_weight/vector_weight와 합계 검사, unrelated_passage_threshold는 제거한다. 관련 문단의 V≥0.55와 H≥0.60은 유지한다. 무관 후보는 핵심/구문 적중 없음·V≤0.25로 분리해 이전 식의 K=0·H≤0.20 범위를 보존한다. 커버리지·추출 품질·길이·충돌 검사는 유지한다.
- JSON 입력·정제 문장·임베딩 호출·원문 보관 정책과 운영 RelevanceAnalyzer는 변경하지 않는다. Streamlit에 버전·새 식·벡터 기본/키워드 보강/최종 점수를 구분한다. OpenAI는 미호출이다.
- 검증: 관련 모델 없는 테스트 72 passed. 키워드 미적중 시 V=0.70이 H=0.70으로 유지되는 것, V=0.54에서 키워드가 모두 맞아 H=0.632여도 최소 코사인 조건으로 보류하는 것, 무관 코사인 0.25 경계·점수 범위·기존 품질/충돌/캐시/입력 검사를 확인했다. 사용자 평가용 자료·기대 라벨은 변경하지 않았고 실제 모델 평가를 추가 실행하지 않았다. 과거 버전의 일치율은 이번 버전의 검증 결과가 아니다.


## 결정 044 - Streamlit 관련 커버리지·최소 글자 수의 판정 차단 제거

- 결정일: 2026-10-22. 사용자가 관련 비율/길이만으로 보류하지 않고 정보 부족·실패는 보류하는 실험 변경을 승인했다.
- `vector-base-keyword-boost-v2`: 관련 커버리지 설정을 제거하고 본문 통과 근거 존재·무관 근거와 충돌 없음으로 RELATED를 반환한다. 결합식·문단 점수 기준·커버리지 계산은 유지하며 사유를 RELATED_EVIDENCE_FOUND로 구분한다.
- Streamlit의 전처리만 min_text_chars=0으로 변경하고 중복 본문 30자 검사를 제거한다. 빈 본문·메뉴·문장부호뿐·근거 부족·저품질·추출/모델 실패는 보류한다. UNRELATED의 기존 비율·최소 길이·완전 추출 조건은 유지한다. 운영 판정기·공개 JSON·팀 정책은 변경하지 않는다.
- 본문 근거 한 개만으로도 통과할 수 있으므로 관련 화면 보류 감소와 무관/혼합 화면 오탐을 같이 평가해야 한다. 모델 없는 회귀와 UI 검증은 실제 Qwen 정확도 증거가 아니다.
- 검증: 모델 없는 관련 회귀·Streamlit UI 테스트 79 passed. 낮은 관련 커버리지에서 통과, 짧은 오류/코드, 짧지만 근거 없는 본문, 빈/메뉴/문장부호 입력의 무호출, 낮은 OCR 품질과 기존 충돌·무관 안전 조건을 확인했다.


## 결정 045 - Streamlit 전처리·문맥·키워드 개선과 개발 전용 보정

- 결정일2026-10-22. 사용자가 진단의1~4번 구현과 실제 개선 측정을 승인했다. `body-context-hybrid-v3`와 `kiwi-weighted-terms-v3`를 실험 경로에 적용한다.
- Streamlit에서만 제거 메뉴 수의 품질 페널티를 끄고 추출된 JSX/제네릭/entity·반복 소제목 경계를 보존한다. 운영 전처리 기본값과 공개 입력/출력은 유지한다.
- 넓은 용어는 낮은 보강 가중치를 사용하며 모든 키워드의 무관 판정 veto를 제거한다. 같은 화면의 실제 제목/창 제목/명시 소제목을 본문 문맥으로 보강하되 raw본문 최소 V를 요구한다. 메타데이터는 목표/query/본문 K에 넣지 않는다.
- 혼합48건의 라벨을 모델 호출 전에 고정했다. 개발 목표24건만72개 후보 선택에 사용하고 다른 평가 목표24건에는 고정 적용한다. 3라벨 지지 없는/모델 실패뿐인 자료로 안전한 보정을 발행하지 않으며 설정의 scoring/keyword/model/revision/dimension/instruction 버전을 검증한다.
- 선택 V0.30/H0.35, 무관V0.25. 별도 평가18/24→20/24, Macro-F1 0.771→0.811이나 무관1/6·정보부족3/6의 RELATED 오탐이 남았다. 기존 React24는4/24→23/24. 미검수 합성 실험이며 운영 기준 확정이 아니다. 평가 후 기준을 재튜닝하지 않았다. [결과](features/graphless-hybrid-calibration-REPORT.md)를 따른다.


## 결정 046 - Streamlit 메타데이터 전용 제목 판정 실험

- 2026-10-23 사용자 요청으로 TAB_META·APP_META를 본문 경로와 분리하고 본문 길이·커버리지 조건을 제외한다. 실제 pageTitle/windowTitle만 제목 근거로 사용하며 앱 이름·도메인만으로 확정하지 않는다. Graph·OpenAI·수집기·공개 API 변경은 없다.
- title-hybrid-v1: V≥0.55/H≥0.60, 보강0.20, 무관V≤0.25의 미보정 기준을 별도 고정한다. 빈/일반 제목·경계·충돌은 UNCERTAIN 후 TAB_META→CHROME_DOM, APP_META→DESKTOP_APP_OCR 요청을 표시한다. 본문 보정 V0.30/H0.35는 유지한다.
- 실제 Qwen 합성240건191/240(79.6%), 관련160 중111통과49보류, 모호80 모두보류, 실패0. 무관 정답이 없어 무관 성능·운영 정확도 검증은 아니다. 결과를 보고 임계값을 재조정하지 않는다. [실험 보고서](features/graphless-hybrid-calibration-REPORT.md).

## 결정 047 - Graphless 도입 표현의 약한 무관 후보 보류

- 2026-10-25 사용자 요청으로 002의 오보류 원인을 검토하고 실험 경로에 `adjacent-intro-v1`을 추가했다. 원래 점수 버전·숫자 임계값은 유지하고 근거 정책 버전을 별도 표시한다. 과거 보정 성능은 새 근거 정책의 정확도로 표시하지 않는다.
- 단순 도입+설명 합침은 002의 V0.249로 무관 기준0.25를 벗어나지 못했다. 대신 원본에서 인접한 동일 BODY/OCR, 한 줄 명사형 표현·종결 문장부호 없음, 다음 설명보다 짧음, 다음 설명에 문장부호 있음인 경우에 한해 두 원문 벡터의 문서 간 코사인≥0.50과 다음 설명의 목표 V>0.25를 확인한다. 통과한 도입 표현은 `INTRO_CONTEXT_DEFERRED`로 무관 근거에서 보류하며 관련 근거로 승격하지 않는다. 정제 중 제거된 경계·명시 소제목·코드·다른 출처를 넘어 연결하지 않는다.
- 무관 후보/인정 근거를 구분해 표시하고 원래 점수와 전체 분석 글자 수는 유지한다. 관련·인정된 무관 근거가 모두 있으면 계속 UNCERTAIN이다. 문서 간0.50은 새로 도입한 임시 휴리스틱이며 목표 코사인의 보정값을 그대로 전용한 것이 아니다. 실제 다른 활동의 의미를 확정하는 분류기는 아니다.
- 실제 Qwen 56건·56POST·36.342초. 동일 벡터에서 기존 정책을 재현 비교해 대조8건7→8건, 기존48건43→43건, 실패0. 002 도입 V0.227806·다음 설명과 코사인0.519579·다음 설명 V0.2521로 무관 후보가 보류돼 RELATED다. 점수·기대 라벨은 수정하지 않았다. 추가 API 호출·OpenAI·Graph 생성은 없다. 합성 개발/회귀 결과로 독립 운영 정확도를 주장하지 않는다.

## 결정 048 - 의존적인 후속 설명의 무관 근거 재검토

- 2026-10-25 사용자가 72건 개선안 구현·재평가를 승인했다. Streamlit 실험에 `dependent-followup-v2`를 추가한다. 기존 명사형 도입 표현 처리와 수치 임계값은 유지하며 공개 입력·출력, 메타데이터 판정, 운영 Graph 분석기는 변경하지 않는다.
- 한국어 후속 문장에서 앞 내용을 가리키는 표현과 추상적인 학습 명사·동작을 Kiwi로 검사한다. 전체 문장 예외 목록이나 caseId별 가중치는 사용하지 않는다. 작은 수동 어휘 규칙이며 Kiwi가 의미나 전공 주제를 이해하는 분류기는 아니다. 새로운 주제 명사·영문 식별자·알 수 없는 동작·불완전한 문장은 이 경로에서 제외한다. 영어·OCR 오탈자 등은 지원 범위를 벗어나 보류가 남을 수 있다.
- 원본에서 바로 이어진 동일 BODY/OCR의 앞 문단과 후속 문장만 묶는다. 제거된 문단·제목·소제목·코드·출처 경계를 넘어 연결하지 않고 8,000 UTF-8 바이트를 넘는 묶음은 만들지 않는다. 실제 원문 두 문단만 사용하며 제목이나 목표 문장을 묶음에 삽입하지 않는다.
- 앞 문단 자체의 V/H와 묶음 문맥의 V/H가 모두 기존 관련 기준을 통과할 때만 후속 문장의 낮은 유사도 무관 후보를 `FOLLOWUP_CONTEXT_DEFERRED`로 보류한다. 후속 문장을 관련 근거로 올리지 않고 원점수·전체 글자 수·관련 커버리지를 보존한다. 다른 무관 근거가 있으면 계속 충돌로 보류한다. 보류된 문장을 다음 문장의 관련 근거로 전파하지 않는다.
- 조건에 맞는 문맥만 기존 임베딩 입력 배치에 추가한다. 입력 벡터 수는 늘며 배치 상한을 넘으면 HTTP 요청도 늘 수 있다. Graph·OpenAI 호출은 없다. `FOLLOWUP_CONTEXT` 행과 앞 문단/문맥 식별자를 내부 진단에 표시한다.
- 자동 회귀376 passed,16 subtests passed. 새 MQTT/Redis 합성16건은 같은 실제 Qwen 벡터에서 기존 정책10/16→새 정책16/16, 기존 대조8건8/8·기존48건43/48 유지다. 새 자료도 독립 사람 검수·운영 정확도는 아니며 기존48건의 오판5건은 남아 있다. 전체600건 결과는 기존 [보정 리포트](features/graphless-hybrid-calibration-REPORT.md)에 별도 기록한다.

- 전체 실제 Qwen600건은456→528건(76→88%) 일치,72건 교정·새 오판 증가0·딴짓60/60 유지다. 최초 실패2건은 재시도 성공·최종 실패0. 90개 후속 문맥을 배치에 추가해72개 무관 근거를 보류했다. 전체185.774초·381POST이며 캐시 혼합 단회 값이다. 기준 조정 없는 합성 개발 결과로 한정한다.

## 결정 049 - 일반 메타데이터 제목의 무관 오판 방지

- 2026-10-25 사용자가 모호한 콘텐츠를 UNRELATED로 확정하지 않고 UNCERTAIN으로 처리하도록 확인했다. Streamlit 메타데이터 버전을 `title-hybrid-v2`로 갱신한다.
- 정보성 검사에 한해 앱 이름 단독 또는 구분자 뒤의 알려진 브라우저·앱 이름과 입력 `appName`을 제거한다. `내용`·`파일`·`화면` 등 일반적인 제목 표현만 남으면 관련·무관 근거로 쓰지 않는다. 모든 제목이 이 조건이면 임베딩 없이 UNCERTAIN/METADATA_INSUFFICIENT와 기존 다음 수집 출처를 반환한다. 같은 입력에 다른 유의미한 제목이 있어도 일반 제목은 반대 근거가 될 수 없다.
- 유의미한 제목의 임베딩 문자열·키워드 점수·벡터 임계값과 본문 판정은 유지한다. 앱 접미사를 실제 임베딩 입력에서도 삭제한 최초 진단에서 기존 무관 1건이 보류로 바뀌어, 최종 구현은 정보성 검사만 수정했다. 운영 Graph 분석기나 공개 API 필드를 변경하지 않는다.
- 관련 자동 회귀 127 passed. 최종 구현의 학교 Qwen 재평가 120건에서 기대 일치 36→66건, 일반 제목의 무관 오판 30→0건, 해당 30건 모두 UNCERTAIN·임베딩 0회다. 기존 정답이 오답으로 바뀐 건은 0건이며 전체 불일치 54건은 남아 있다. 90 POST·32.873초·실패 0건. 합성 개발 자료이며 실제 화면 독립 정확도가 아니다.
- 원본 파일·기대 라벨은 변경하지 않았고 입력 SHA-256을 확인했다. 결과: `evaluation/new-topic-metadata-prefilter-results.json`.


## 결정 050 - 10/26 화면 분석 MVP 계약 경계 적용

- 사용자 제공 최신 허재원 화면 분석/AI 계약과 이번 구현 요청을 기준으로 기존 #11 작업에 적용한다. 첨부 문서 자체는 REVIEW 상태이며 Notion 실시간 갱신을 확인했다고 주장하지 않는다.
- 메타데이터는 RELATED만 확정한다. 낮은 코사인의 무관 후보도 UNCERTAIN이며 사유 코드 종류와 관계없이 정상 현재 관측은 Chrome DOM 또는 Desktop OCR로 재확인한다. 차단·stale·실행 실패는 추가 캡처로 우회하지 않는다. DOM 뒤 Chrome OCR 요청은 제거하고 enum만 유지한다. 현재 입력으로 Chrome OCR을 직접 요청해도 모델을 호출하지 않는다.
- 공개 중첩 JSON과 내부 실행 문맥은 `relevance_contract`에서 변환한다. Server 문맥과 식별자·시각·목표가 다르면 거부한다. 원문 text는 최대 2,000자, 초과는 거부하며 기존 민감정보 검사를 유지한다. Client 캡처/PII 제거와 Backend 상태·이탈 정책은 별도 담당 범위다.
- 내부 진단 사유와 공개 5종 reasonCode를 분리한다. 공개 response confidence는 별도의 검증된 값을 명시적으로 받아야 하며 누락/0.6 미만이면 UNCERTAIN이다. cosine·Hybrid·OCR confidence를 판정 confidence로 전용하거나 임의 상수로 채우지 않는다. confidence 보정 및 Server 실행 연결은 후속 검증이 필요하다.
- 본문 점수 임계값·산식·정답 fixture와 과거 실측 결과는 유지했다. 메타데이터 정책 버전은 title-hybrid-v3-mvp, 내부 graph 분석기는 school-hybrid-v7-mvp다. 세션에 고정된 기존 정책을 자동 마이그레이션하는 구현이 아니다.

- 검증: 모델 없는 관련 회귀 310 passed·16 subtests passed, Streamlit 초기/입력/판정 UI 검증과 8501 health HTTP 200. 실제 Qwen/OpenAI 평가 및 Client/Server 통합은 실행하지 않았다.

## 결정 051 - 미사용 목표 Graph 실행 경로 제거

- 2026-10-28 사용자 요청에 따라 현재 Streamlit에서 사용하지 않는 Goal Graph 생성 Prompt, OpenAI 생성 Client, 생성/최소/템플릿 Graph resolver, ChromaDB 저장소·환경 설정, Graph 전용 분석기와 이전 비교/평가 CLI를 삭제한다. ChromaDB와 이전 재정렬 설치 안내도 제거한다.
- 실제 사용 중인 Kiwi + Qwen Hybrid와 공개 입력/응답 검증 도구는 유지한다. 공용 벡터 수학을 별도 모듈로 옮기고 평가된 retrieval 지침 문자열은 그대로 둔다. 판정 산식·숫자 임계값·근거 정책·기대 라벨은 이번 정리에서 바꾸지 않는다.
- Graph 전용 테스트는 제거하고 공통 벡터·스키마·캐시·문단 묶음 검사는 보존한다. 과거 실험 보고/결정은 이력으로 보존하되 현재 실행 안내에서 제거된 코드의 사용을 권하지 않는다.
- 공개 API·confidence 보정·실제 수집 연동은 이번 코드 삭제의 완료 범위가 아니다. 예전 Graph 분석기의 이벤트 멱등/현재 문맥 callback도 현재 실험 경로로 자동 이식됐다고 간주하지 않는다. 실제 Backend 통합에서 구현·검증한다.

- 검증: 전체 AI 회귀 262 passed·16 subtests passed. 기존 고정 48건은 동일한 합성 벡터에서 삭제 전후 판정/점수/근거/커버리지/임베딩 입력이 일치했다. 실제 API 정확도 재평가는 수행하지 않았다.

## 결정 052 - Backend 전용 FastAPI 입력 수신

- 2026-10-29 사용자 요청으로 기존 #11에 내부 수신 경로를 구현한다. `POST /internal/v1/analysis-runs`에서 기존 request/goal/context/extractionStatus/선택 OCR Adapter 인자를 받는다. Client 공개 컬럼은 유지하며 내부 envelope·OCR 위치는 Backend 합의 대상이다.
- 사용자 인증·최신 문맥 조회는 Backend, 서비스 Bearer 인증은 AI FastAPI가 담당한다. 키 없으면 시작 실패, 요청 크기·Schema·목표/관측·민감/제외/활성·P1 출처를 검사한다.
- 모델 호출·confidence·Queue·저장 없이 200 RECEIVED와 analysisExecuted=false를 반환한다. 실제 판정·Backend 왕복·중복/역순 처리는 후속 작업이다. 원문·키를 로그/오류에 노출하지 않으며 로컬은 loopback/8001/access-log 비활성으로 실행한다.
- 검증과 실행법: `features/fastapi-reception-REPORT.md`. 다음 결정은 053부터 추가한다.

## 결정 053 - Streamlit UI 제거와 분석 코드 보존

- 2026-11-03 사용자 요청으로 Streamlit 화면·UI 전용 테스트와 설치 목록을 제거한다. 기존 분석 runner를 `analysis_runner.py`로 변경하고 평가·테스트 참조를 옮긴다. Hybrid 판정 조건과 결과는 유지하며, FastAPI의 수신 확인은 실제 분석 실행을 의미하지 않는다.
- Kiwi 0.23.1을 공통 의존성에 명시한다. 실제 Backend 연동과 API에서의 분석 호출은 후속 작업이다.

## 결정 054 - FastAPI 실험 분석 연결

- 2026-11-03 사용자 요청으로 별도 내부 분석 경로를 추가한다. 기존 수신·검증을 재사용하고 기존 Kiwi + Qwen Hybrid 계산을 실행한다.
- 후보 라벨은 candidateDecision으로 분리하고 candidateCalibrated=false로 명시한다. 공개 decision은 보정된 confidence가 없으면 기존 기준대로 UNCERTAIN이다. 점수를 confidence로 변환하거나 임의 상수를 사용하지 않는다.
- 후보 필드와 서비스 연동은 Backend 합의, 실제 Qwen 연결 및 독립 confidence 보정은 후속. 테스트와 한계는 `features/fastapi-relevance-analysis-REPORT.md`를 따른다.

다음 결정은 `결정 022`부터 추가한다.

## 목표 보조 #12 브랜치 결정 이력

공통 결정 015~021과 다른 브랜치에서 같은 번호를 사용했으므로 기존 #12 이력을 GA-015~GA-037로 구분해 보존한다. GA-030·GA-036의 과거 화면 정책은 공통 최신 결정 020·021과 analysis_rules.md를 대체하지 않는다. 내부 목표 보조의 실제 실행 기준은 GA-027·GA-028·GA-035·GA-037이며 공개 응답 변환은 후속 작업이다.

## 결정 GA-015 - 목표 보조의 JEV 판단과 OpenAI 생성 분리

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

학습 목표 설정 보조에서 TypeSafe JEV System One은 타입화된 확률 판단·검증만 수행하고 OpenAI는 사용자 표시 문장과 GoalProfile 구조화 생성만 수행한다. Python Router와 LangGraph가 Threshold, 우선순위, 최대 1회 Repair와 fallback을 결정하며 OpenAI가 JEV 결과 또는 `clarityStatus`를 덮어쓰지 못하게 한다.

### 결정 이유

비생성형 판단 모델과 생성 모델의 책임을 섞으면 JEV 응답에 존재하지 않는 설명을 파싱하거나 생성 문장이 Workflow 결정을 바꾸는 오류가 생긴다. 결정적 코드를 신뢰 경계로 두어 판단 재현성과 사용자 확인 보장을 유지한다.

### 고려한 대안

- OpenAI 단일 호출로 판단과 문구를 함께 생성: 단순하지만 분기 재현성과 확률 기반 평가가 약해진다.
- JEV가 사용자 문구까지 생성한다고 가정: JEV의 질문 타입·응답 계약과 맞지 않아 제외했다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/src/workflow/goal_assistance.py`
- `AI/docs/prompt_guide.md`
- `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-016 - 로컬 목표 보조의 Vercel AI Gateway 단일 Key 경로

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

로컬 목표 보조 테스트는 `AI_GATEWAY_API_KEY` 하나가 설정되면 Vercel AI Gateway를 우선 사용한다. JEV는 TypeSafe 호환 Base URL과 `typesafe-ai/jev`, 자연어 생성은 OpenAI 호환 Base URL과 `openai/gpt-5-mini`를 사용한다. Gateway Key가 없으면 기존 TypeSafe·OpenAI 직접 제공자 Key를 사용하는 호환 경로를 유지한다.

### 결정 이유

TypeSafe 또는 별도 Jev 서비스의 Key·잔액과 호출 주소가 맞지 않아 발생하는 인증 오류를 제거하고, 판단과 생성 모델의 로컬 테스트 인증을 한 Key로 단순화하기 위해서다. 모델 역할 분리는 결정 GA-015를 그대로 유지하며 Gateway는 호출·과금 경로만 통합한다.

### 영향 범위

- `AI/src/config.py`
- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/streamlit_app.py`
- `AI/.env.example`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-017 - Vercel JEV 추론 권한은 실제 요청 결과로 안내

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

`AI_GATEWAY_API_KEY`의 존재와 모델 목록 조회 성공은 JEV 추론 권한이 있다는 뜻으로 표시하지 않는다. 실제 JEV 요청이 `no_providers_available`와 `RestrictedModelsError`를 반환하면 API Key 또는 모델명 오류로 합치지 않고, Vercel AI Gateway 유료 크레딧이 필요한 상태로 안내한다.

### 결정 이유

2026-09-27 실제 호출에서 유효한 Key로 모델 목록은 조회됐지만 무료 등급의 `typesafe-ai/jev` 추론은 403으로 거부됐다. TypeSafe 제공자를 명시해도 같은 결과였으므로 애플리케이션 라우팅으로 해결할 수 없으며, 사용자가 `.env`를 반복 수정하지 않도록 원인을 정확히 구분해야 한다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/streamlit_app.py`
- `AI/tests/test_model_clients.py`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-018 - 목표 보조는 TypeSafe·OpenAI 공식 API를 직접 사용

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

로컬 목표 보조는 Vercel AI Gateway를 사용하지 않는다. JEV 판단은 `TYPESAFE_API_KEY`와 `jev-latest`로 TypeSafe 공식 API를 직접 호출하고, 질문·추천·GoalProfile 생성은 `OPENAI_API_KEY`와 `gpt-5-mini`로 OpenAI 공식 API를 직접 호출한다.

### 결정 이유

Vercel 무료 등급에서 JEV 실제 추론이 403으로 제한돼 한 Key로 두 모델을 호출하는 경로가 로컬 테스트 목적을 충족하지 못했다. 사용자가 TypeSafe 공식 API의 잔액을 충전하고 Key를 발급했으므로, 중간 Gateway 설정과 Provider 라우팅을 제거해 각 제공자의 Key만 입력하면 예측 가능하게 실행되도록 단순화한다.

### 영향 범위

- `AI/src/config.py`
- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/streamlit_app.py`
- `AI/.env.example`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-019 - 시작 가능한 학습 목표의 명확성 기준 완화

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

목표의 핵심 주제와 사용자가 하려는 학습 활동을 대략 파악할 수 있으면 세부 범위, 완료 조건, 학습 방법, 자료, 시간의 누락만으로 질문이나 추천으로 보내지 않는다는 정책을 확정한다.

`is_usable_goal`의 `INVALID` 임계값, `has_multiple_main_goals`, `has_unclear_term`의 임계값과 우선순위는 유지한다. `specificity_confidence`와 `is_usable_goal`의 `INVALID` 초과 확률은 관측값으로 보존하되, 단독으로 명확한 목표를 질문 경로로 강등하지 않는다.

### 결정 이유

기존 Score 2의 의미는 "이해 가능하지만 넓음"이었으나 Router가 2.50 이상만 `CLEAR`로 처리해, 바로 시작 가능한 학습 목표까지 불필요하게 구체화하도록 만들었다. 명확성은 상세 계획 수준이 아니라 사용자가 한 회차에서 무엇을 공부하려는지 이해할 수 있는지로 판단해야 한다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/src/analysis/goal_analyzer.py`
- `AI/src/config.py`, `AI/.env.example`
- `AI/evaluation/evaluation_cases.json`
- 목표 보조 Router·Workflow·SDK 계약 테스트

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-020 - 바로 시작 가능성 Noul을 명확성 라우팅에 사용

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

JEV 최초 판단에 `is_startable_as_is` Noul을 추가한다. 이 값은 현재 목표 문장 그대로 한 회차 학습을 시작할 수 있다는 예 확률이며 기본 `STARTABLE_AS_IS_MIN=0.70` 이상이면 `CLEAR`로 라우팅한다. 기존 `specificity_level`과 `specificity_confidence`는 목표의 세밀함을 설명하는 화면 정보로 유지하며, `CLEAR`를 직접 결정하지 않는다.

### 결정 이유

Score의 "구체성"과 Score confidence가 각각 다른 의미를 가져 사용자가 `CLEAR` 기준을 해석하기 어려웠다. 사용자가 실제로 알고 싶은 질문을 별도 Noul로 분리하면 JEV가 확신한 "지금 시작 가능" 확률을 화면에 그대로 표시하고, Router도 동일한 의미의 값으로 결정할 수 있다.

### 영향 범위

- `AI/src/analysis/goal_analyzer.py`
- `AI/src/models/jev_client.py`
- `AI/src/config.py`, `AI/.env.example`
- `AI/streamlit_app.py`
- 목표 보조 평가 Fixture·Router·Workflow·SDK 계약 테스트

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-021 - 한국어 목표 구분용 JEV 판단 기준

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

학습 목표 최초 판단의 다섯 JEV 질문과 criteria를 한국어로 작성한다. 특히 `has_unclear_term`은 약어·오타·은어 때문에 핵심 주제 또는 활동을 추측해야 할 때만 true이며, 목표 범위가 넓다는 사실만으로는 true가 아니다. `is_startable_as_is`는 한국어 활동 표현을 포함해 현재 문장 그대로 시작할 수 있는지를 판단한다.

### 결정 이유

판단 대상이 주로 한국어 목표인데 기준 문구가 영어로만 작성돼 한국어 약어·오타와 넓은 주제의 경계가 충분히 직접적으로 표현되지 않았다. 지침의 언어 변경 자체가 품질을 보장하지는 않으므로, 요청된 약어·오타·넓은 주제 사례를 고정 평가 Fixture에 함께 추가해 실제 Live 평가로 후속 확인한다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/tests/test_model_clients.py`
- `AI/evaluation/evaluation_cases.json`
- `AI/docs/prompt_guide.md`, `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-022 - CLEAR는 바로 시작 가능성과 다른 판단을 함께 만족해야 함

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

`CLEAR`는 `is_startable_as_is`와 `is_usable_goal`이 각각 0.70 이상이고, `has_unclear_term`과 `has_multiple_main_goals`가 각각 0.50 미만일 때만 허용한다. 이미 강한 판정을 위한 `INVALID_USABLE_MAX=0.20`, `UNCLEAR_TERM_MIN=0.70`, `MULTIPLE_GOALS_MIN=0.70`은 유지한다. 강한 임계값에 이르지 않았더라도 CLEAR 확인선에 걸리는 입력은 `NEEDS_QUESTION`이다.

### 결정 이유

높은 바로 시작 가능성 하나가 학습 목표 적합도 또는 불명확 표현의 중간 위험을 덮어써 잘못 `CLEAR`가 될 수 있었다. 예를 들어 바로 시작 가능성 90%, 적합도 60%, 불명확 표현 55%는 사용자가 의미를 확인할 여지가 있으므로 바로 저장시키지 않고 질문 경로로 보내야 한다.

### 영향 범위

- `AI/src/config.py`, `AI/.env.example`
- `AI/src/analysis/goal_analyzer.py`
- `AI/tests/test_goal_analyzer.py`
- 목표 보조 정책·평가·다음 작업 문서

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-023 - 목표 정보 수준 Choice로 넓은 목표와 정보 부족 목표를 분리

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정
- 대체: 결정 GA-020·022의 `CLEAR` 라우팅 규칙과 결정 GA-021의 최초 질문 개수

### 결정 내용

JEV 최초 판단에 `goal_detail_level` Choice를 추가하고 `CLEAR`, `BROAD`, `MISSING` 중 하나를 반드시 선택하게 한다. `INVALID`·`UNRECOGNIZED_TERM`·`NEEDS_SELECTION`의 기존 강한 우선순위를 적용한 뒤 `CLEAR`는 GoalProfile 확인, `BROAD`는 `NEEDS_SUGGESTION`, `MISSING`은 `NEEDS_QUESTION`으로 보낸다. `BROAD` 화면에서는 사용자가 원문을 명시적으로 선택하면 해당 원문으로 GoalProfile 확인 단계에 갈 수 있다.

`is_startable_as_is`, `specificity_level`, `specificity_confidence`는 Streamlit 판단 표의 설명값으로 유지하며 상태를 결정하지 않는다. 질문 답변은 다음 JEV 재평가와 OpenAI 생성 요청에 모두 전달하고, 최신 답변의 주제·활동·범위·결과는 추천 후보와 GoalProfile의 `interpretedGoal`에 반영하도록 Prompt 계약으로 강제한다.

### 결정 이유

Noul 확률과 Score는 넓은 목표와 핵심 정보가 빠진 목표에서 비슷한 값이 나올 수 있어, 바로 시작 가능성이나 복합 임계값만으로는 두 사례를 안정적으로 구별하기 어려웠다. 서로 배타적인 Choice로 정보 수준을 직접 판단하면 `BROAD`에는 선택권과 추천을, `MISSING`에는 질문을 일관되게 제공할 수 있다. 또한 답변이 최종 목표에 반영되지 않으면 질문 과정의 의미가 없으므로 생성 계약과 회귀 테스트에 이를 명시한다.

### 영향 범위

- `AI/src/analysis/goal_analyzer.py`
- `AI/src/models/jev_client.py`
- `AI/src/workflow/goal_assistance.py`
- `AI/src/prompts/goal_prompt.py`
- `AI/streamlit_app.py`
- 목표 보조 Router·Workflow·SDK 계약·Streamlit 테스트와 Offline Fixture
- `AI/docs/features/goal-assistance-PLAN.md`, `AI/docs/prompt_guide.md`, `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-024 - 학습 목표 성립 판단과 짧은 생성의 지연을 분리

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

JEV `is_usable_goal`은 목표의 구체성, 난이도, 완료 가능성, 계획 품질을 평가하지 않는다. 공부·연습·구현·문제 해결에 사용할 수 있는 주제 또는 활동이면 범위가 넓거나 완료 조건이 없어도 true를 높게 판단하도록 지침과 criteria를 명시한다. 화면에서는 이 값을 "학습 목표 성립 가능성"으로 표시하고 단독 통과 기준이 아니라는 설명을 붙인다.

OpenAI의 질문·추천·GoalProfile 구조화 생성은 기본 `reasoning_effort=minimal`, `verbosity=low`, 최대 600 출력 토큰으로 호출한다. JEV 판단·상태 라우팅·Schema 검증·실패 시 재시도는 생략하지 않는다.

### 결정 이유

기존 "실제로 수행 가능한 목표"라는 표현은 JEV가 범위가 넓은 정상 학습 목표까지 계획의 완성도 관점에서 보수적으로 채점할 여지를 만들었다. 또한 짧은 질문 하나를 만들 때 모델 기본 추론량이 필요 이상으로 커질 수 있었다. 학습 목표 성립과 정보 수준을 분리하고, 생성 모델의 추론량·출력 길이만 낮추면 판단 의미를 흐리지 않으면서 사용자가 느끼는 대기 시간을 줄일 수 있다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/src/config.py`, `AI/.env.example`
- `AI/streamlit_app.py`
- 제공자 설정·JEV Prompt·Streamlit 렌더링 테스트
- `AI/docs/prompt_guide.md`, `AI/docs/features/goal-assistance-PLAN.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-025 - Laya는 목표 설정의 로컬 비교 판단으로만 사용

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

Streamlit 목표 입력 시 `convaiinnovations/laya-multilingual`을 로컬로 호출한다. Laya에는 JEV와 정확히 같은 `originalText`, `selectedGoalText`, `effectiveGoalText`, `clarificationAnswers` state와 여섯 typed question을 전달한다. Laya 결과는 JEV와 나란히 비교 표로 표시한다.

JEV의 `clarityStatus`와 Router만 실제 질문·추천·GoalProfile·저장 흐름을 결정한다. Laya 결과·오류·미설치 상태는 JEV 판단을 변경하거나 저장을 막지 않는다.

### 결정 이유

한국어 목표에서 두 System One 계열 판단의 차이를 확인하려면 입력과 기준이 동일해야 한다. 다만 Laya Multilingual은 자체 확률·Score 보정 체계를 가지며 현재 목표 보조 도메인에서 보정·평가되지 않았으므로, 비교 결과를 제품 동작에 연결하면 안 된다.

### 영향 범위

- `AI/src/analysis/goal_assessment_contract.py`
- `AI/src/models/jev_client.py`, `AI/src/models/laya_client.py`
- `AI/streamlit_app.py`
- `AI/requirements.txt`, `AI/.env.example`
- 모델 계약·Streamlit 비교 렌더링 테스트

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-026 - Laya 기본 체크포인트의 목표 분류 수치를 보정 전 원시값으로 표시

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

Streamlit의 Laya 열 이름을 `Laya 원시값 (실험용)`으로 표시하고, JEV 확률·통과 기준과 직접 비교할 수 없다는 경고를 함께 보여 준다. Laya 비교 호출은 유지하되, 한국어 목표 라벨 데이터로 fine-tune·확률 보정·holdout 평가를 완료하기 전에는 어떤 상태·저장·추천 정책에도 사용하지 않는다.

### 결정 이유

로컬 Live sanity check에서 Laya 호출과 응답 매핑은 정상이었다. 그러나 단일 목표 `React Query 캐시 무효화 구현하기`와 `Spring Security JWT 인증 구현하기`가 각각 복수 목표 Noul 86.57%, 97.12%로 출력되어, 기본 체크포인트의 목표 분류 zero-shot 수치가 이 도메인에서 신뢰할 수 없음을 확인했다. 이를 UI 버그나 JEV와 동등한 확률처럼 보여 주면 잘못된 비교를 유도한다.

### 영향 범위

- `AI/streamlit_app.py`
- `AI/tests/test_streamlit_app.py`
- `AI/docs/CONTEXT.md`, `AI/docs/NEXT_TASK.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-027 - JEV 최초 목표 판단 계약으로 복원

- 결정일: 2026-09-29
- 담당 영역: AI
- 상태: 확정

### 결정 내용

사용자 요청에 따라 JEV 최초 목표 판단을 최초 기능 구현의 계약으로 복원한다. JEV는 `goal_text`, `selected_goal_text`, `clarification_answers` state와 `is_usable_goal`, `has_multiple_main_goals`, `has_unclear_term` Noul 및 `specificity_level` Score의 네 질문만 받는다. instructions와 criteria도 최초 영어 문구를 사용한다.

Router는 `INVALID` → `UNRECOGNIZED_TERM` → `NEEDS_SELECTION` 우선순위 뒤 `is_usable_goal < 0.60`, `specificity_confidence < 0.45`, `specificity_level` 2.50/1.50 threshold를 적용한다. 나중에 추가한 `is_startable_as_is`와 `goal_detail_level` Choice는 JEV 호출·상태 분기·Streamlit 표에서 제거한다.

Laya는 비교 전용이라는 결정은 유지하되, JEV와 동일한 네 질문 및 state를 받도록 함께 되돌린다. OpenAI의 답변 반영 생성, 직접 제공자 설정, Streamlit의 선분석/최종 확인 흐름은 JEV 판단 계약이 아니므로 변경하지 않는다.

### 결정 이유

후속 한국어 지침, 바로 시작 가능성 Noul, 정보 수준 Choice를 추가한 뒤 사용자가 체감한 JEV 목표 분류가 최초 구현보다 나빠졌다고 판단했다. 동일한 기준으로 성능을 다시 확인할 수 있도록 후속 실험적 판단 변경을 제거하고, 최초 동작을 기준선으로 삼는다.

### 영향 범위

- `AI/src/analysis/goal_analyzer.py`, `goal_assessment_contract.py`
- `AI/src/models/jev_client.py`, `laya_client.py`
- `AI/streamlit_app.py`, `AI/evaluation/`, `AI/tests/`
- 목표 보조·평가·Prompt 관련 AI 문서

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-028 - 목표 명확성·추천 Score 기준 하향

- 결정일: 2026-09-30
- 담당 영역: AI
- 상태: 확정

### 결정 내용

JEV `specificity_level`의 `CLEAR` 기준을 2.50에서 2.00으로, 추천(`NEEDS_SUGGESTION`) 기준을 1.50에서 1.00으로 낮춘다. 1.00 미만만 `NEEDS_QUESTION`으로 남긴다. 학습 목표 적합도, 구체성 신뢰도, 불명확 용어, 복수 목표, 무효 입력의 기준과 우선순위는 변경하지 않는다.

### 결정 이유

사용자가 명확한 목표와 넓지만 학습 가능한 목표를 더 쉽게 확인·추천 경로로 진행할 수 있도록 Score 경계를 낮춘다. 이 값은 평가 데이터로 재조정 가능한 설정값으로 유지한다.

### 영향 범위

- `AI/src/config.py`, `AI/.env.example`
- 목표 Router 경계 테스트와 Offline 평가 Fixture
- 목표 보조·Prompt·평가 관련 문서

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-029 - 로컬 목표 보조 테스트의 LangSmith 원문 Trace opt-in

- 결정일: 2026-09-30
- 담당 영역: AI
- 상태: 확정

### 결정 내용

로컬 수동 테스트에서 `LANGSMITH_TRACE_RAW_GOAL_TEXT=true`를 명시한 경우에만 LangSmith 입력에 `originalText`, `selectedGoalText`, `clarificationAnswers`를 기록한다. 기본값과 공유·통합·배포 환경의 값은 `false`다.

Streamlit의 병렬 비교는 `goal_assessment_comparison` 부모 trace로 기록하며, 그 아래 `jev_assess_goal`과 `laya_assess_goal`을 함께 표시한다. 두 모델 output에는 각자의 네 typed judgment와 현재 Router 상태를 기록한다.

### 결정 이유

목표 원문이 완전히 제거된 trace는 반복 테스트의 입력과 정답 기대를 대응할 수 없어 JEV와 Laya의 경계 사례를 분석하기 어려웠다. 로컬에서 개발자가 직접 입력한 테스트 query에 한해 opt-in으로 원문을 보이면 모델 비교와 라우팅 분석이 가능하다. 다만 실제 사용자 입력의 외부 trace 전송은 서비스화 전에 별도 개인정보·보존·접근 정책으로 검토한다.

### 영향 범위

- `AI/src/models/langsmith_tracing.py`
- `AI/src/models/jev_client.py`, `AI/src/models/laya_client.py`
- `AI/streamlit_app.py`, `AI/.env.example`, `AI/README.md`
- `docs/DATA_PRIVACY.md`, `AI/docs/CONTEXT.md`, `AI/docs/NEXT_TASK.md`

## 결정 GA-030 - MVP 화면·카메라 분석 정책 문서 동기화

- 결정일: 2026-10-01
- 담당 영역: AI·Server·Desktop·Extension·공통 개인정보 정책
- 상태: 제안 (Notion 기준 반영, 최종 팀 검토 필요)

### 기준

[AI 분석 계약](https://app.notion.com/p/AI-3de0b6e5e64c80fbb0bccb985e38002c)의 현재 MVP 정책을 Markdown 계약에 동기화한다. 이 Notion 페이지는 `REVIEW` 상태이므로 본 기록은 문서 반영 사실을 남기며 최종 팀 승인이나 구현 완료를 의미하지 않는다.

### MVP 문서 기준

- 목표 문장 확인·수정과 화면 관련성 판정 수정·삭제는 별개다. AI 명확화 질문은 대화 전체 최대 2회이고, 여러 목표가 입력되면 최종적으로 하나를 선택한다.
- 화면 관련성 confidence `0.6` 미만은 `UNCERTAIN`으로 처리한다. 15초 미만 화면 전환은 이탈 누적에서 제외한다. 약하게 120초 연속, 보통 최근 10분 안에 30초 이상 이탈 2회, 적극적으로 30초 연속을 적용한다.
- `RELATED`는 현재 이탈 구간과 카메라 알림 후보를 초기화하고, `UNCERTAIN`은 이탈 시간에 누적하지 않는다.
- 카메라 `AWAY` 60초에 후보를 만들고 90초 지속 시 최종 조건을 확인한다. 최신 화면이 `RELATED`면 알림 후보를 억제한다.
- `EXCLUDED`는 화면 분석을 중단하되 유효한 사용자 동의 아래 카메라 분석은 계속할 수 있다. `PRIVACY_BLOCKED`에서는 화면·카메라 분석을 모두 중단한다.
- 화면 관련성 판정 수정·삭제, 그 결과 저장과 개인화는 MVP에서 제외한다. 기존 결정 013은 후속 기능 검토용 제안으로 남기며 MVP에 적용하지 않는다.

### 영향 범위

- `AI/docs/analysis_rules.md`, `state_model.md`, `api_spec.md`
- `AI/docs/feedback_personalization_spec.md`, `evaluation_spec.md`, `data_lifecycle.md`
- `AI/docs/goal_session_spec.md`, `DEVELOPMENT_RULES.md`, `docs/DATA_PRIVACY.md`

## 결정 GA-031 - 목표 후보 검증·Repair 제한 병렬 실행

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (Live 지연·Rate Limit 검증 필요)

### 결정 내용

추천 후보별 JEV 검증과 실패 후보별 OpenAI Repair를 서로 독립된 항목끼리 병렬 실행한다. 동시 호출 수는 응답 계약의 후보 최대 개수인 3개를 넘지 않는다. 결과 순서는 입력 후보 순서에 맞춰 보존하며, 일부 병렬 요청 실패는 기존과 같이 Workflow 실패로 처리한다.

`NEEDS_QUESTION`의 질문 생성은 현재 OpenAI 구조화 요청 1회이므로 이 변경의 병렬화 대상이 아니다. 질문 생성 지연은 실제 요청 시간을 별도로 측정한 뒤 후속 최적화를 결정한다.

### 결정 이유

후보별 JEV 검증과 Repair는 서로의 결과를 기다릴 필요가 없으며, 기존 순차 실행에서는 최대 3회의 네트워크 대기가 누적된다. 제한된 병렬 실행으로 사용자 대기 시간을 줄이되, 후보 수와 동시 요청을 제한해 요청 폭증을 막는다.

### 영향 범위

- `AI/src/workflow/goal_assistance.py`
- `AI/tests/test_goal_workflow.py`
- `AI/docs/features/goal-assistance-PLAN.md`, `CONTEXT.md`, `NEXT_TASK.md`

## 결정 GA-032 - Streamlit 질문 답변의 목표 입력 반영

- 결정일: 2026-10-02
- 담당 영역: AI (로컬 Streamlit 테스트 화면)
- 상태: 구현 (사용성 Live 확인 필요)

### 결정 내용

사용자가 Streamlit 확인 질문에 답하면 해당 답변 문구를 현재 목표 입력값 뒤에 반영하고, 업데이트된 목표 문장으로 다음 JEV 판단을 실행한다. 같은 답변이 이미 목표 문장에 포함된 경우 중복 추가하지 않는다. 사용자는 업데이트된 목표 문장을 계속 수정할 수 있으며, 기존과 같이 `CLEAR` 결과를 별도로 최종 확인하기 전에는 저장하지 않는다.

### 결정 이유

질문 답변과 목표 입력란이 분리되어 보이는 문제를 줄이고, 사용자가 자신의 답변이 목표에 어떻게 반영되었는지 확인·수정할 수 있게 한다. 최종 목표의 승인은 여전히 사용자에게 남긴다.

### 영향 범위

- `AI/streamlit_app.py`
- `AI/tests/test_streamlit_app.py`
- `AI/docs/features/goal-assistance-PLAN.md`, `CONTEXT.md`, `NEXT_TASK.md`

## 결정 GA-033 - 확인 질문 생성 2건 제한 병렬 경주

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (실제 지연·비용 평가 필요)

### 결정 내용

`NEEDS_QUESTION`, `UNRECOGNIZED_TERM` 및 검증 실패 fallback의 확인 질문 생성에서 동일한 구조화 OpenAI 요청을 최대 2건 동시에 실행한다. 먼저 도착한 유효한 질문 하나만 응답에 포함하고 나머지 대기 작업은 취소한다. 이전에 이미 답한 질문은 선택하지 않고 다른 결과를 기다린다. 두 결과가 모두 이전 질문을 반복하면 응답 검증 오류로 처리한다. `OPENAI_QUESTION_PARALLELISM=1`로 단일 호출을 선택할 수 있다.

### 결정 이유

이전 결정 GA-031은 후보 검증과 Repair만 병렬화했으며 질문 생성 지연에는 직접 영향을 주지 않았다. 사용자가 요청한 질문 생성 속도 개선을 실제 경로에 적용한다. 외부 제공자가 취소된 요청을 이미 처리했을 수 있어 호출량과 비용은 최대 두 배까지 늘 수 있다. 속도 향상도 Live 측정 전에는 확정할 수 없다.

### 영향 범위

- `AI/src/models/llm_client.py`, `AI/src/config.py`, `AI/src/prompts/goal_prompt.py`
- `AI/tests/test_model_clients.py`, `AI/.env.example`
- 목표 보조 기획·결과·컨텍스트·다음 작업 문서

## 결정 GA-034 - 확인 질문 스트리밍과 4.5초 제한 시간

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (Live 지연·비용·질문 품질 검증 필요)

### 결정 내용

Streamlit에서 OpenAI Responses 스트림의 질문 문구를 초안으로 순차 표시한다. 최종 응답은 기존 `ClarificationQuestion` Schema 검증을 통과해야 선택지로 표시한다. 질문 생성은 기본 4.5초 안에 유효한 결과가 없으면 상태에 맞는 로컬 기본 질문을 반환한다. 두 요청이 모두 일시 실패·Schema 오류·기존 질문 반복인 경우도 로컬 기본 질문을 사용하며, 설정·인증 오류는 표시한다. 이로써 결정 GA-033의 반복 질문 검증 오류 처리를 대체한다. 이 제한 시간에는 최초 JEV 판단과 Streamlit 렌더링은 포함하지 않는다. 병렬 생성 두 요청 중 한 건은 스트리밍하고 다른 건은 기존 구조화 호출로 유지한다.

### 결정 이유

사용자는 완성된 JSON을 기다리는 동안 아무 내용도 볼 수 없었다. 초안을 실제 토큰에 맞춰 보여주고 생성 시간을 제한하면 대기 상태를 이해할 수 있으며, 지연 시에도 답변 가능한 질문을 제공할 수 있다. 초안은 최종 질문과 달라질 수 있고 로컬 기본 질문은 모델이 생성한 문구가 아니다.

### 영향 범위

- `AI/src/models/llm_client.py`, `fakes.py`, `AI/src/workflow/goal_assistance.py`
- `AI/streamlit_app.py`, `AI/src/config.py`, `AI/.env.example`
- 목표 보조 기획·결과·컨텍스트·다음 작업 문서

## 결정 GA-035 - 5초 성능 목표와 10초 이후 기본 질문 전환 분리

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (Live P50·P95와 비용 검증 필요)

### 결정 내용

결정 GA-034의 4.5초 강제 전환을 대체한다. 질문 생성 5초는 최적화 목표이지 유효한 모델 응답을 취소할 시점이 아니다. 병렬 생성과 Streamlit 질문 초안 스트리밍은 유지하고, 유효한 모델 질문은 먼저 완성되는 즉시 반환한다. 규칙 기반 기본 질문은 생성 시작 후 기본 10초 이상 유효한 모델 질문이 없을 때만 반환한다. 두 모델 호출이 일찍 실패하거나 기존 질문만 반복해도 10초 전에는 기본 질문을 표시하지 않는다. 설정·인증 오류는 기다리지 않고 표시한다. 이 기준은 질문 생성 단계에만 적용한다.

### 결정 이유

4.5초에 무조건 전환하면 조금 늦게 도착할 양질의 모델 질문을 버릴 수 있다. 초기 문구를 스트리밍으로 보여 주면서 5초 목표를 별도로 측정하고, 기본 질문은 충분히 기다린 뒤 최후 수단으로 사용한다.

### 영향 범위

- `AI/src/models/llm_client.py`, `AI/src/config.py`, `AI/.env.example`
- 질문 생성 테스트, Streamlit 실행 안내, 목표 보조 기획·결과·컨텍스트·다음 작업 문서

## 결정 GA-036 - 관련성 Hybrid 우선 평가와 OCR·이탈 입출력 책임 정리

- 결정일: 2026-10-11
- 담당 영역: AI·Server·Desktop·Extension 협의 필요
- 상태: 제안 (팀원 평가 산출물·최신 Notion 본문·Backend 필드 합의 확인 전)

### 결정 내용

팀원이 보고한 120건 비교에서 Hybrid는 99건 정답(82.5%), Macro F1 0.732로 여섯 후보 중 가장 높았다. 관련성 분류의 다음 구현·재평가에서는 Hybrid를 우선 후보로 다룬다. 구성 신호·결합식·가중치·임계값은 아직 확인되지 않았으며, 현재 코드에 Hybrid 분류기가 구현된 것으로 기록하지 않는다. 결정 005의 단계적 처리 구조를 이 보고만으로 폐기하거나 새 운영 임계값으로 대체하지 않는다.

OCR 전달은 개인정보 검사를 통과한 `text`와 `ocr` 메타데이터를 구분하는 제안형으로 정리한다. AI 전처리가 최종 `qualityScore`와 정제 콘텐츠 `contentHash`를 생성하고, 이벤트 중복·최신성은 Server/Client가 담당한다. 이탈·복귀 판단은 최종 화면 관련성 출력의 라벨·confidence를 그대로 소비하며 품질점수·유사도로 덮어쓰지 않는다. Backend가 검토할 9개 저장 컬럼과 정확한 내부 요청 Schema는 확정하지 않는다. ColPali는 현 MVP 구현 범위에서 제외하고 후속 PoC로 유지한다.

### 결정 이유

팀원 메시지의 비교 결과와 입출력 정리 요청을 저장소에 남기되, 재현 자료가 없는 수치를 확정 품질 또는 배포 완료로 오인하지 않기 위해서다. 기존 OCR·이탈 기능 페이지의 본문은 요청 내용과 아직 다르게 보이므로 교차 검증이 필요하다.

### 영향 범위

- `AI/docs/evaluation_spec.md`, `analysis_rules.md`, `content_acquisition_spec.md`, `api_spec.md`
- `AI/docs/CONTEXT.md`, `NEXT_TASK.md`

## 결정 GA-037 - 목표 확인 요청 필드와 대화 ID·재시도 책임 일치

- 결정일: 2026-10-25
- 상태: 전달받은 팀 메시지에 따른 AI 내부 구현 기준; Backend·Desktop 통합 미검증
- 관련 Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)
- 입력은 `goalText`, `conversationTurn`, 선택적 `clarificationSessionId`, `previousAnswers[{question,answer}]`로 통일한다. AI 내부 경로에는 `requestId`를 추가하며 공개 경로를 AI에 중복 생성하지 않는다. 내부 Python 이름·이전 JSON 입력 별칭은 이행 기간 호환하고 직렬화는 새 이름만 사용한다.
- Backend가 대화 ID의 발급·소유권·회차·최종 목표 연결을 관리하고 AI는 전달받은 ID만 반환한다. AI는 대화 상태를 저장하지 않는다. Streamlit에서만 가상 ID를 메모리에 발급해 흐름을 시험한다.
- 회차는 0~2이며 완료된 답변 수와 일치한다. 두 번 답한 뒤 명확하지 않으면 세 번째 AI 질문을 생성하지 않고 직접 수정·확인을 요청한다. 기존 응답 상태값과 나머지 필드는 유지한다.
- 자동 요청 재시도는 Backend가 retryable 오류에 1초→3초, 최대 2회 수행한다. 같은 대화 ID·회차·답변을 유지하며 Desktop은 자동 재전송하지 않는다. AI HTTP 경로의 제공자 SDK 재시도는 0, 기존 병렬 후보 생성·의미 검수 Repair는 별도다.
- Notion 연결 도구의 404 때문에 이번에는 최신 3.2 본문을 확인하지 못했다. 사용자가 직접 전달한 계약을 변경 근거로 기록한다.

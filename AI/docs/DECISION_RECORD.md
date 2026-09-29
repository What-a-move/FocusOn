# AI 결정 기록

> AI 분석 기준·모델·프롬프트·데이터 처리 결정을 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

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

## 결정 015 - 목표 보조의 JEV 판단과 OpenAI 생성 분리

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

## 결정 016 - 로컬 목표 보조의 Vercel AI Gateway 단일 Key 경로

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

로컬 목표 보조 테스트는 `AI_GATEWAY_API_KEY` 하나가 설정되면 Vercel AI Gateway를 우선 사용한다. JEV는 TypeSafe 호환 Base URL과 `typesafe-ai/jev`, 자연어 생성은 OpenAI 호환 Base URL과 `openai/gpt-5-mini`를 사용한다. Gateway Key가 없으면 기존 TypeSafe·OpenAI 직접 제공자 Key를 사용하는 호환 경로를 유지한다.

### 결정 이유

TypeSafe 또는 별도 Jev 서비스의 Key·잔액과 호출 주소가 맞지 않아 발생하는 인증 오류를 제거하고, 판단과 생성 모델의 로컬 테스트 인증을 한 Key로 단순화하기 위해서다. 모델 역할 분리는 결정 015를 그대로 유지하며 Gateway는 호출·과금 경로만 통합한다.

### 영향 범위

- `AI/src/config.py`
- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/streamlit_app.py`
- `AI/.env.example`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 017 - Vercel JEV 추론 권한은 실제 요청 결과로 안내

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

## 결정 018 - 목표 보조는 TypeSafe·OpenAI 공식 API를 직접 사용

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

## 결정 019 - 시작 가능한 학습 목표의 명확성 기준 완화

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

## 결정 020 - 바로 시작 가능성 Noul을 명확성 라우팅에 사용

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

## 결정 021 - 한국어 목표 구분용 JEV 판단 기준

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

## 결정 022 - CLEAR는 바로 시작 가능성과 다른 판단을 함께 만족해야 함

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

## 결정 023 - 목표 정보 수준 Choice로 넓은 목표와 정보 부족 목표를 분리

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정
- 대체: 결정 020·022의 `CLEAR` 라우팅 규칙과 결정 021의 최초 질문 개수

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

## 결정 024 - 학습 목표 성립 판단과 짧은 생성의 지연을 분리

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

## 결정 025 - Laya는 목표 설정의 로컬 비교 판단으로만 사용

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

## 결정 026 - Laya 기본 체크포인트의 목표 분류 수치를 보정 전 원시값으로 표시

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

## 새 결정 기록

## 결정 027 - JEV 최초 목표 판단 계약으로 복원

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

## 새 결정 기록

## 결정 028 - 목표 명확성·추천 Score 기준 하향

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

## 새 결정 기록

다음 결정은 `결정 029`부터 추가한다.

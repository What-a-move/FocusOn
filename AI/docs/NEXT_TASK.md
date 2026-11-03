# AI 다음 작업

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: MVP 분석 계약 리뷰 및 Issue #12 코드와 후속 관련성 구현의 정합성 확인
- 담당 영역: AI
- 우선순위: 높음
- 상태: Issue #16 문서 계약 반영, 팀 승인·구현 검증 전

## 작업 목적

Server와 AI 사이 호출 경로·Schema·상태 투영을 확정하고 Issue #12의 목표 보조 Branch와 문서 계약을 비교한다. 관련성·카메라 분석은 아직 구현되지 않았으므로 문서의 MVP 정책을 코드 완료로 표현하지 않는다.

## 선행 조건

- 루트 `docs/API_CONTRACT.md` 확인
- `DEVELOPMENT_RULES.md`, `state_model.md`, `data_lifecycle.md`, `evaluation_spec.md` 확인
- `goal_session_spec.md`의 식별자·회차·멱등·시간 집계 경계 확인
- `content_acquisition_spec.md`의 AI 입력과 비분석 상태 계약 확인
- `focus_session_agent_spec.md`, `feedback_personalization_spec.md`, `session_note_spec.md`는 후속 구현 경계로 사용
- Desktop·Extension에서 전달할 최소 데이터 확인
- Server의 AI 요청·결과 저장 범위 확인
- 원본 화면·카메라 영상 미저장 원칙 확인
- Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로, AI 서버의 원본 이미지 비수신 경계 확인
- Chrome Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로를 분리 확인
- 목표 보조 4개 `decision`, 최대 2회 질문·수동 확정 경로 확인
- `EXCLUDED`와 `PRIVACY_BLOCKED`, `sessionStatus` 계약 확인
- AI 내부 라벨과 제품 표시 라벨 매핑 확인
- 개인화 휴식의 최근 7일 규칙과 카메라 비필수 경로 확인
- ColPali는 기본 서버 구조에 바로 포함하지 않고 별도 PoC·평가로 분리
- 목표 입력 2~40자·대화당 질문 최대 2회·복수 목표 단일 선택을 #12 구현과 비교
- `text`·`ocr` 전달, AI 전처리 `qualityScore`·`contentHash`, Backend 검토 9개 컬럼 합의

## 예상 작업 순서

1. 현재 공개 관련성 경로 `/api/v1/sessions/{sessionId}/analysis-runs`와 내부 AI 경계를 Server·Client 담당자와 확정한다. Notion `POST /api/v1/goals/clarify`의 네 판정값·최대 2회 질문·장애 시 수동 확정도 확인한다. `/api/v1/feedback`은 공개 MVP 경로가 없는 후속 제안으로 유지한다.
2. AI 내부 라벨과 제품 표시 라벨, 기존 `FocusState` 호환 전략을 공유 타입 담당자와 확정한다.
3. `goalId`, `sessionId`, `eventId`, `navigationId`와 `sessionStatus`, `analysisStatus`의 필수 범위를 확인한다.
4. 목표 보조 4개 판정값과 AI 장애 시 직접 목표 확정 경로를 확인한다.
5. FastAPI, Pydantic, pytest와 서비스 인증·설정 주입 방식을 확정한다.
6. Health Check, 공통 성공·실패 envelope, Schema 검증 테스트를 구현한다.
7. 로그에 원문·비밀 값이 남지 않는지 검사한다.
8. 관련성 분석, Agent, 화면 판정 피드백, 노트, ColPali PoC를 별도 Issue로 진행한다. 화면 판정 피드백과 ColPali는 MVP에 포함하지 않는다.

## 이후 작업 후보

- 팀원 보고 120건 Hybrid 평가의 사례·정답·예측·클래스별 지표·결합식·재현 코드를 받아 독립 holdout과 지연·비용을 검증한다. 현재 결과를 배포 성능으로 표현하지 않는다.
- 화면 `UNRELATED`와 시간 기반 `driftState`를 분리하고, `RELATED` 초기화·`UNCERTAIN` 비누적 및 연속 구간 종료·15초 미만 제외와 `analysis_rules.md` 9.1의 정책 `v1` 연속/반복 OR 조건을 계약 테스트한다. 임계값 숫자와 실제 개입 판정은 Server가 관리한다.
- 카메라 60초 후보·90초 최종 게이트, 최신 화면 `RELATED` 억제, `EXCLUDED`/`PRIVACY_BLOCKED` 분기를 통합 테스트한다.

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

- 2026-10-25 전달받은 계약대로 Backend 공개 `POST /api/v1/goals/clarify` DTO와 내부 AI 어댑터를 연결한다. 공개 입력은 `goalText`, `conversationTurn`, 선택적 `clarificationSessionId`, `previousAnswers[{question,answer}]`; 내부 AI 호출에는 `requestId`를 추가한다. Backend가 첫 대화 ID를 발급하고 소유권·유효성·권위 있는 회차를 확인한 뒤 공개 응답에 돌려주며 `POST /api/v1/goals`의 같은 ID를 확인한다. AI 응답의 ID echo는 인증·상태 저장을 대체하지 않는다.
- Desktop의 자동 재전송은 사용하지 않는다. Backend가 retryable 일시 실패에만 1초·3초 후 최대 2회 재시도하고 같은 ID·회차·답변을 유지한다. AI HTTP 경로의 제공자 SDK 재시도 0 설정은 반영했으나 Backend 재시도·Desktop/Server 통합은 해당 영역에서 구현·검증해야 한다.
- 로컬 `AI/.env`에 `TYPESAFE_API_KEY`와 `ANTHROPIC_API_KEY`를 설정한 뒤 JEV 판단 표, 질문·추천 생성, 후보 JEV 병렬 검수·Repair의 Live 흐름을 각각 최소 1회 검증하고 응답 지연·Rate Limit·실패 처리를 확인한다. 이때 `React Query에서 캐시 무효화 구현하기`와 구체성 2.00 이상 목표는 `CLEAR`, 구체성 1.00 이상 2.00 미만 목표는 `NEEDS_SUGGESTION`, 핵심 주제 또는 활동이 없는 목표는 `NEEDS_QUESTION`, 한국어 약어·오타·다중 목표·무효 입력은 기존 우선순위 상태인지 확인한다. 질문 답변 뒤 `interpretedGoal`과 추천 제목이 답변의 주제·활동·범위를 반영하는지도 확인한다.
- Streamlit에서 선택지·직접 입력 답변 후 목표 입력값이 한 번만 갱신되는지, 갱신된 문장을 사용자가 편집해 재분석할 수 있는지 Live UI에서 확인한다.
- 확인 질문 생성의 병렬 2건과 단일 호출 설정을 동일한 테스트 문장으로 비교해 P50·P95 지연시간, 질문 반복률, Claude 호출량·비용·Rate Limit을 기록한다. 실제 측정 전에는 속도 개선을 확정 성과로 기록하지 않는다.
- Live Streamlit에서 첫 질문 문구 표시 시각, 유효한 최종 질문 표시 시각, 10초 이후 기본 질문 전환, 취소된 병렬 호출의 비용·연결 정리를 확인한다. 5초는 강제 종료가 아닌 성능 목표로 P50·P95를 측정하고, 첫 JEV 판단과 화면 렌더링을 포함한 전체 체감 시간은 별도로 기록한다.
- `laya` 패키지와 `convaiinnovations/laya-multilingual` 체크포인트를 설치한 뒤 비교 표가 같은 네 판단 항목을 표시하는지 확인한다. Laya 응답·실패 여부와 무관하게 JEV `clarityStatus`, 질문·추천, 저장 경로가 동일한지 확인한다.
- Laya Multilingual 기본 모델은 목표 분류 Live sanity check에서 단일 목표도 복수 목표로 높게 응답해, 현재 값은 원시 실험값으로만 표시한다. 제품 판단에 사용하려면 한국어 목표 라벨 데이터로 fine-tune·확률 보정·holdout 평가를 별도 수행한다.

- 위 브랜치 실행 이력과 공통 공개 계약을 대조해 4종 응답 변환·목표 길이·인증을 담당자와 확인한 뒤 연결한다. PR 충돌 해소 자체를 통합 완료로 간주하지 않는다.

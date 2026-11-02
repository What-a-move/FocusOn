# AI 다음 작업

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Issue #12 목표 설정 보조 Server 연동 계약 확정
- 담당 영역: AI
- 우선순위: 높음
- 상태: AI 내부 구현 완료, 연동 전 검토 필요

## 작업 목적

구현된 `POST /internal/v1/goals/clarify`를 Server가 안전하게 호출하도록 서비스 인증, Timeout 예산, 배포 환경과 공개 API 변환 계약을 확정한다. AI는 목표 저장·세션 시작·사용자 확정을 담당하지 않는다.

## 선행 조건

- 2026-10-25 전달받은 계약대로 Backend 공개 `POST /api/v1/goals/clarify` DTO와 내부 AI 어댑터를 연결한다. 공개 입력은 `goalText`, `conversationTurn`, 선택적 `clarificationSessionId`, `previousAnswers[{question,answer}]`; 내부 AI 호출에는 `requestId`를 추가한다. Backend가 첫 대화 ID를 발급하고 소유권·유효성·권위 있는 회차를 확인한 뒤 공개 응답에 돌려주며 `POST /api/v1/goals`의 같은 ID를 확인한다. AI 응답의 ID echo는 인증·상태 저장을 대체하지 않는다.
- Desktop의 자동 재전송은 사용하지 않는다. Backend가 retryable 일시 실패에만 1초·3초 후 최대 2회 재시도하고 같은 ID·회차·답변을 유지한다. AI HTTP 경로의 제공자 SDK 재시도 0 설정은 반영했으나 Backend 재시도·Desktop/Server 통합은 해당 영역에서 구현·검증해야 한다.
- 로컬 `AI/.env`에 `TYPESAFE_API_KEY`와 `OPENAI_API_KEY`를 설정한 뒤 JEV 판단 표, 질문·추천 생성, 후보 JEV 병렬 검수·Repair의 Live 흐름을 각각 최소 1회 검증하고 응답 지연·Rate Limit·실패 처리를 확인한다. 이때 `React Query에서 캐시 무효화 구현하기`와 구체성 2.00 이상 목표는 `CLEAR`, 구체성 1.00 이상 2.00 미만 목표는 `NEEDS_SUGGESTION`, 핵심 주제 또는 활동이 없는 목표는 `NEEDS_QUESTION`, 한국어 약어·오타·다중 목표·무효 입력은 기존 우선순위 상태인지 확인한다. 질문 답변 뒤 `interpretedGoal`과 추천 제목이 답변의 주제·활동·범위를 반영하는지도 확인한다.
- Streamlit에서 선택지·직접 입력 답변 후 목표 입력값이 한 번만 갱신되는지, 갱신된 문장을 사용자가 편집해 재분석할 수 있는지 Live UI에서 확인한다.
- 확인 질문 생성의 병렬 2건과 단일 호출 설정을 동일한 테스트 문장으로 비교해 P50·P95 지연시간, 질문 반복률, OpenAI 호출량·비용·Rate Limit을 기록한다. 실제 측정 전에는 속도 개선을 확정 성과로 기록하지 않는다.
- Live Streamlit에서 첫 질문 문구 표시 시각, 유효한 최종 질문 표시 시각, 10초 이후 기본 질문 전환, 취소된 병렬 호출의 비용·연결 정리를 확인한다. 5초는 강제 종료가 아닌 성능 목표로 P50·P95를 측정하고, 첫 JEV 판단과 화면 렌더링을 포함한 전체 체감 시간은 별도로 기록한다.
- `laya` 패키지와 `convaiinnovations/laya-multilingual` 체크포인트를 설치한 뒤 비교 표가 같은 네 판단 항목을 표시하는지 확인한다. Laya 응답·실패 여부와 무관하게 JEV `clarityStatus`, 질문·추천, 저장 경로가 동일한지 확인한다.
- Laya Multilingual 기본 모델은 목표 분류 Live sanity check에서 단일 목표도 복수 목표로 높게 응답해, 현재 값은 원시 실험값으로만 표시한다. 제품 판단에 사용하려면 한국어 목표 라벨 데이터로 fine-tune·확률 보정·holdout 평가를 별도 수행한다.
- 루트 `docs/API_CONTRACT.md` 확인
- `DEVELOPMENT_RULES.md`, `state_model.md`, `data_lifecycle.md`, `evaluation_spec.md` 확인
- `goal_session_spec.md`의 식별자·회차·멱등·시간 집계 경계 확인
- `content_acquisition_spec.md`의 AI 입력과 비분석 상태 계약 확인
- `focus_session_agent_spec.md`, `feedback_personalization_spec.md`, `session_note_spec.md`는 후속 구현 경계로 사용
- Desktop·Extension에서 전달할 최소 데이터 확인
- Server의 AI 요청·결과 저장 범위 확인
- 원본 화면·카메라 영상 미저장 원칙 확인
- DOM 우선·Apple Vision OCR 보조 경로와 AI 서버의 원본 이미지 비수신 경계 확인
- ColPali는 기본 서버 구조에 바로 포함하지 않고 별도 PoC·평가로 분리
- 관련 기능의 PLAN·ERROR·REPORT 파일 준비

## 예상 작업 순서

1. Server→AI 서비스 인증과 내부 네트워크 경계를 확정한다.
2. Server 공개 `POST /api/v1/goals/clarify`와 내부 응답 변환을 확정한다.
3. 전체 Timeout·Retry·멱등성 예산과 `requestId` 전달 규칙을 확정한다.
4. `AI-PRIV-003` 외부 제공자·지역·보존·학습 이용 조건 승인을 완료한다.
5. 승인된 비식별 평가 데이터로 JEV Threshold와 OpenAI 생성 품질을 조정한다.
6. 통합·배포 환경에서는 `LANGSMITH_TRACE_RAW_GOAL_TEXT=false`를 강제하고, 로컬 수동 테스트용 LangSmith raw trace 예외가 실제 사용자 입력에 적용되지 않는지 다시 검사한다.

## 이후 작업 후보

- 별도 관련성 구현 Issue에서 팀원이 보고한 120건 평가의 사례·정답·예측·클래스별 지표와 Hybrid 구성/결합식을 받아 재현한다. 동일 데이터 튜닝과 독립 holdout 평가를 구분하고 지연·비용·confidence 보정을 확인한 뒤 구현한다. 현재 Hybrid 코드는 없다.
- OCR/이탈 연동 Issue에서 Client→Server→AI의 `text`·`ocr` 하위 Schema, AI 전처리 `qualityScore`·`contentHash` 출력 위치, Backend 검토 예정 9개 저장 컬럼을 담당자와 확정한다. 관련성 최종 라벨·confidence는 이탈 정책에서 재분류하지 않도록 계약 테스트한다.
- 팀원이 수정했다고 전한 6개 Notion 담당 기능 페이지의 실제 게시 본문을 재확인한다. 현재 브라우저 표시본은 카메라 30초 알림·목표 질문 무제한·ColPali 현행 경로·이전 주 기준 휴식 추천 등 팀원 메시지와 충돌한다. 게시/동기화 여부를 확인하기 전에는 Markdown을 해당 구버전으로 되돌리지 않는다.
- 개인화 휴식 추천은 팀원 메시지의 최근 7일·카메라 비필수·미루기/끄기 방향을 담당 기능 기획서와 확인한다. 이는 MVP에서 제외된 **화면 관련성 판정 수정·저장 기반 개인화**와 다른 기능이므로 두 계약을 합치지 않는다.
- Server 관련성 분석 요청·결과 저장 연동
- DOM·Apple Vision OCR 왕복 PoC와 실패 경로 검증
- ColPali 시각 콘텐츠 기준선 비교 PoC
- 페이지 이동·체류 시간 기반 학습 흐름·목표 이탈 분석
- MediaPipe 얼굴·시선·자세 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동
- 평가 자동화와 모델·Prompt Version 관리

## MVP 분석 정책 구현 전 확인 사항

현재 Notion AI 분석 계약을 Markdown 기준 문서에 동기화했지만 화면·흐름·카메라 분석 구현은 완료되지 않았다. 해당 기능의 구현 Issue를 시작하기 전에 다음을 계약·코드·테스트에서 함께 확인한다.

- 목표 입력은 2~40자 계약과 현재 200자 설정을 추가 정합화한다. 답변 최대 2개·회차 0~2·세 번째 질문 차단은 반영했으며 Backend가 대화 ID별 회차와 이력을 검증해야 한다.
- 관련성 confidence `0.75` 기준, 15초 미만 전환 제외, GENTLE/NORMAL/ACTIVE 이탈 정책, `RELATED` 초기화와 `UNCERTAIN` 보류·종료 동작을 테스트한다.
- 카메라 60초 후보·90초 최종 게이트, 최신 화면 `RELATED` 억제, `EXCLUDED`/`PRIVACY_BLOCKED` 분기를 통합 테스트한다.
- 화면 관련성 판정 수정·삭제·저장·개인화 API 및 피드백 적재는 MVP에서 구현하지 않는다.
- 구현 시작 전 각 담당 Issue를 만들고 공유 상태/API·개인정보 경계를 승인받는다.
- 화면 관련성 `UNRELATED`는 이탈 임계값 충족 전에도 콘텐츠 관계 결과로 유지하고, `driftState`·알림만 시간 정책에 따라 별도로 변한다. OCR 품질·임베딩 유사도로 최종 관련성 라벨이나 confidence를 덮어쓰지 않는 테스트를 추가한다.

## 완료 조건

- [x] 기능 기획서를 작성했다.
- [x] AI 내부 입력·출력 계약을 문서화했다.
- [x] Health Check와 공통 응답 Schema가 동작한다.
- [ ] 다중 상태 또는 호환 상태 계약이 문서와 Schema에서 일치한다.
- [x] 외부 모델 없이 Fake 기반 테스트가 실행된다.
- [x] 개인정보·민감 데이터 제외 기준을 확인했다.
- [x] 실행 명령과 의존성을 문서화했다.
- [x] 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DEVELOPMENT_RULES.md`와 적용 Rule ID를 확인했다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] Desktop·Extension·Server 담당자와 계약을 공유했다.

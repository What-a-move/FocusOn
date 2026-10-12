# Backend 개발 불변 규칙

규칙이 바뀌면 이 문서와 `DECISION_RECORD.md`를 함께 갱신한다.

## BE-001 계약 원본

- 엔드포인트별 최종 계약은 Notion `FocusOn API 명세서`를 따른다.
- 공통 오류와 세션 전이는 Notion `공통 Error Code`, `세션 상태 정책`을 따른다.
- 로컬 요약은 루트 `docs/API_CONTRACT.md`에서 관리한다.
- 폐기된 예전 Notion `API 명세` 페이지는 사용하지 않는다.

## BE-002 응답과 오류

- 성공 응답은 `{ "data": ... }` 형식이며 `204`는 본문이 없다.
- 실패 응답은 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details`를 최상위에 둔다.
- 클라이언트는 `code`로 분기한다.
- 새 오류 코드는 Notion 공통 정책에 먼저 정의하고 구현한다.

## BE-003 계층 책임

- Controller: 요청·응답 변환과 입력 검증
- Service: 비즈니스 로직과 트랜잭션 경계
- Repository: 데이터 접근

Controller에 상태 전이와 정책 판단을 작성하지 않는다.

## BE-004 인증과 동시성

- 인증 API는 `Authorization: Bearer {token}`을 사용한다.
- Desktop·Extension 요청은 명세에 따라 `X-Device-Id`를 함께 사용한다.
- 세션 생성·일시정지·재개·연장·종료는 `Idempotency-Key`를 사용한다.
- 기존 세션 상태 변경은 `If-Match-Version`도 검증한다.
- Heartbeat는 두 동시성 헤더를 사용하지 않는다.

## BE-005 세션 시간

- 서버가 세션 상태와 기준 시간을 관리한다.
- 활성 시간은 `accumulatedActiveMs`, 목표 시간은 `targetDurationMs`로 표현한다.
- `targetDurationMs = null`은 시간 기준 자동 종료 없이 사용자가 직접 종료하는 무기한 세션이다.
- Heartbeat는 10초 간격, 무신호 60초를 `ABANDONED` 후보 기준으로 사용한다. 마지막 유효 heartbeat 뒤 3분까지 복구되지 않으면 `HEARTBEAT_TIMEOUT` 사유로 원자적 자동 종료한다.
- 현재 RUNNING 구간의 첫 유효 heartbeat 전에는 `lastResumedAt`을 무신호 시간 계산 기준으로 사용한다. 최초 시작에서는 `lastResumedAt = startedAt`이다.
- 한 사용자에게 `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING` 상태 세션은 하나만 허용한다.
- Desktop 재실행 시 마지막 서버 확정 `accumulatedActiveMs`만 인정하고 `AUTO_PAUSED`, `pauseReason: APP_RESTART`로 복구한다. 사용자 확인 없는 자동 재개는 금지한다.

## BE-006 분석·개입 정책

- `confidence < 0.75`는 `UNCERTAIN`으로 정규화한다.
- `UNCERTAIN`은 이탈 시간에 합산하지 않는다. 30초 이하는 기존 구간을 보류하고, 30초를 초과하면 기존 구간을 종료한다.
- `RELATED`가 확인되면 기존 이탈 구간과 카메라 알림 후보를 즉시 초기화한다. 15초 미만의 짧은 화면 전환은 이탈 구간으로 집계하지 않는다.
- 개입 임계값은 `GENTLE` 120초 연속, `NORMAL` 최근 10분 안의 30초 이상 구간 2회, `ACTIVE` 30초 연속이다.
- cooldown은 `sessionId + interventionType`별 5분이다.
- 정책 버전은 세션 시작 시 `interventionPolicyVersion`으로 고정하고 `analysis_runs.policy_version`, `intervention_events.policy_version`에 같은 값을 저장한다.

## BE-007 재시도와 속도 제한

- 상태 명령은 동일한 idempotency key가 있을 때만 재시도한다.
- 분석 요청은 `clientRunId`로 중복을 막는다.
- `429`에는 `Retry-After`를 반환한다.
- 수치는 `docs/API_CONTRACT.md`의 표와 Notion 계약을 따른다.

## BE-008 개인정보와 로그

- `docs/DATA_PRIVACY.md`를 따른다.
- 토큰, API key, 연결 코드, 원본 화면·카메라 데이터, 원문 콘텐츠를 로그·오류 details·개발 오류 문서에 남기지 않는다.
- 로그에는 requestId, 안전한 리소스 식별자, 기능명, 오류 코드 정도만 남긴다.

## 확인표

| 규칙 | PR에서 확인할 내용 |
| --- | --- |
| BE-001 | Notion 최종 명세와 경로·필드·권한 일치 |
| BE-002 | 성공·오류 형식과 HTTP 상태 일치 |
| BE-003 | 계층 책임과 트랜잭션 경계 |
| BE-004 | 인증·기기·idempotency·version 헤더 |
| BE-005 | 세션 전이와 시간 누적 |
| BE-006 | 개입 임계값·구간 처리·정책 버전 |
| BE-007 | 재시도 가능 조건과 rate limit |
| BE-008 | 민감 정보 비노출 |

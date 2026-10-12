# FocusOn API 계약

이 문서는 Desktop, Extension, Server, AI가 구현 중 빠르게 확인할 공통 계약 요약이다. 엔드포인트별 Request, Response, 권한, 예외의 최종 원본은 Notion **FocusOn API 명세서** 데이터베이스다.

## 문서 우선순위

1. Notion `FocusOn API 명세서`: 엔드포인트별 최종 계약
2. Notion `공통 정책 · 기술 계약`의 `공통 Error Code`, `세션 상태 정책`: 공통 오류와 상태 전이
3. 이 문서: 로컬 개발용 요약

서로 다르면 위 순서로 판단하고 같은 작업에서 로컬 문서도 갱신한다. 폐기된 예전 Notion `API 명세` 페이지는 기준으로 사용하지 않는다.

## 공통 형식

- Base path: `/api/v1`
- 필드 이름: lower camel case
- 식별자: 문자열(UUID 포함)
- 날짜와 시각: UTC ISO 8601 문자열
- 조회 기준 일자는 필요할 때 `timeZone`과 함께 전달한다.

성공 응답은 엔드포인트 명세의 스키마를 `data`에 담는다.

```json
{
  "data": {}
}
```

`204 No Content`는 본문을 반환하지 않는다.

실패 응답은 별도 래퍼 없이 다음 형식을 사용한다.

```json
{
  "code": "SESSION_VERSION_CONFLICT",
  "message": "세션 상태가 변경되었습니다.",
  "retryable": false,
  "retryAfterSeconds": null,
  "requestId": "req_01...",
  "details": {}
}
```

클라이언트는 `code`로 분기하고 `message` 문구에 의존하지 않는다. 토큰, 연결 코드, 원본 화면·카메라 데이터 등 민감 값은 응답의 `details`와 로그에 넣지 않는다.

## 요청 헤더

| 헤더 | 적용 |
| --- | --- |
| `Authorization: Bearer {accessToken}` | 인증 API |
| `X-Device-Id: {deviceId}` | Desktop·Extension의 인증 요청 |
| `Idempotency-Key: {key}` | 세션 생성·일시정지·재개·연장·종료 |
| `If-Match-Version: {version}` | 기존 세션의 일시정지·재개·연장·종료 |

Heartbeat에는 `Authorization`과 `X-Device-Id`만 사용한다. `Idempotency-Key`와 `If-Match-Version`을 보내지 않는다.

## 엔드포인트 목록

### 인증·기기 연결

```text
POST   /api/v1/auth/google/exchange
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
GET    /api/v1/devices
DELETE /api/v1/devices/{deviceId}
POST   /api/v1/pairing-codes
POST   /api/v1/extension-pairings
DELETE /api/v1/extension-pairings/{extensionDeviceId}
```

### 사용자·설정

```text
GET /api/v1/users/me
PUT /api/v1/users/me/profile
PUT /api/v1/users/me/onboarding-readiness
GET /api/v1/users/me/consents
PUT /api/v1/users/me/consents/{consentType}
GET /api/v1/users/me/focus-settings
PUT /api/v1/users/me/focus-settings
GET /api/v1/users/me/analysis-excluded-domains
PUT /api/v1/users/me/analysis-excluded-domains
```

### 목표

```text
POST   /api/v1/goals
GET    /api/v1/goals/{goalId}
PATCH  /api/v1/goals/{goalId}
DELETE /api/v1/goals/{goalId}
POST   /api/v1/goals/clarify
```

### 세션

```text
POST /api/v1/sessions
GET  /api/v1/sessions/active
GET  /api/v1/sessions/{sessionId}
GET  /api/v1/sessions                         # P1
POST /api/v1/sessions/{sessionId}/pause
POST /api/v1/sessions/{sessionId}/resume
POST /api/v1/sessions/{sessionId}/extend
POST /api/v1/sessions/{sessionId}/end
POST /api/v1/sessions/{sessionId}/heartbeats
```

### 분석·개입

```text
GET  /api/v1/sessions/{sessionId}/analysis-runs
POST /api/v1/sessions/{sessionId}/analysis-runs
GET  /api/v1/sessions/{sessionId}/interventions
POST /api/v1/interventions/{interventionId}/acknowledgements
POST /api/v1/sessions/{sessionId}/local-events
```

### 리포트

```text
GET /api/v1/reports/daily
GET /api/v1/reports/calendar?month=YYYY-MM
GET /api/v1/reports/weekly                     # P1
GET /api/v1/sessions/{sessionId}/summary
GET /api/v1/sessions/{sessionId}/learning-summary
```

`GET /api/v1/sessions`와 `GET /api/v1/reports/weekly`만 P1이며 나머지는 MVP 범위다.

## 세션 규칙

- 상태: `DRAFT`, `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING`, `ENDED`, `ABANDONED`
- `ENDED`와 `ABANDONED`는 최종 상태다.
- 한 사용자에게 `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING` 중인 활성 세션은 하나만 허용한다.
- 시간은 `targetDurationMs`와 `accumulatedActiveMs`를 기준으로 계산한다.
- `targetDurationMs = null`은 시간 기준 자동 종료 없이 사용자가 직접 종료하는 무기한 세션을 뜻한다.
- Heartbeat는 10초 간격으로 보낸다. 과거 `clientObservedAt`은 무시하고, 감소한 `accumulatedActiveMs`는 거절한다.
- 현재 RUNNING 구간의 첫 유효 heartbeat를 받기 전에는 `lastResumedAt`을 무신호 시간 계산 기준으로 사용한다. 최초 시작에서는 `lastResumedAt = startedAt`이다.
- 마지막 heartbeat 뒤 60초 동안 신호가 없으면 `ABANDONED` 후보로 처리한다.
- 마지막 유효 heartbeat 뒤 3분까지 정상 신호가 복구되지 않으면 `ABANDONED`로 원자적 자동 종료한다. 3분 안에 복구되면 후보를 해제한다.
- 자동 종료는 마지막 서버 확정 `accumulatedActiveMs`만 사용하고 `endReason: HEARTBEAT_TIMEOUT`을 기록한다. 확정 뒤 도착한 heartbeat는 세션을 되살리지 않는다.
- Desktop 재실행으로 로컬 monotonic 기준점이 사라지면 서버의 마지막 확정 누적 시간만 사용하고 `AUTO_PAUSED`, `pauseReason: APP_RESTART`로 복구한다. 사용자가 명시적으로 재개하기 전에는 `RUNNING`으로 바꾸지 않는다.
- 세션 종료 후 학습 요약 상태는 `QUEUED`, `GENERATING`, `READY`, `INSUFFICIENT_DATA`, `FAILED`를 사용한다.

## 분석 결과 규칙

- 목표 관련성 값은 `RELATED`, `UNRELATED`, `UNCERTAIN`을 사용한다.
- `PRIVACY_BLOCKED`, `EXCLUDED`, `UNCERTAIN`은 정상적인 분석 상태이며 오류 코드가 아니다.
- 관련성, 신뢰도, 판단 근거는 서로 다른 필드로 유지한다.
- 원본 화면과 카메라 영상은 기본 저장하지 않는다.
- `confidence < 0.75`인 관련성 결과는 `UNCERTAIN`으로 정규화한다.
- `UNCERTAIN` 시간은 이탈 시간에 합산하지 않는다. 30초 이하는 기존 이탈 구간을 보류하고, 30초를 초과하면 기존 구간을 종료한다.
- `RELATED`가 확인되면 기존 이탈 구간과 카메라 알림 후보를 즉시 초기화한다. 15초 미만의 짧은 화면 전환은 이탈 구간으로 집계하지 않는다.
- 개입 임계값은 `GENTLE` 연속 120초, `NORMAL` 최근 10분 안의 30초 이상 이탈 구간 2회, `ACTIVE` 연속 30초다.
- 개입 cooldown은 `sessionId + interventionType`별 5분이다.
- `policyVersion`은 세션 시작 시 `interventionPolicyVersion`으로 고정하고 분석 기록과 개입 이벤트에 같은 값을 저장한다.

## 분석 제외 도메인

- 제외 목록에는 URL 전체가 아니라 정규화한 `host`만 저장한다. path, query, fragment, 페이지 내용은 저장하지 않는다.
- 온보딩에서 빈 배열로 건너뛸 수 있고 설정에서 전체 목록을 조회·교체할 수 있다.
- `PUT /api/v1/users/me/analysis-excluded-domains`는 전체 교체 방식이며 빈 배열은 목록 전체 삭제를 뜻한다.
- 제외 목록은 사용자별 최대 50개다.
- 제외 도메인에서는 화면 DOM·OCR·AI 콘텐츠 분석을 중단하지만 타이머는 유지한다. 카메라 동의와 권한이 유효하면 로컬 카메라 분석은 계속할 수 있다.

## 학습 정리와 리포트

- 학습 정리는 세션 종료와 분리된 비동기 작업이다. 생성 실패가 `ENDED` 확정이나 세션 리포트 조회를 막지 않는다.
- 학습 정리 응답과 저장 JSON은 `overview`, `keyConcepts`, `nextStartPrompt`만 사용한다. `reviewQuestions`와 자동 퀴즈는 MVP에서 사용하지 않는다.
- `learningSummaryStatus`는 HTTP 오류 코드가 아니라 정상 응답의 업무 상태다.
- 집중 달력은 세션 시작 시 확정한 `studyDate`를 기준으로 집계한다. 자정을 넘긴 세션도 시작일 하나에 귀속한다.
- 달력 집계에는 종료가 확정된 `ENDED`, `ABANDONED` 세션만 포함하고, `ABANDONED`는 마지막 서버 확정 누적 시간만 사용한다.
- `totalActiveMs >= 60000`인 날짜만 학습 활동일로 표시한다.

## 재시도와 속도 제한

- `401`: 토큰 갱신은 single-flight로 처리하고 원 요청은 최대 한 번 재시도한다.
- GET의 네트워크 오류·`503`·`504`: 1초, 3초 간격으로 최대 두 번 재시도한다.
- 상태 변경 요청은 같은 `Idempotency-Key`가 있을 때만 안전하게 재시도한다.
- 분석 요청 중복 방지는 `clientRunId`를 사용한다.
- `429 TOO_MANY_REQUESTS`에는 `Retry-After`를 반환한다.

| 대상 | 기준 | 제한 |
| --- | --- | --- |
| Google 로그인 교환 | IP + installationId | 10분당 5회 |
| 연결 코드 발급 | userId + Desktop deviceId | 10분당 3회 |
| 연결 코드 입력 | IP + Extension installationId | 10분당 10회 |
| 분석 요청 | sessionId + deviceId | 분당 8회 |
| 일반 인증 API | userId + deviceId | 분당 60회 |

## 공통 오류 코드

상세 조건과 HTTP 상태는 Notion `공통 Error Code`를 따른다. 현재 공통 코드 집합은 다음과 같다.

```text
AUTH_ACCESS_TOKEN_EXPIRED
AUTH_RELOGIN_REQUIRED
AUTH_TOKEN_REUSE_DETECTED
AUTH_OAUTH_REAUTH_REQUIRED
AUTH_DEVICE_LIMIT_EXCEEDED
DEVICE_NOT_FOUND
LINK_001_INVALID_FORMAT
LINK_002_EXPIRED
LINK_003_ALREADY_USED
LINK_004_ATTEMPTS_EXCEEDED
LINK_005_CANCELLED
LINK_TOKEN_REVOKED
PROFILE_NICKNAME_INVALID
PROFILE_NICKNAME_DUPLICATE
GOAL_TEXT_INVALID
GOAL_ANALYSIS_UNAVAILABLE
GOAL_ANALYSIS_TIMEOUT
GOAL_NOT_FOUND
GOAL_UPDATE_BLOCKED
SESSION_ACTIVE_EXISTS
SESSION_STATE_INVALID
SESSION_VERSION_CONFLICT
SESSION_NOT_FOUND
SESSION_START_REQUIREMENT_MISSING
SESSION_IDEMPOTENCY_REQUIRED
SESSION_HEARTBEAT_INVALID
SESSION_ABANDONED
ANALYSIS_SERVICE_UNAVAILABLE
VALIDATION_ERROR
IDEMPOTENCY_CONFLICT
TOO_MANY_REQUESTS
INTERNAL_ERROR
SERVICE_UNAVAILABLE
REQUEST_TIMEOUT
```

TypeScript 공유 타입은 `packages/shared-types`에서 관리한다. Spring DTO와 AI 입력 스키마는 각 서버에서 런타임 검증을 별도로 수행한다.

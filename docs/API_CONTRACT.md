# FocusOn API 계약

이 문서는 Desktop, Extension, Server, AI가 구현 중 빠르게 확인할 공통 계약 요약이다. 엔드포인트별 Request, Response, 권한, 예외의 최종 원본은 Notion **FocusOn API 명세서** 데이터베이스다.

## 빠르게 찾기

| 확인할 내용 | 위치 |
| --- | --- |
| 응답·ID·날짜·헤더 | 공통 형식 / 요청 헤더 |
| Google 로그인·Extension 연결 | 엔드포인트 목록 → 인증·기기 연결 |
| 온보딩·동의 | 엔드포인트 목록 → 사용자·설정 |
| Heartbeat·자동 종료 | 세션 규칙 |
| 개입 v1·cooldown | 분석 결과 규칙 |
| 일간 리포트·학습 정리 | 학습 정리와 리포트 |
| 정본 간 충돌·구현 전 확인 | [문서 대조 기록](NOTION_SYNC_AUDIT.md) |

이 문서의 계약 설명은 구현 완료를 뜻하지 않는다. 실제 구현 상태는 각 영역의 `CONTEXT.md`에서 확인한다.

## 문서 우선순위

1. Notion `FocusOn API 명세서`: 엔드포인트별 최종 계약
2. Notion `공통 정책 · 기술 계약`의 `공통 Error Code`, `세션 상태 정책`: 공통 오류와 상태 전이
3. 이 문서: 로컬 개발용 요약

서로 다르면 해당 항목의 최신 확정 결정을 함께 확인한다. 현재 화면 개입은 사용자 확정과 최신 화면 분석 5.2의 `v1`을 적용하며, 이전 값이 남은 AI 분석 계약 4.4·7장의 차이는 대조 기록에서 추적한다. 폐기된 예전 Notion `API 명세` 페이지는 기준으로 사용하지 않는다.

## 공통 형식

- Base path: `/api/v1`
- 필드 이름: lower camel case
- DB 리소스 ID: BIGINT를 10진 문자열로 직렬화한다. UUID인 `clientRunId`·멱등 키와 구분하며 모든 ID에 UUID 검증을 적용하지 않는다.
- 날짜와 시각: UTC ISO 8601 문자열
- `timeZone`은 세션 생성 때 전달해 날짜를 고정한다. 일간 조회는 `date=YYYY-MM-DD`, 달력 조회는 `month=YYYY-MM`을 사용하고 `timeZone` query로 과거 기록을 재배정하지 않는다.

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
| `Authorization: Bearer {accessToken}` | Notion 명세에서 인증이 필요한 API |
| `X-Device-Id: {deviceId}` | 해당 Notion endpoint의 필수 헤더에 기재된 요청 |
| `Idempotency-Key: {key}` | 세션 생성·일시정지·재개·연장·종료 |
| `If-Match-Version: {version}` | 기존 세션의 일시정지·재개·연장·종료 |

Heartbeat에는 `Authorization`과 `X-Device-Id`만 사용한다. `Idempotency-Key`와 `If-Match-Version`을 보내지 않는다.

`POST /api/v1/auth/refresh`는 `Authorization` 헤더 대신 JSON body의 `refreshToken`으로 인증한다. 각 endpoint의 실제 필수 헤더는 Notion API DB 항목을 따른다. 성공 응답의 `data`에는 새 `accessToken`, 새 `refreshToken`, UTC ISO 8601 `accessTokenExpiresAt`을 담는다. 클라이언트는 single-flight로 1회 호출한 뒤 원 요청을 1회만 재시도한다.

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

`POST /api/v1/auth/google/exchange` 요청에는 `authorizationCode`, `codeVerifier`, `state`, `redirectUri`, `device`를 포함한다. `redirectUri`는 Desktop Main이 Google 인증 요청에 사용한 `http://127.0.0.1:<임시 포트>` 주소 그대로다. Main이 callback의 `state` 일치를 먼저 확인한다. Backend는 `redirectUri`의 scheme·host·port·경로를 엄격히 검증한 뒤 서버 설정의 Desktop Google Client ID, code, verifier, 동일한 `redirectUri`로 Google token endpoint에 요청하고 Google ID token을 검증한다. Backend가 사전에 `state`를 저장하지 않았다면 요청에 포함된 값만으로 일치 여부를 검증했다고 간주하지 않는다. [Notion API 정본](https://app.notion.com/p/00f3391cae454614bf51bffbe995f0dc)

### FocusOn 로그인 유지·로그아웃 (2026-10-26 사용자 승인)

- `POST /auth/refresh`는 Bearer 없이 `{refreshToken}`으로 인증하고 `200 data.{accessToken,refreshToken,accessTokenExpiresAt}`을 반환한다.
- 갱신 성공마다 기존 token은 즉시 ROTATED이고 새 token·family의 만료는 현재부터 30일로 연장한다. 앱을 계속 사용해 유효 갱신이 이어지면 유지되며, 30일간 갱신하지 않으면 재로그인이 필요하다.
- 누락·빈 값·미확인·만료는 401 AUTH_RELOGIN_REQUIRED, ROTATED/REVOKED 재사용은 401 AUTH_TOKEN_REUSE_DETECTED와 해당 family 폐기다. 폐기는 오류 응답 전에 commit한다.
- 공개 refresh IP 보호는 300회/10분, 유효 token의 userId+deviceId는 일반 인증 60회/분 한도를 공유한다. 공개/인증 한도 공간은 분리한다.
- `POST /auth/logout`은 현재 활성 Desktop Bearer를 사용하고 204 빈 본문이다. 해당 Desktop과 연결 Extension을 폐기하며 다른 Desktop은 유지한다. Extension의 직접 logout은 허용하지 않고 unlink 계약을 사용한다.
- 사용자→family 순서의 잠금으로 rotation·logout·재로그인을 직렬화한다. Client single-flight와 원 요청 1회 재시도를 유지하며 token 재전송 grace는 없다.
- #59 구현·QA 기록은 `server/docs/features/auth-lifecycle-*`를 따른다. 30일 연장·한도 세부값은 사용자가 승인한 최신 결정이며 이번 작업에서 Notion 원문은 수정하지 않았다.

### Extension 연결 응답과 실패 횟수

`POST /api/v1/extension-pairings`는 성공 시 `201`과 다음 구조를 반환한다. Desktop과 독립된 token family를 발급하고, `device.deviceId`는 연결 해제 경로의 `extensionDeviceId`로 사용한다. `accessTokenExpiresAt`은 UTC ISO 8601 문자열이다.

```json
{
  "data": {
    "accessToken": "extension-access-token",
    "refreshToken": "extension-refresh-token",
    "accessTokenExpiresAt": "2026-10-24T12:00:00Z",
    "device": { "deviceId": "2", "deviceType": "EXTENSION" }
  }
}
```

이 연결 응답의 공개 `deviceType`은 `EXTENSION`이고, 기존 DB·Java 내부 기기 종류는 `CHROME_EXTENSION`이다. 응답 변환 계층에서 매핑하며 V1 migration이나 내부 enum을 바꾸지 않는다.

형식은 맞지만 코드가 틀린 1~2번째 실패는 `400 LINK_006_CODE_MISMATCH`와 숫자 `details.remainingAttempts`를 반환한다. 세 번째 실패는 `410 LINK_004_ATTEMPTS_EXCEEDED`이며 해당 시도에 연결된 코드를 폐기한다. 형식 오류는 `400 LINK_001_INVALID_FORMAT`이다.

```json
{
  "code": "LINK_006_CODE_MISMATCH",
  "message": "코드가 맞지 않아요. 다시 확인해 주세요.",
  "retryable": false,
  "retryAfterSeconds": null,
  "requestId": "req_example",
  "details": { "remainingAttempts": 2 }
}
```

공유된 초안에는 `retryable: true`도 있었지만, 마지막 입력 오류 예시와 공통 400 정책에 맞춰 자동 재전송은 하지 않는다(`false`). 사용자가 코드를 수정해 직접 다시 제출하는 것은 허용한다. 현재 `pairingCode`·`installationId` 요청만으로 틀린 코드가 어떤 Desktop 코드에 대한 시도인지 식별하는 계약은 아직 부족하며, 구현 전 [대조 기록](NOTION_SYNC_AUDIT.md)의 연결 시도 문맥 항목을 해결해야 한다.

### 사용자·설정

```text
GET /api/v1/users/me
GET /api/v1/users/me/onboarding-readiness
PUT /api/v1/users/me/profile
PUT /api/v1/users/me/onboarding-readiness
GET /api/v1/users/me/consents
PUT /api/v1/users/me/consents/{consentType}
GET /api/v1/users/me/focus-settings
PUT /api/v1/users/me/focus-settings
GET /api/v1/users/me/analysis-excluded-domains
PUT /api/v1/users/me/analysis-excluded-domains
```

`GET /api/v1/users/me`의 `onboardingStatus`는 닉네임, Extension 연결, 필수 권한·동의, 분석 제외 사이트 설정 단계를 기준으로 `INCOMPLETE` 또는 `COMPLETED`를 반환한다. 목표·시간·개입 설정은 온보딩 상태에 포함하지 않으며, 온보딩 완료 뒤 Desktop 대시보드의 `오늘 학습` 흐름에서 설정한다.

앱을 다시 열었을 때 이어갈 온보딩 단계는 `GET /api/v1/users/me/onboarding-readiness`로 조회한다. 응답의 `currentStep`은 `NICKNAME`, `EXTENSION`, `PERMISSIONS_AND_CONSENTS`, `EXCLUDED_SITES`, `COMPLETED` 중 첫 미완료 단계다. `steps`에는 프로필, Extension 연결, 필수 권한·동의, 제외 사이트 단계의 현재 상태를 반환한다. `COMPLETED`면 Desktop 대시보드로 이동한다.

```json
{
  "data": {
    "onboardingStatus": "INCOMPLETE",
    "currentStep": "PERMISSIONS_AND_CONSENTS",
    "steps": {
      "profileCompleted": true,
      "extensionLinked": true,
      "screenAnalysisReady": false,
      "requiredConsentsCompleted": false,
      "analysisExcludedDomainsStepCompleted": false
    }
  }
}
```

`PUT /api/v1/users/me/onboarding-readiness`는 현재 Desktop Main이 OS에서 확인한 `screenAnalysisReady`와 사용자가 제외 도메인을 저장하거나 빈 목록 건너뛰기를 선택한 뒤의 `analysisExcludedDomainsStepCompleted`만 받는다. 앱 시작·복귀 때 Desktop은 OS 권한을 다시 확인해 이 값을 갱신한다. Extension 연결은 서버 pairing 상태, 필수 동의는 동의 기록, 닉네임은 저장된 프로필에서 서버가 판정한다. 빈 도메인 목록만으로 사용자가 단계를 방문했는지 판별하지 않는다. 카메라 동의는 선택 사항이다. 알림은 필수이며 Desktop의 시험 알림과 사용자 확인으로 점검하지만 readiness API·DB에는 새 알림 필드를 추가하지 않는다. 2026-10-25 세션 생성 정본에 따라 알림 준비는 Desktop이 요청 전에 확인·차단한다. 서버는 알림 권한을 독립 검증했다고 처리하거나 `NOTIFICATION_PERMISSION`을 반환하지 않는다.

```json
{
  "screenAnalysisReady": true,
  "analysisExcludedDomainsStepCompleted": true
}
```

닉네임 확인 전용 API는 추가하지 않는다. `PUT /api/v1/users/me/profile` 저장 결과의 `409 PROFILE_NICKNAME_DUPLICATE`로 중복을 판단한다.

`GET /api/v1/users/me/consents`는 다음 필드명을 사용한다. `currentVersion`은 현재 표시하는 동의 문서 버전으로 응답에 반드시 포함한다. 아래 `2026-09-17`은 현재 합의된 버전이며 이후 실제 동의 문서가 바뀌면 서버의 버전 관리와 함께 변경한다.

```json
{
  "data": {
    "currentVersion": "2026-09-17",
    "consents": [
      {
        "consentType": "TERMS_OF_SERVICE",
        "isGranted": true,
        "consentVersion": "2026-09-17",
        "grantedAt": "2026-10-24T03:00:00Z",
        "withdrawnAt": null
      }
    ]
  }
}
```

Desktop은 읽은 `currentVersion`을 `PUT /api/v1/users/me/consents/{consentType}`의 `consentVersion`으로 그대로 보낸다. 이전 초안의 `type`, `agreed`, `version`, `agreedAt`은 사용하지 않는다. Frontend의 임시 fallback이 있어도 서버가 `currentVersion`을 생략해도 된다는 뜻은 아니다.

Extension 연결 화면의 readiness 조회는 3초 간격, 연결 코드의 5분 수명 동안 최대 약 100회다. 기본 약 20회/분이므로 일반 인증 한도 60회/분 안에 들어간다. 다른 API와 재시도까지 같은 사용자·기기 한도를 공유하므로 중복 polling을 막고, 완료·만료·화면 이탈 시 중단하며 429에서는 `Retry-After`를 따른다.

### 목표

```text
POST   /api/v1/goals
GET    /api/v1/goals/{goalId}
PATCH  /api/v1/goals/{goalId}
DELETE /api/v1/goals/{goalId}
POST   /api/v1/goals/clarify
```

`POST /api/v1/goals/clarify`는 다음 요청으로 대화를 이어 간다. 첫 요청은 `conversationTurn: 0`, `previousAnswers: []`이며 `clarificationSessionId`를 생략한다.

```json
{
  "goalText": "Spring Security JWT 인증 구현",
  "conversationTurn": 0,
  "previousAnswers": []
}
```

응답의 `data`에는 기존 `decision`, `normalizedGoalText`, `question`, `suggestions`, `goalCandidates`, `conversationTurn`과 함께 서버가 발급한 문자열 `clarificationSessionId`가 있다. 이후 요청은 같은 ID와 `previousAnswers: [{question, answer}]`를 보낸다. `POST /api/v1/goals`에서 최종 목표를 저장할 때도 받은 ID를 전달한다. AI를 거치지 않은 수동 확정은 이 ID를 필수로 요구하지 않는다.

서버는 대화 ID의 사용자 소유·유효성을 검증하고, 클라이언트가 보낸 질문·턴을 권위 있는 이력으로 무조건 신뢰하지 않는다. Desktop은 이 POST를 자동 재전송하지 않는다. AI 일시 장애·timeout 재시도는 Backend에서 1초 → 3초 간격으로 최대 2회만 수행하며, 내부 재시도로 대화 턴이나 질문 수를 중복 증가시키지 않는다. 최종 실패는 기존 목표 분석 오류로 반환하고 Desktop은 수동 확정을 제공한다.

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

세션 생성의 필수 헤더는 `Authorization`, `X-Device-Id`, `Idempotency-Key`다. 기존 세션이 아직 없으므로 생성에 `If-Match-Version`을 요구하지 않는다.

```json
{
  "goalId": "12",
  "targetDurationMs": 1800000,
  "timeZone": "Asia/Seoul",
  "sessionOptions": {
    "interventionLevel": "NORMAL",
    "notificationChannels": ["MOLE_UI", "MACOS_NOTIFICATION"],
    "cameraAnalysisEnabled": false
  },
  "startReadiness": { "screenAnalysisReady": true }
}
```

`sessionOptions`는 focus-settings와 같은 필드명을 사용하되 `breakSuggestionEnabled`를 받지 않는다. 강도는 `GENTLE | NORMAL | ACTIVE`, 채널은 `MOLE_UI | MACOS_NOTIFICATION | SOUND` 중 1개 이상·중복 없는 배열이다. 카메라 사용은 별도 동의·권한 조건을 검증한다.

`startReadiness`에는 Desktop의 화면 기록 확인값만 담는다. 서버는 화면 분석 준비 상태와 Extension 연결·소유권 등 서버가 확인할 수 있는 조건을 검증한다. 알림은 Desktop이 요청 전에 시험 알림·사용자 확인으로 차단하며, 서버는 `NOTIFICATION_PERMISSION`을 반환하지 않는다. Desktop은 생성 POST를 자동 재전송하지 않고 같은 생성 의도의 수동 재시도에는 같은 멱등 키를 사용한다. payload가 바뀐 새 생성 의도에는 새 키를 사용한다.

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

`GET /api/v1/sessions`와 `GET /api/v1/reports/weekly`만 P1이며 나머지는 MVP 범위다. 제외 도메인 API는 현재 사용자 목록을 조회하고 전체 교체한다. 빈 배열은 전체 삭제이며, host는 소문자·IDNA ASCII·후행 점 제거 기준으로 정규화한다. 사용자당 최대 50개를 저장하고 scheme·port·path·query·fragment·페이지 내용은 저장하지 않는다.

## 세션 규칙

- 서버 저장 상태: `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING`, `ENDED`, `ABANDONED`.
- `DRAFT`는 서버 세션 생성 전 클라이언트 상태다. DB의 세션 상태 enum에 넣지 않는다. `ABANDONED` 후보도 별도 상태로 저장하지 않고 `RUNNING`의 생존 기준 시각으로 계산한다.
- `POST /api/v1/sessions`의 `timeZone`은 Desktop 기기의 IANA 이름을 받는다. 없거나 유효하지 않으면 `Asia/Seoul`을 사용하며, IP·VPN·WARP 주소로 추정하지 않는다. 서버 UTC `startedAt`과 확정한 `userTimeZoneSnapshot`으로 `studyDate`를 한 번 정한다.
- 시작·종료·일시정지 시각은 서버 UTC 시계로 확정한다. 활성 누적 시간은 Desktop monotonic clock의 값을 서버가 자신의 시계와 상태로 검증·확정한다. 사용자의 PC 벽시계 차이를 그대로 누적 시간으로 사용하지 않는다.
- `ENDED`와 `ABANDONED`는 최종 상태다.
- 한 사용자에게 `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING` 중인 활성 세션은 하나만 허용한다.
- 시간은 `targetDurationMs`와 `accumulatedActiveMs`를 기준으로 계산한다.
- MVP 세션 생성의 `targetDurationMs`는 필수 정수이며 `300000~18000000ms`(5~300분), `60000ms`(1분) 단위다. null·누락·범위 밖·분 단위가 아닌 값은 거절한다. 이전 무기한 세션 계약은 현재 MVP에 적용하지 않는다.
- MVP 대시보드의 첫 세션 설정은 `30분`, `1시간`, `2시간`, `직접 입력(5~300분)`이다.
- Heartbeat는 10초 간격으로 보낸다. 과거 `clientObservedAt`은 무시하고, 감소한 `accumulatedActiveMs`는 거절한다.
- 현재 RUNNING 구간의 첫 유효 heartbeat를 받기 전에는 `lastResumedAt`을 무신호 시간 계산 기준으로 사용한다. 최초 시작에서는 `lastResumedAt = startedAt`이다.
- 생존 기준은 현재 `RUNNING` 구간의 `lastResumedAt`과 마지막 유효 `lastHeartbeatAt` 중 최신 시각이다. 이전 실행 구간의 heartbeat 때문에 재개 직후 시간 초과로 판정하지 않는다.
- 기준 시각 뒤 60초 무신호는 후보, 총 180초 무신호는 `ABANDONED` 확정이다. 180초 전에 복구되면 후보를 해제한다. 확정 시 마지막 서버 검증 `accumulatedActiveMs`, `endReason: HEARTBEAT_TIMEOUT`, 서버 확정 시각 `endedAt`을 사용하고 미확인 시간을 더하지 않는다.
- 자동 종료 worker와 heartbeat는 같은 세션의 조건부 갱신·트랜잭션으로 경쟁을 처리한다. 확정 후 heartbeat는 `409 SESSION_ABANDONED`와 `details.currentStatus`, `details.endReason`을 반환하며, 클라이언트는 최신 상태를 조회한다.
- 클라이언트 관측 시각의 순서 비교와 서버 수신 시각의 생존 계산은 구분한다. `clientObservedAt` 비교용 저장값·같은 시각의 재전송 처리 등 아직 부족한 정의는 대조 기록의 구현 전 확인 항목을 따른다.
- Desktop 재실행으로 로컬 monotonic 기준점이 사라지면 서버의 마지막 확정 누적 시간만 사용하고 `AUTO_PAUSED`, `pauseReason: APP_RESTART`로 복구한다. 사용자가 명시적으로 재개하기 전에는 `RUNNING`으로 바꾸지 않는다.
- `ENDED` 이후 학습 요약 상태는 `QUEUED`, `GENERATING`, `READY`, `INSUFFICIENT_DATA`, `FAILED`를 사용한다. `ABANDONED`는 시간·리포트 집계에 포함하지만 자동 학습 정리 작업을 만들지 않는다.

일간 리포트와 홈 집중 달력은 세션 생성 시 고정한 `studyDate`에 `ENDED` 및 서버 확정 `ABANDONED`의 마지막 검증된 `accumulatedActiveMs`를 귀속한다. 자정을 넘어도 분할하지 않으며, 같은 날짜 합계가 60,000ms 이상일 때 달력에 포키를 표시한다. 진행 중 세션은 집계하지 않는다.

## 분석 결과 규칙

- 공개 `relation`은 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 사용한다. AI 관련성 판정값은 `RELATED`, `UNRELATED`, `UNCERTAIN`이며 `EXCLUDED`와 `PRIVACY_BLOCKED`는 AI 호출 전에 적용되는 정책 차단 상태다.
- AI 내부 `SUPPORTING`은 목표에 필요한 보조 학습 활동을 뜻하며 공개 `relation`에서는 `RELATED`로 매핑한다. `SUPPORTING`은 공개 상태 enum을 확장하지 않는다.
- `EXCLUDED`, `PRIVACY_BLOCKED`, `UNCERTAIN`은 HTTP 오류가 아닌 정상 분석 상태다.
- 관련성, 신뢰도, 판단 근거는 서로 다른 필드로 유지한다.
- 원본 화면과 카메라 영상은 기본 저장하지 않는다.
- `confidence < 0.75`인 관련성 결과는 `UNCERTAIN`으로 정규화한다.
- 정책 `v1`에서 `UNCERTAIN` 시간은 이탈 시간에 합산하지 않고 현재 연속 이탈 구간을 끊는다. 이전의 30초 보류·이어붙이기 규칙은 새 세션에 사용하지 않는다. `EXCLUDED`·`PRIVACY_BLOCKED`·미관찰 시간도 연속 이탈을 이어붙일 근거가 아니다.
- `RELATED`가 확인되면 기존 이탈 구간과 카메라 알림 후보를 즉시 초기화한다. 15초 미만의 짧은 화면 전환은 이탈 구간으로 집계하지 않는다.
- 개입 조건은 아래 `v1` 표를 사용한다. 각 강도에서 연속 조건과 반복 조건 중 하나만 충족해도 후보가 된다. `초과`는 엄격한 `>`이며 경계값과 같은 시간에는 충족하지 않는다. 15초 미만 전환 제외와 신뢰도·세션·권한 검사를 먼저 적용한다.

| 강도 | 끊김 없는 UNRELATED 구간 | rolling 10분 안의 반복 조건 |
| --- | --- | --- |
| `GENTLE` | 180초 초과 | 60초 초과인 서로 다른 구간 3회 |
| `NORMAL` | 60초 초과 | 30초 초과인 서로 다른 구간 2회 |
| `ACTIVE` | 30초 초과 | 20초 초과인 서로 다른 구간 2회 |

- 같은 구간의 여러 heartbeat·분석 결과를 서로 다른 이탈 횟수로 중복 집계하지 않는다. rolling window 경계에서 구간을 포함하는 정확한 시각 기준은 Policy Engine 구현 전에 공통 계약에 명시한다.
- 서버 개입 cooldown은 `sessionId + interventionType`별 5분이며 알림이 실제 전송된 시점부터 계산한다. cooldown 동안 쌓인 후보를 만료 직후 그대로 전송하지 않고 새 관측에서 조건을 다시 평가한다.
- `확인했어` 뒤의 5분 재집중 구간은 Desktop UI 상태다. 타이머·세션·AI 판정을 바꾸거나 별도 서버 상태로 저장하지 않는다. 서버 cooldown의 시작 시각과 혼합하지 않는다.
- 서버가 세션 생성 시 현재 버전 `v1`을 `interventionPolicyVersion`에 고정하고 분석 기록·개입 이벤트에는 같은 값을 `policyVersion`으로 저장한다. 클라이언트가 임계값이나 버전을 임의 선택하지 않는다. 일시정지·재개·배포 뒤에도 진행 중 세션의 버전을 바꾸지 않는다.
- 기준은 2026-10-24 사용자 확정과 Notion `화면 분석` 5.2·5.3이다. 아직 이전 임계값·`focus-intervention-v1` 예시가 남은 Notion `AI 분석 계약` 7장과의 차이는 [대조 기록](NOTION_SYNC_AUDIT.md)에 남긴다.

## 분석 제외 도메인

- 제외 목록에는 URL 전체가 아니라 정규화한 `host`만 저장한다. path, query, fragment, 페이지 내용은 저장하지 않는다.
- 온보딩에서 분석 제외 사이트 설정 단계를 반드시 거친다. 사이트를 등록하거나 빈 배열로 건너뛰기를 선택하면 단계가 완료되고, 온보딩 뒤에도 설정에서 전체 목록을 조회·교체할 수 있다.
- `PUT /api/v1/users/me/analysis-excluded-domains`는 전체 교체 방식이며 빈 배열은 목록 전체 삭제를 뜻한다.
- 제외 목록은 사용자별 최대 50개다.
- 제외 도메인에서는 화면 DOM·OCR·AI 콘텐츠 분석을 중단하지만 타이머는 유지한다. 카메라 동의와 권한이 유효하면 로컬 카메라 분석은 계속할 수 있다.

## 학습 정리와 리포트

- 학습 정리는 세션 종료와 분리된 비동기 작업이다. 생성 실패가 `ENDED` 확정이나 세션 리포트 조회를 막지 않는다.
- 학습 정리 응답과 저장 JSON은 `overview`, `keyConcepts`, `nextStartPrompt`만 사용한다. `reviewQuestions`와 자동 퀴즈는 MVP에서 사용하지 않는다.
- `keyConcepts`는 `{ name, briefExplanation, sessionEvidence }` 객체 배열로 최대 5개다. 설명과 근거는 확정된 세션 근거 범위만 사용하고 원문·URL·코드·개인정보를 포함하지 않는다. 전체 정리 결과는 1,200자 이하다. 서로 다른 근거가 2개 미만이면 추측으로 채우지 않고 `INSUFFICIENT_DATA`를 반환한다.
- `learningSummaryStatus`는 HTTP 오류 코드가 아니라 정상 응답의 업무 상태다.
- 집중 달력은 세션 시작 시 확정한 `studyDate`를 기준으로 집계한다. 자정을 넘긴 세션도 시작일 하나에 귀속한다.
- 세션용 migration에서 `learning_sessions.study_date`는 처음부터 `DATE NOT NULL`로 만든다. 출시 전 신규 세션 모델의 기존 기록 백필은 하지 않는다. 이 결정 자체로 기존 DB나 테스트 데이터를 삭제하지 않는다.
- 달력 집계에는 종료가 확정된 `ENDED`, `ABANDONED` 세션만 포함하고, `ABANDONED`는 마지막 서버 확정 누적 시간만 사용한다.
- `totalActiveMs >= 60000`인 날짜만 학습 활동일로 표시한다.

`GET /api/v1/reports/daily?date=YYYY-MM-DD`의 `sessions[]` 항목은 다음 형태로 맞춘다. 날짜는 고정한 `studyDate`이며 조회 시점의 시간대로 재배정하지 않는다. API의 기존 `REVIEW` 상태를 구현 완료로 바꾸지는 않는다.

```json
{
  "sessionId": "1",
  "goalTextSnapshot": "Spring Security 공식 문서 학습",
  "startedAt": "2026-10-24T03:00:00Z",
  "endedAt": "2026-10-24T03:30:00Z",
  "accumulatedActiveMs": 1800000
}
```

`endedAt`의 공개 타입은 `string | null`로 두되, 일간 집계 대상은 `ENDED`와 확정된 `ABANDONED`만 유지한다. null 허용이 진행 중 세션을 포함한다는 뜻은 아니다. 2026-10-25 재확인한 Notion 일간 API에도 위 5개 항목 필드가 반영돼 있다. `meta.requestId` 표기와 공통 성공 래퍼의 차이는 대조 기록에서 별도로 추적한다.

## 재시도와 속도 제한

- `401`: 토큰 갱신은 single-flight로 처리하고 원 요청은 최대 한 번 재시도한다.
- GET의 네트워크 오류·`503`·`504`: 1초, 3초 간격으로 최대 두 번 재시도한다.
- 상태 변경 요청은 같은 `Idempotency-Key`가 있을 때만 안전하게 재시도한다.
- 분석 요청 중복 방지는 `clientRunId`를 사용한다.
- `429 TOO_MANY_REQUESTS`에는 `Retry-After`를 반환한다.

| 대상 | 기준 | 제한 |
| --- | --- | --- |
| Google 로그인 교환 | IP + installationId | 10분당 5회 |
| Refresh Token 갱신 | IP 보호 + 유효 token의 userId/deviceId | IP 10분당 300회 + 일반 인증 분당 60회 공유 |
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
LINK_006_CODE_MISMATCH
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

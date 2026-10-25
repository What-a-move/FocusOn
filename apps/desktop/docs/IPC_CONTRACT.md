# Desktop IPC 계약

> 결정 상태: 인증 IPC 경계와 Google callback, Backend code 교환·Token·Keychain 동작을 Main에 연결했다. Renderer 로그인 화면과 상태별 화면 전환도 연결했다. 실제 서버 왕복은 Backend API 병합 뒤 확인한다.

## 목적

IPC는 Electron의 Renderer와 Main이 대화하는 통로다. Renderer가 임의의 Electron 기능을 실행하지 못하도록 Channel, 입력, 출력, 오류를 계약으로 제한한다.

## 호출 방향

| 형태 | 용도 | 기본 API |
| --- | --- | --- |
| Renderer → Main → Renderer | 요청 후 결과가 필요한 작업 | `invoke` / `handle` |
| Main → Renderer | 권한·세션·Window 상태 변경 알림 | 제한된 Event 구독 |

응답이 필요한 작업에 `send`와 임시 Reply Channel을 만들지 않는다.

## Channel 이름

```text
영역:대상:동작
```

예시:

```text
permission:screen:get-status
permission:camera:request
active-app:current:get
capture:screen:start
capture:screen:stop
window:floating:set-visible
app:settings:open
```

## 공통 결과

```ts
type IpcResult<T> =
  | { success: true; data: T }
  | {
      success: false
      error: {
        code: string
        retryable: boolean
      }
    }
```

- Renderer는 오류 응답 최상위 `code`를 사용자에게 보여줄 안전한 문구로 변환한다.
- Main Process의 내부 Error Message는 Renderer로 보내지 않고 민감 정보를 제거한 개발 Log에만 남긴다.
- Stack Trace, File Path, Token, 원본 화면을 응답에 포함하지 않는다.

## 공통 Payload

```ts
type PermissionStatus =
  | 'not-determined'
  | 'granted'
  | 'denied'
  | 'restricted'
  | 'unknown'

type SessionPayload = {
  sessionId: string
}

type FloatingPosition = {
  displayId: string
  xRatio: number
  yRatio: number
}

type AuthUiStatus =
  | 'SIGNED_OUT'
  | 'AUTHENTICATING'
  | 'SIGNED_IN'
  | 'LINK_REQUIRED'
  | 'EXPIRED'
  | 'OFFLINE'

type AuthSessionSnapshot = {
  /** Main이 상태를 갱신할 때마다 증가한다. 오래된 Event가 뒤늦게 와도 무시할 수 있다. */
  revision: number
  status: AuthUiStatus
  user: { userId: string; nickname: string | null } | null
  onboardingStatus: 'INCOMPLETE' | 'COMPLETED' | null
  error: { code: string; retryable: boolean } | null
}
```

모든 입력은 위치 인자 대신 이름이 있는 Object를 사용한다. `xRatio`와 `yRatio`는 `0`부터 `1` 사이인지 Main에서 다시 검증한다.

## 확정 Channel 목록

| Channel | 방향 | 입력 | 성공 Data |
| --- | --- | --- | --- |
| `permission:screen:get-status` | Renderer → Main | `void` | `{ status: PermissionStatus }` |
| `permission:screen:open-settings` | Renderer → Main | `void` | `{ opened: boolean }` |
| `permission:camera:get-status` | Renderer → Main | `void` | `{ status: PermissionStatus }` |
| `permission:camera:request` | Renderer → Main | `void` | `{ status: PermissionStatus }` |
| `active-app:current:get` | Renderer → Main | `void` | `{ name: string; bundleId: string | null }` |
| `capture:screen:start` | Renderer → Main | `SessionPayload` | `{ startedAt: string }` |
| `capture:screen:stop` | Renderer → Main | `SessionPayload` | `{ stoppedAt: string }` |
| `window:floating:set-visible` | Renderer → Main | `{ visible: boolean }` | `{ visible: boolean }` |
| `window:floating:set-position` | Renderer → Main | `FloatingPosition` | `FloatingPosition` |
| `app:settings:open` | Renderer → Main | `void` | `{ route: '/settings' }` |
| `notification:show` | Renderer → Main | `{ titleCode: string; bodyCode: string }` | `{ shown: boolean }` |
| `auth:session:get` | Renderer → Main | `void` | `AuthSessionSnapshot` |
| `auth:session:retry` | Renderer → Main | `void` | `AuthSessionSnapshot` |
| `auth:google-login:start` | Renderer → Main | `void` | `{ started: boolean }` |
| `auth:logout` | Renderer → Main | `void` | `{ loggedOut: true }` |

실제 Channel을 추가하면 입력·출력 Type을 `electron/shared/`에 정의하고 이 표를 갱신한다.

현재 인증 Channel의 Type은 `electron/shared/auth-contract.cts`, 권한·알림 Channel(`permission:screen:get-status`, `permission:screen:open-settings`, `permission:camera:get-status`, `permission:camera:request`, `notification:show`)의 Type은 `electron/shared/permission-contract.cts`에 있다(2026-10-22 구현). `notification:show`는 Main의 `NOTIFICATION_TEXT`에 있는 code만 받는다. Preload는 `window.focusOn.permissions`로 노출한다. `permission:notification:get-status`(입력 `void`, 성공 `{ confirmed }`)와 `permission:notification:confirm`(입력 `{ seen: boolean }`, 성공 `{ confirmed }`)은 시험 알림을 본 사용자 확인을 이 기기에 기록·조회한다(2026-10-25, 결정 022). macOS 허용 상태가 아니라 사용자 확인 결과다. `permission:notification:open-settings`(입력 `void`, 성공 `{ opened: boolean }`)는 시스템 설정 › 알림을 연다(2026-10-23 추가).

온보딩 Channel(2026-10-22 구현, Type은 `electron/shared/onboarding-contract.cts`, Preload는 `window.focusOn.onboarding`):

| Channel | 입력 | 성공 Data | Backend 호출 |
| --- | --- | --- | --- |
| `onboarding:readiness:get` | `void` | `OnboardingReadiness` | `PUT`(Main이 확인한 `screenAnalysisReady`) → `GET /users/me/onboarding-readiness` |
| `onboarding:profile:save` | `{ nickname }` | `{ nickname }` | `PUT /users/me/profile` (409 = 중복) |
| `onboarding:pairing-code:issue` | `void` | `{ codeDisplay, expiresAt }` | `POST /pairing-codes` |
| `onboarding:permissions-step:save` | `{ consents: 필수 3개, cameraConsent }` | `{ saved: true }` | `PUT /users/me/consents/{type}` ×4 → `PUT onboarding-readiness` |
| `onboarding:excluded-domains:save` | `{ domains }` | `{ domains }` | `PUT /users/me/analysis-excluded-domains`(X-Device-Id) → `PUT onboarding-readiness { analysisExcludedDomainsStepCompleted: true }` |
| `onboarding:summary:get` | `void` | `OnboardingSummary` | readiness·consents·excluded-domains `GET` |

- 모든 온보딩 요청은 `AuthService.authorizedRequest`를 거친다. 만료가 가까우면 먼저 refresh하고, 서버가 `AUTH_ACCESS_TOKEN_EXPIRED`를 주면 single-flight refresh 1회 뒤 원 요청을 1회만 다시 보낸다. `GET`은 `LOCAL_NETWORK_UNAVAILABLE`·`SERVICE_UNAVAILABLE`(503)·`REQUEST_TIMEOUT`(504)에서 1초 → 3초 간격으로 최대 2회 재시도하고, `PUT`·`POST`는 자동 재전송하지 않는다(공통 Error Code 정책 4.3, 2026-10-23). 성공 상태인데 본문이 계약과 다르면 `INVALID_RESPONSE`로 구분해 재시도하지 않는다. refresh가 재로그인 오류면 Keychain을 비우고 `SIGNED_OUT` Event를 보낸다.
- `screenAnalysisReady`는 Renderer 입력을 받지 않고 Main이 `systemPreferences`로 직접 확인한 값만 보낸다.
- 서버가 `COMPLETED`를 반환하면 Main은 `AuthSessionSnapshot.onboardingStatus`도 갱신해 홈·온보딩 사이를 오가지 않게 한다. 닉네임 저장 성공 시 `user.nickname`도 갱신한다.
- `FOCUSON_API_BASE_URL`이 없으면 모든 온보딩 Channel은 `SERVICE_UNAVAILABLE`이다. Main은 호출 창·Main Frame·허용 URL과 `void` 입력을 검사한다. Renderer에 내부 예외 메시지나 Token을 전달하지 않는다.

Google 데스크톱 Client ID 또는 Backend origin이 없거나 브라우저·listener를 시작할 수 없으면 `auth:google-login:start`는 `SERVICE_UNAVAILABLE`, `retryable: false`를 반환한다. 둘 다 설정되면 기본 브라우저·임시 loopback listener 시작 뒤 `{ started: true }`를 반환한다. 이는 **로그인 성공**이 아니라 **인증 시도 시작**만 뜻한다. code callback 뒤 Main이 Backend에 교환을 요청하고 성공 응답의 Refresh Token을 Keychain에 저장한 경우에만 `SIGNED_IN` Event를 보낸다. 서버 API가 아직 없으면 성공 상태를 만들지 않는다. `auth:session:get`은 앱 시작 시 Keychain 복구가 끝난 Snapshot을 반환한다.

Google 로그인은 브라우저 왕복이 필요하므로 `auth:google-login:start`는 흐름을 시작한 직후 `{ started: true }`를 반환한다. 이미 `AUTHENTICATING`이면(브라우저 탭을 닫아 callback이 오지 않은 경우 포함) 이전 시도의 listener를 정리하고 새 `state`·verifier로 다시 시작해 `{ started: true }`를 반환한다. 진행 중이던 code 교환 결과는 버리고 서버 토큰을 폐기한다(2026-10-22). 완료·취소·실패 뒤 상태는 `auth:session:changed` Event로 알린다.

```ts
type AuthSessionChangedPayload = AuthSessionSnapshot
```

- `auth:session:get`은 현재 인증 상태의 화면용 Snapshot만 반환한다. Access Token, Refresh Token, authorization code, PKCE verifier, `state`는 포함하지 않는다.
- `auth:session:retry`는 `OFFLINE` 화면의 재시도 버튼에서 Main의 Keychain 복구·refresh·auth/me를 다시 실행하고 최신 Snapshot을 반환한다. 동시에 요청되면 Main의 같은 복구 작업을 공유한다.
- `auth:google-login:start`는 입력값을 받지 않는다. Main이 브라우저 URL, loopback listener, `state`, PKCE verifier를 직접 만든다.
- Main은 `127.0.0.1` 임시 포트의 기본 주소에서 code와 `state`를 받고 Host·경로·state를 확인한다. Google 취소 또는 5분 timeout이면 `SIGNED_OUT`으로 돌아간다. callback은 code를 화면에 표시하지 않고 listener를 닫는다.
- `auth:logout`은 Main이 `POST /api/v1/auth/logout`을 호출한다. 성공 `204` 또는 계약상 `401`이면 Access Token과 Keychain Refresh Token을 지우고 `{ loggedOut: true }`와 `SIGNED_OUT` Event를 반환한다. 네트워크·서버 오류면 세션은 유지하고 재시도 가능한 오류를 반환한다.
- OAuth 사용자가 취소하면 `SIGNED_OUT` Snapshot을 보내고 오류로 취급하지 않는다. 네트워크 오류나 서버 오류는 기존 오류 `code`와 `retryable`만 전달한다.
- Event를 구독한 뒤 Snapshot을 조회한다. Renderer는 `revision`이 현재 값보다 큰 Snapshot만 반영해 초기 조회와 Event 도착 순서가 바뀌어도 오래된 상태로 되돌아가지 않게 한다.
- Refresh Token은 Main이 macOS Keychain에서 관리하고 Access Token은 Main 메모리에만 둔다. 현재 인증 API 요청은 Main이 처리하며, 이후 인증이 필요한 API도 같은 경계를 사용한다. 토큰 원문은 Renderer로 넘기지 않는다.
- Preload는 `window.focusOn.auth.getSession()`, `retrySession()`, `startGoogleLogin()`, `logout()`, `onSessionChanged(callback)`처럼 기능별 API만 노출한다. Event 구독 함수는 해제 함수를 반환한다.
- 공통 Type은 `electron/shared/`에 정의했다. Main은 `sender`와 입력값을 검증한다. OAuth·IPC 전용 오류 코드를 새로 만들 필요가 생기면 공통 Error Code 계약과 함께 먼저 확정한다.

### 리포트 Channel (2026-10-24, Issue #41)

| Channel | 입력 | 성공 data | Backend |
| --- | --- | --- | --- |
| `report:calendar:get` | `{ month: 'YYYY-MM' }` | `{ month, studyDayCount, days[{ date, totalActiveMs, sessionCount, hasStudyActivity }] }` | `GET /api/v1/reports/calendar` |
| `report:daily:get` | `{ date: 'YYYY-MM-DD' }` | `{ date, totalActiveMs, sessions[{ sessionId, goal, startedAt, endedAt, activeMs }] }` | `GET /api/v1/reports/daily` |

- Preload는 `window.focusOn.reports.getCalendar()`, `getDaily()`로 노출한다. Main은 실제로 있는 월·날짜만 받고, `AuthService.authorizedRequest`(X-Device-Id, GET 재시도)로 조회한다.
- 일간 리포트 `sessions[]` 항목 필드는 Notion에 없어 `{ sessionId, goalTextSnapshot, startedAt, endedAt, accumulatedActiveMs }`로 읽는다(2026-10-24 확정, 결정 020). 형식이 다르면 `INVALID_RESPONSE`로 실패를 보여 준다.

### 목표 Channel (2026-10-24, Issue #44)

| Channel | 입력 | 성공 data | Backend |
| --- | --- | --- | --- |
| `goal:clarify` | `{ goalText, conversationTurn(0~2), clarificationSessionId?, previousAnswers[{ question, answer }] }` | `{ decision, normalizedGoalText, question, suggestions, goalCandidates, conversationTurn, clarificationSessionId }` | `POST /api/v1/goals/clarify` |
| `goal:create` | `{ goalText, confirmationSource: AI_CONFIRMED \| MANUAL_CONFIRMED, clarificationSessionId? }` | `{ goalId, goalText, confirmationSource }` | `POST /api/v1/goals` |

- Preload는 `window.focusOn.goals.clarify()`, `create()`로 노출한다. Main은 목표 2~40자·질문 2회·허용 필드만 받는다. POST라 자동 재전송하지 않는다(AI 재시도는 Backend).
- `clarificationSessionId`·`previousAnswers`는 AI 분석 계약 3.2와 같은 필드다(2026-10-24 확정, 결정 020). 응답에 `clarificationSessionId`가 없으면 null로 읽는다.
- 결정에 필요한 값이 없는 응답(예: `UNKNOWN_TERM`인데 `question` 없음, `MULTI_GOAL` 후보 1개 이하)은 `INVALID_RESPONSE`로 처리한다.
- Renderer는 `electron/shared`에서 타입만 가져온다(`goal-setting-shared-runtime-import-ERROR.md`).

### 세션 Channel (2026-10-24, Issue #46)

| Channel | 입력 | 성공 data | Backend |
| --- | --- | --- | --- |
| `session:focus-settings:get` | `void` | `{ interventionLevel, notificationChannels, cameraAnalysisEnabled }` | `GET /api/v1/users/me/focus-settings` |
| `session:start` | `{ goalId, goalText(화면 표시용, 서버로 안 보냄), targetDurationMs(5~300분, 분 단위), timeZone(IANA), sessionOptions: { interventionLevel, notificationChannels(1개 이상·중복 없음), cameraAnalysisEnabled }, idempotencyKey(UUID v4) }` | `{ sessionId, status, version, targetDurationMs, accumulatedActiveMs, startedAt, interventionPolicyVersion }` | `POST /api/v1/sessions` |

- Main은 `Authorization`(Bearer Access Token, 모든 인증 요청 공통)·`Idempotency-Key`·`X-Device-Id` 헤더를 붙이고, `startReadiness: { screenAnalysisReady }`는 Main이 macOS 권한을 직접 확인해 넣는다(Renderer 입력 아님). POST라 자동 재전송하지 않는다. Renderer는 같은 선택으로 다시 누르면 같은 키를 보낸다.
- `session:start`는 알림 확인 기록이 없으면 서버에 요청하지 않고 `LOCAL_NOTIFICATION_NOT_CONFIRMED`를 돌려준다(결정 022).
- `sessionOptions`는 focus-settings와 같은 필드 이름(`breakSuggestionEnabled` 제외), `startReadiness`는 `{ screenAnalysisReady }`다(2026-10-24 확정, 결정 021).
- `AuthorizedRequest.headers`로 요청별 헤더를 붙인다. 토큰 만료로 한 번 다시 보낼 때도 같은 값이다.

### 집중 세션 Channel (2026-10-25, Issue #48)

| Channel | 입력 | 성공 data | Backend |
| --- | --- | --- | --- |
| `session:current:get` | `void` | `SessionSnapshot \| null` (Main에 없으면 활성 세션 복구) | `GET /api/v1/sessions/active` (204 = 없음) |
| `session:pause` | `void` | `SessionSnapshot` | `POST /sessions/{id}/pause` `{ pauseReason: USER, accumulatedActiveMs }` |
| `session:resume` | `void` | `SessionSnapshot` | `POST /sessions/{id}/resume` `{ readiness: { screenAnalysisReady } }` |
| `session:extend` | `{ minutes: 5~300 }` | `SessionSnapshot` | `POST /sessions/{id}/extend` `{ additionalDurationMs }` |
| `session:end` | `void` | `SessionSnapshot` | `POST /sessions/{id}/end` |
| `session:changed` (Event) | — | `SessionSnapshot \| null` | — |

- `SessionSnapshot = { sessionId, goalText, status, targetDurationMs, accumulatedActiveMs(정수 ms), error, busy }`. Renderer는 `RUNNING`일 때만 받은 뒤 흐른 시간을 더해 그린다.
- Main `SessionRuntime`이 monotonic clock으로 누적 시간을 세고, 1초마다 목표 시간 도달(→ `AWAITING_END_CONFIRMATION`, 누적 멈춤)을, 10초마다 `POST /sessions/{id}/heartbeats`(`Authorization`·`X-Device-Id`, `{ clientObservedAt, accumulatedActiveMs }`)를 처리한다.
- 상태 명령은 `Authorization`·`Idempotency-Key`·`If-Match-Version`을 붙이고 자동 재전송하지 않는다. 같은 의도(명령·사유·연장 시간)로 다시 누를 때만 처음 보낸 키와 본문을 그대로 쓰고, 의도가 바뀌면 새 키를 만든다. `SESSION_VERSION_CONFLICT`면 `GET /sessions/{id}`로 서버 최신값에 맞춘다. `SESSION_ABANDONED`면 되살리지 않는다.
- 로그아웃(`SIGNED_OUT`)하면 Main은 세션 상태를 비운다. 복구 조회 중 새 세션이 시작되면 늦게 온 복구 결과로 덮어쓰지 않는다.
- 앱 재실행 시 서버가 `RUNNING`이면 서버 확정 시간으로 `pauseReason: APP_RESTART` 일시정지한다(꺼져 있던 시간을 추정해 더하지 않음).
- 재개도 알림 확인 기록이 없으면 서버에 요청하지 않는다(결정 022).
- `GET /sessions/active`·`GET /sessions/{id}` 응답 필드는 Notion에 없어 `{ sessionId, status, version, goalTextSnapshot, targetDurationMs, accumulatedActiveMs }`로 읽는다(2026-10-25 확정, 결정 023).
- 목표 시간 도달은 별도 API 없이 heartbeat(`accumulatedActiveMs = targetDurationMs`)로 서버가 판단한다(결정 023).
- 공통 요청 함수는 200을 기대한 요청이 204를 받으면 `parse(null)`로 '없음'을 판단한다.

### 화면 분석 Channel (2026-10-25, Issue #54, 결정 024)

| Channel | 입력 | 성공 data | Backend |
| --- | --- | --- | --- |
| `analysis:installed-apps:list` | `void` | `{ apps: { bundleId, name, icon(data URL \| null), running }[] }` (실행 중인 앱 먼저) | — |
| `analysis:excluded-apps:get` | `void` | `{ apps: { bundleId, name }[] }` | — |
| `analysis:excluded-apps:save` | `{ apps: { bundleId, name }[] }` (최대 50개, 중복 불가) | `{ apps }` | — (이 기기 `userData/excluded-apps.json`에만 저장) |

- 분석 제외 앱은 서버로 보내지 않는다. 서버는 관측 결과의 `guardStatus: EXCLUDED`만 받는다(2단계).
- 세션이 `RUNNING`이면 Main이 1초마다 Swift 도우미(`build/native/focuson-observer`)로 맨 앞 앱을 보고, 같은 화면이 15초 유지되면 관측 1건(`APP_META` / `EXCLUDED` / `PRIVACY_BLOCKED`)을 만든다. Chrome은 관측하지 않는다(Extension 담당). FocusOn 자기 창과 지원하지 않는 브라우저(Safari 등)는 내용 없이 `EXCLUDED`로 만든다.
- 관측은 Main `AnalysisSender`가 바로 `POST /sessions/{id}/analysis-runs`(`Authorization`·`X-Device-Id`)로 보낸다(Issue #56). 본문은 `{ clientRunId(UUID v4), contentSource: APP_META, observedAt, sequence(세션별 1부터), guardStatus, metadata? }`이고 제외·민감이면 `metadata`를 넣지 않는다. 다시 보내거나 쌓지 않고, `TOO_MANY_REQUESTS`면 60초 쉰다. Renderer IPC는 없다.
- 세션이 `RUNNING`일 때 10초마다 화면 기록 권한을 확인한다. 꺼졌으면 `SessionRuntime`이 바로 `AUTO_PAUSED`(오류 `LOCAL_SCREEN_PERMISSION_REVOKED`)로 멈추고 `POST /sessions/{id}/local-events` `{ localEventCode, occurredAt }`(`Authorization`·`X-Device-Id`)을 보낸다. 일시 실패는 다음 heartbeat 주기에 다시 보낸다. 권한이 없으면 재개 요청을 보내지 않는다.

Notification은 Renderer가 임의의 문장을 보내는 방식이 아니라 Main이 허용한 `titleCode`와 `bodyCode`를 실제 문구로 변환하는 방식으로 제한한다.

## 보안 규칙

- Preload는 Channel 이름을 인자로 받는 범용 `send(channel, data)`를 노출하지 않는다.
- 기능별 이름이 있는 함수만 `contextBridge`로 노출한다.
- Main은 Sender, Payload Type, Session 상태, 권한을 다시 확인한다.
- Renderer가 전달한 File Path와 URL을 검증 없이 사용하지 않는다.
- Content가 큰 원본 Image·Video·Page Text를 반복 IPC로 전송하지 않는다.

## Event 구독

- 구독 함수는 반드시 해제 함수를 반환한다.
- React Effect Cleanup에서 Listener를 해제한다.
- 같은 Window 재실행으로 Listener가 중복 등록되지 않게 한다.
- Main은 닫히거나 파괴된 Window에 Event를 보내지 않는다.

초기 Event 이름은 `permission:changed`, `session:changed`, `analysis:activity-changed`, `window:floating:visibility-changed`, `auth:session:changed`로 사용한다. Event Payload도 `electron/shared/`의 Type으로 제한한다.

## 공식 참고

- [Electron IPC](https://www.electronjs.org/docs/latest/tutorial/ipc)
- [Electron Context Isolation](https://www.electronjs.org/docs/latest/tutorial/context-isolation)

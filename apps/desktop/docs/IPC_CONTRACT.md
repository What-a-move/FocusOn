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

현재 인증 Channel의 Type은 `electron/shared/auth-contract.cts`, 권한·알림 Channel(`permission:screen:get-status`, `permission:screen:open-settings`, `permission:camera:get-status`, `permission:camera:request`, `notification:show`)의 Type은 `electron/shared/permission-contract.cts`에 있다(2026-10-22 구현). `notification:show`는 Main의 `NOTIFICATION_TEXT`에 있는 code만 받는다. Preload는 `window.focusOn.permissions`로 노출한다. Main은 호출 창·Main Frame·허용 URL과 `void` 입력을 검사한다. Renderer에 내부 예외 메시지나 Token을 전달하지 않는다.

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

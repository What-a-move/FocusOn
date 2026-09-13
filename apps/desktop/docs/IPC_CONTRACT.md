# Desktop IPC 계약

> 결정 상태: Channel 이름, 방향, 기본 Payload와 오류 형식은 확정했다. 실제 IPC 구현은 아직 진행하지 않았다.

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

- Renderer는 `error.code`를 사용자에게 보여줄 안전한 문구로 변환한다.
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

실제 Channel을 추가하면 입력·출력 Type을 `electron/shared/`에 정의하고 이 표를 갱신한다.

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

초기 Event 이름은 `permission:changed`, `session:changed`, `analysis:activity-changed`, `window:floating:visibility-changed`로 사용한다. Event Payload도 `electron/shared/`의 Type으로 제한한다.

## 공식 참고

- [Electron IPC](https://www.electronjs.org/docs/latest/tutorial/ipc)
- [Electron Context Isolation](https://www.electronjs.org/docs/latest/tutorial/context-isolation)

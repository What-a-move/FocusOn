# Chrome Extension Message 계약

> 결정 상태: Message 이름, 방향, 기본 Payload와 오류 형식은 확정했다. Message Listener와 실제 Type은 아직 구현하지 않았다.

## 목적

Popup·Service Worker·Content Script가 주고받는 Message의 이름과 데이터 모양을 제한해 잘못된 호출과 개인정보 전송을 막는다.

## 기본 Message 모양

```ts
type ExtensionMessage<TType extends string, TPayload> = {
  type: TType
  requestId: string
  sentAt: string
  source: 'popup' | 'service-worker' | 'content-script'
  payload: TPayload
}

type ExtensionResponse<T> =
  | { success: true; data: T }
  | {
      success: false
      error: {
        code: string
        retryable: boolean
      }
    }
```

- Message는 JSON으로 직렬화할 수 있는 값만 사용한다.
- 함수, DOM Node, Class Instance, 순환 참조 객체를 보내지 않는다.
- 응답 분기는 오류 응답 최상위 `code`를 사용하고 Popup이 사용자 안전 문구로 변환한다.
- 내부 오류 문구와 Stack Trace는 Message에 넣지 않는다.

## 공통 값

```ts
type SessionCommand = 'PAUSE' | 'RESUME' | 'END'

type TrackingCancelReason =
  | 'tab-inactive'
  | 'navigation'
  | 'session-inactive'
  | 'excluded-domain'
```

시간은 ISO 8601 문자열, `thresholdMs`는 Millisecond, `expectedVersion`은 Server의 세션 Version을 사용한다.

## 확정 Message 목록

| Type | 발신 → 수신 | Payload |
| --- | --- | --- |
| `DWELL_TRACKING_STARTED` | Worker → Content | `{ navigationId; startedAt; thresholdMs: 15000 }` |
| `DWELL_TRACKING_CANCELLED` | Worker → Content | `{ navigationId; reason: TrackingCancelReason }` |
| `DWELL_THRESHOLD_REACHED` | Content → Worker | `{ navigationId; reachedAt }` |
| `TAB_CONTEXT_REQUESTED` | Worker → Content | `{ navigationId; maxTextLength }` |
| `TAB_CONTEXT_COLLECTED` | Content → Worker | `{ navigationId; sanitizedUrl; title; text; collectedAt }` |
| `EXCLUSION_STATUS_CHANGED` | Worker → Popup | `{ domain; excluded; changedAt }` |
| `SESSION_STATUS_REQUESTED` | Popup → Worker | `{}` |
| `SESSION_COMMAND_REQUESTED` | Popup → Worker | `{ sessionId; command: SessionCommand; expectedVersion }` |
| `SESSION_STATUS_CHANGED` | Worker → Popup | `{ sessionId; status; version; serverTime }` |
| `CONNECTION_STATUS_CHANGED` | Worker → Popup | `{ connected; lastSyncedAt }` |

실제 구현 시 위 Type과 Payload를 `extension/shared/`의 Discriminated Union으로 정의한다. Content Script가 보낸 `tabId`나 URL을 신뢰하지 않고 `sender.tab`과 현재 Tab 정보를 Worker에서 다시 확인한다.

`sanitizedUrl`은 Query와 Hash를 제거한 Origin·Path만 포함한다. `text`는 Feature PLAN에서 정한 최대 길이와 민감 정보 필터를 통과한 값만 허용한다.

## 전송 방식

- 한 번의 요청·응답은 `runtime.sendMessage` 또는 `tabs.sendMessage`를 사용한다.
- 장시간 연결이 정말 필요한 경우에만 Port 기반 연결을 검토한다.
- Port를 Service Worker를 억지로 계속 살려두는 목적으로 사용하지 않는다.
- Timeout과 수신자 없음 오류를 정상적인 예외 흐름으로 처리한다.

## 검증과 보안

- 수신자는 `type`, `source`, Payload 모양을 확인한다.
- `sender.tab`, Extension ID, URL Origin을 상황에 맞게 검증한다.
- `externally_connectable`을 추가하지 않는 한 외부 Web Page Message를 허용하지 않는다.
- Token, Cookie, 연결 Code, 전체 Server 응답을 Message에 넣지 않는다.
- Page 본문은 제외 Domain 확인과 15초 체류 검증이 끝난 뒤에만 요청하고, 수집 목적에 필요한 길이와 Field로 제한한다.
- 제외 Domain Message에는 원문 본문을 포함하지 않는다.

## 중복 처리

- `requestId`와 Tab·Navigation 식별값으로 중복 요청을 판별한다.
- 동일 Message 재수신 시 상태 변경 명령을 중복 실행하지 않는다.
- Page 이동 후 도착한 오래된 응답은 현재 URL과 비교해 폐기한다.
- `SESSION_COMMAND_REQUESTED`는 `expectedVersion`으로 오래된 상태 변경과 중복 명령을 Server에서 거부할 수 있어야 한다.

## 공식 참고

- [Chrome Extension Message Passing](https://developer.chrome.com/docs/extensions/develop/concepts/messaging)

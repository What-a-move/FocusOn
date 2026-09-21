# Desktop macOS 권한 흐름

> 결정 상태: 권한 요청 UX와 거부 시 동작은 확정했다. 실제 권한 API 연결은 아직 구현하지 않았으며 지원 macOS와 Electron Version에서 검증한다.

## 대상 권한

| 권한 | 사용 목적 | 기본 동작 |
| --- | --- | --- |
| Camera | 사용자 동의 기반 자세·시선 상태 분석 | 동의 전 시작 금지 |
| Screen Recording | 화면 관련성 분석 | 권한 없으면 캡처 중지 |
| Notification | 휴식·집중 상태 알림 | 거부해도 세션 유지 |

Microphone은 현재 요구사항이 없으므로 요청하지 않는다.

## 상태 모델

```text
NOT_DETERMINED
  → REQUESTING
  → GRANTED
  → DENIED / RESTRICTED / UNKNOWN
```

Electron API가 반환하는 `not-determined`, `granted`, `denied`, `restricted`, `unknown` 값을 앱의 공통 권한 Type으로 변환한다.

## 기본 흐름

1. 기능을 실제 사용하기 직전에 권한 상태를 확인한다.
2. 권한이 필요한 이유와 처리 데이터를 먼저 설명한다.
3. 사용자가 동의하면 Main에서 권한 요청을 시작한다.
4. 결과를 IPC 계약의 권한 상태로 Renderer에 반환한다.
5. 거부·제한 상태에서는 기능을 안전하게 중지하고 설정 방법을 안내한다.
6. 앱이 다시 활성화되면 필요한 경우 상태를 재확인한다.

앱 첫 실행과 동시에 모든 권한을 한꺼번에 요청하지 않는다.

## 권한별 주의사항

### Camera

- 명시적인 사용자 동작 후 요청한다.
- Main에서 `systemPreferences.getMediaAccessStatus('camera')`로 먼저 확인하고 `not-determined`일 때만 `askForMediaAccess('camera')`를 호출한다.
- 사용자가 이미 거부했다면 OS Prompt를 반복하지 않고 System Settings 안내를 표시한다.
- Packaging 설정의 Camera Usage Description을 실제 기능 설명과 일치시킨다.
- 분석 중지·세션 종료·제외 앱 또는 제외 도메인 진입 시 Camera Track을 중지한다.
- 원본 영상을 기본 저장하거나 Server에 전송하지 않는다.

### Screen Recording

- Main에서 현재 권한 상태를 확인한다.
- Screen Recording은 `askForMediaAccess` 대상이 아니므로 가짜 요청 API를 만들지 않는다.
- 권한이 없으면 캡처 API를 반복 호출하지 않고 System Settings의 Screen Recording 안내로 이동한다.
- 설정 변경 후 앱 재실행이 필요한지 실제 macOS Version에서 확인한다.
- 제외 앱 또는 제외 도메인이 활성화되면 Capture와 후속 OCR·AI 처리를 함께 중지한다.

### Notification

- 핵심 기능 시작을 막는 필수 권한으로 취급하지 않는다.
- 알림을 실제로 사용하려는 시점에 먼저 용도와 빈도를 설명한다.
- Main의 Electron Notification API로 허용한 알림 Code만 표시하고 Renderer가 임의 문구를 전달하지 않는다.
- 거부 상태에서는 앱 내부 안내로 대체한다.
- 과도한 빈도의 알림을 보내지 않는다.

## 확정한 사용자 흐름

```text
기능 진입
  → 권한이 필요한 이유와 수집 범위 설명
  → 사용자가 계속하기 선택
  → macOS 권한 확인 또는 요청
  → 허용이면 해당 분석만 시작
  → 거부면 Timer는 유지하고 해당 분석만 중지
  → 다시 확인 / 설정 열기 / 나중에 제공
```

권한이 여러 개 필요해도 한꺼번에 요청하지 않고 Camera·Screen Recording·Notification을 각각 실제 사용 직전에 요청한다.

## 제외 상태 연동

- Extension이 제외 도메인을 감지하면 페이지 수집을 즉시 멈추고 Server에 제외 상태를 전달한다.
- Desktop이 제외 상태를 받으면 화면 캡처·OCR·AI·Camera 분석을 모두 중지한다.
- Server 연결이 끊겨 최신 제외 상태를 확인할 수 없으면 안전한 상태가 다시 확인될 때까지 분석을 중지한다.
- 제외 도메인을 벗어났다는 상태와 활성 세션을 다시 확인한 뒤에만 분석을 재개한다.
- Server 동기화 방식이 구현되기 전에는 제외 도메인 연동 기능을 완료로 처리하지 않는다.

## 오류와 UI

- 권한 거부를 시스템 오류처럼 표시하지 않는다.
- 사용자가 취소해도 앱이 종료되거나 무한 요청하지 않는다.
- `다시 확인`, `설정 열기`, `나중에` 행동을 상황에 맞게 제공한다.
- 설정으로 이동하는 URL과 API는 허용 목록으로 제한한다.

## 테스트 기준

- 최초 요청, 허용, 거부, 제한, 설정 변경 후 복귀
- 앱 종료·재실행
- 세션 중 권한 변경
- 제외 앱 진입·복귀
- Camera·Screen 권한이 서로 다른 조합

## 공식 참고

- [Electron systemPreferences](https://www.electronjs.org/docs/latest/api/system-preferences)
- [Electron Notification](https://www.electronjs.org/docs/latest/api/notification)

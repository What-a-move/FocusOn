# Desktop Window 생명주기

> 결정 상태: Window 종류와 생명주기 기준은 확정했다. Main Window와 Floating Feedback Window는 아직 구현하지 않았다.

## Window 종류

| Window | 역할 | 기본 생성 시점 |
| --- | --- | --- |
| Main | 로그인, 학습 설정, 세션 시작, 리포트 | 앱 준비 후 |
| Floating Feedback | 두더지·팻말·집중 복귀 안내 | 학습 세션 시작 시 |

macOS Menu Bar Item은 Window가 아니라 앱 상태 확인과 Window 열기를 위한 진입점으로 다룬다.

Settings는 MVP에서 별도 Window를 만들지 않고 Main Window의 `/settings` Route로 제공한다.

## 상태 모델

```text
NOT_CREATED → CREATING → READY → VISIBLE ↔ HIDDEN → DESTROYED
```

- Window 참조는 Main의 한 Registry에서 관리한다.
- 같은 역할의 Window를 중복 생성하지 않는다.
- `closed` 또는 파괴 Event에서 참조와 Listener를 정리한다.
- 파괴된 Window를 다시 사용하지 않고 필요하면 새로 생성한다.

## 앱 시작과 종료

- `app.whenReady()` 이후 Window를 만든다.
- macOS Dock 또는 Menu Bar에서 앱을 다시 열었을 때 Main Window가 없으면 다시 생성하고, 있으면 앞으로 가져온다.
- Main Window의 닫기 버튼은 Window를 숨기고 앱·학습 세션·Floating Window는 유지한다.
- `Cmd+Q` 또는 Menu Bar의 `FocusOn 종료`만 앱 완전 종료로 처리한다.
- 앱이 두 번 실행되면 새 Process를 종료하고 기존 Main Window를 앞으로 가져온다.
- 완전 종료 전 Capture·Camera·Timer·IPC Listener를 정리한다.
- 강제 종료 후 다음 실행에서 세션 상태를 Server와 다시 동기화한다.

## Floating Feedback Window

- 사용자의 작업 화면을 과도하게 가리지 않는다.
- 학습 세션 중에는 일반 Window보다 위에 표시하되 전체 화면 앱과 시스템 보안 화면을 침범하지 않는다.
- 기본 상태에서는 클릭을 받을 수 있으며 지정된 Drag Handle에서만 이동한다. 버튼과 조작 영역은 `no-drag`로 분리한다.
- 전체 Window 클릭 통과는 기본값으로 사용하지 않는다. 필요하면 사용자가 켤 수 있는 별도 모드로 추가하고 Menu Bar에서 반드시 해제할 수 있게 한다.
- 위치는 Main Process의 설정 저장소에 `{ displayId, xRatio, yRatio }` 형태로 저장한다.
- `displayId`가 사라졌거나 화면 배율·배치가 바뀌면 Primary Display의 `workArea` 안으로 위치를 보정한다.
- 집중 상태가 바뀔 때마다 Window를 새로 만들지 않고 안전하게 상태를 갱신한다.
- 세션 종료와 사용자 숨김 설정을 구분한다.

## Navigation과 외부 Window

- 앱이 허용하지 않은 Remote Page Navigation을 차단한다.
- 새 Window 요청은 목적지를 확인한 뒤 허용한다.
- 외부 Browser로 열 URL은 `https:` 등 허용한 Protocol만 사용한다.
- 인증 Callback과 외부 링크 흐름은 별도 허용 목록으로 관리한다.

## 확정한 기준

- Main Window 닫기: 앱과 세션을 유지하고 Window만 숨김
- Dock·Menu Bar 재진입: 기존 Window 표시 또는 재생성
- Settings: Main Window의 `/settings` Route
- Floating Window: 기본 클릭 가능, Drag Handle만 이동 가능
- 위치 저장: Display ID와 화면 내부 비율을 Main Process에서 저장
- Multi Monitor: 저장된 Display가 없으면 Primary Display 안으로 보정
- 완전 종료: `Cmd+Q` 또는 명시적인 종료 메뉴만 사용

## 공식 참고

- [Electron Process Model](https://www.electronjs.org/docs/latest/tutorial/process-model)
- [Electron Security](https://www.electronjs.org/docs/latest/tutorial/security)

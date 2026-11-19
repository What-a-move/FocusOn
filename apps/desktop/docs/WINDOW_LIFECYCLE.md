# Desktop Window 생명주기

> 결정 상태: Window 종류와 생명주기 기준은 확정했다. Main Window와 Floating Feedback Window를 구현했다. Menu Bar Item은 후속 작업이다.

## Window 종류

| Window | 역할 | 기본 생성 시점 |
| --- | --- | --- |
| Main | 로그인, 학습 설정, 세션 시작, 리포트 | 앱 준비 후 |
| Floating Feedback | 두더지·팻말·집중 복귀 안내 | 첫 알림을 띄울 때 |

macOS Menu Bar Item은 Window가 아니라 앱 상태 확인과 Window 열기를 위한 진입점으로 다룬다.

현재는 macOS 앱 메뉴의 `FocusOn 열기`와 `FocusOn 종료`를 제공한다. 상태를 표시하는 Menu Bar Item은 아직 만들지 않았다.

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

- 구석 알림은 주 화면 아래에 고정하고, Dock이 있을 때 그 위로 올린다. 포키·팻말·버튼 바깥의 클릭은 뒤 앱으로 전달한다.
- 보통 2차와 적극 알림은 주 화면 전체 크기의 `screen-saver` 투명 패널이다. `hud` vibrancy와 연한색 또는 진한색 덮개를 적용한다. 큰 알림이 떠 있는 동안에는 창이 클릭을 받는다.
- 포커스를 요구하지 않고 띄우며 전체 화면 Space에도 표시한다. macOS가 위치를 작업 영역으로 옮길 수 있으므로 표시 뒤 실제 화면 좌표를 다시 지정한다. 큰 창은 `enableLargerThanScreen`으로 노치와 Dock 위치까지 덮는다.
- 같은 크기의 창은 숨겼다가 다시 쓴다. 구석과 전체 화면 사이를 바꿀 때는 이전 창을 닫고 새 크기의 패널을 만든다.

## Navigation과 외부 Window

- 앱이 허용하지 않은 Remote Page Navigation을 차단한다.
- 새 Window 요청은 목적지를 확인한 뒤 허용한다.
- 외부 Browser로 열 URL은 `https:` 등 허용한 Protocol만 사용한다.
- 인증 Callback과 외부 링크 흐름은 별도 허용 목록으로 관리한다.

## 확정한 기준

- Main Window 닫기: 앱과 세션을 유지하고 Window만 숨김
- Dock·Menu Bar 재진입: 기존 Window 표시 또는 재생성
- Settings: Main Window의 `/settings` Route
- Floating Window: 주 화면의 구석 패널 또는 전체 화면 흐림 패널
- 완전 종료: `Cmd+Q` 또는 명시적인 종료 메뉴만 사용

## 공식 참고

- [Electron Process Model](https://www.electronjs.org/docs/latest/tutorial/process-model)
- [Electron Security](https://www.electronjs.org/docs/latest/tutorial/security)

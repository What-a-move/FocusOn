# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: -
- 오류 식별명: floating-poki-window-pushed-above-dock
- 기능명: 포키 알림 창(화면 위 포키)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-12
- 작성자: jae1
- 심각도: 보통
- 상태: 해결
- 관련 Issue·PR: #225, PR #226

## 오류 제목

- 포키 창을 화면 맨 아래에 두라고 해도 macOS가 Dock 높이(63px)만큼 위로 밀어 올려, 전체 화면에서 포키가 바닥에서 떠 보이고 잘림

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple Silicon, 노치 있는 MacBook (주 화면 1512×982, Dock 63px)
- Electron 버전: ^44.1.0
- 서버: 로컬 Docker(dev)
- 화면 기록 권한: 허용
- 카메라 권한: 해당 없음

## 재현 방법

1. Dock을 항상 보이게 둔다(자동 숨기기 끔).
2. 집중 세션 중 포키 알림을 띄운다(데모 모드 ⌘⇧D).
3. 아무 앱이나 전체 화면으로 바꾼 뒤 포키 위치를 본다.

## 기대 결과

- 일반 화면에서는 포키가 Dock 바로 위에, 전체 화면(Dock 숨김)에서는 화면 바닥에 붙는다.

## 실제 결과

- 창을 만들 때 `y = 화면 높이 − 창 높이`로 줘도, 실제 창 위치는 63px 위였다.
- 전체 화면에서 Dock이 사라져도 창은 그 자리에 남아 포키 몸통이 바닥에서 63px 떠 보였다.

## 오류 메시지

```text
(없음) 창 위치만 다름: 창 높이 300 기준 요청 y=682 → 실제 y=619 (63px 차이 = Dock 높이)
```

## 원인 및 해결

- 추정 원인: 창 크기·좌표 계산이 틀렸거나, 레티나 배율 때문에 좌표가 어긋났다.
- 실제 원인: macOS는 새 창을 띄울 때 작업 영역(`screen.workArea`, 화면에서 메뉴 막대·Dock을 뺀 영역) 안으로 위치를 고친다. 그래서 `BrowserWindow`를 만들 때 준 아래쪽 좌표가 Dock 높이만큼 위로 밀렸다. 화면 높이 982 중 작업 영역 아래 끝과 화면 아래 끝의 차이(Dock 높이) 63px가 정확히 밀린 거리였다.
- 해결 방법:
  - 창을 띄운 뒤(`showInactive`) `setBounds`로 진짜 화면 맨 아래 좌표를 다시 준다(`pinFloating`). 띄운 뒤 준 좌표는 고치지 않는다.
  - 포키를 Dock 위로 올리는 일은 창 위치가 아니라 창 안 CSS(`--lift`)로 옮겼다. Dock이 보이면 Dock 높이, 전체 화면이면 0을 IPC(`floating:lift`)로 보낸다. 바뀔 때 스프링 애니메이션으로 움직인다.
  - 창 레벨은 Dock보다 한 층 아래(`modal-panel`)라 전체 화면에서 Dock을 불러오면 Dock이 포키를 덮는다. 빈 곳은 클릭이 통과한다.
- 변경 파일: `electron/main/index.cts`, `electron/shared/intervention-contract.cts`, `electron/preload/index.cts`, `features/floating/FloatingPoki.tsx`, `features/floating/floating.css`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (사용자 실제 화면: 일반 화면 Dock 위, 전체 화면 바닥)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (Electron 테스트 183개, `tsc`·eslint 통과)

## 재발 방지 및 남은 작업

- 화면 가장자리에 붙는 창은 만든 뒤 위치를 다시 주고, 실제 `getBounds()`로 확인한다.
- 보조 모니터(주 화면 외)는 확인하지 않았다. 지금은 주 화면 기준으로만 둔다.

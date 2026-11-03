# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: desktop-ocr-001
- 오류 식별명: observer-cgs-init-crash
- 기능명: Desktop 화면 OCR (화면 분석 5단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 높음 (OCR 다시 보내기 단계 전체가 동작하지 않음)
- 상태: 해결
- 관련 Issue·PR: #140 (발견: #71 실제 기기 QA)

## 오류 제목

- OCR을 요청하면 Swift 화면 관측 도우미가 `CGS_REQUIRE_INIT` assertion으로 종료됨

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Mac 모델·칩: 개발용 Mac (Apple Silicon)
- Electron 버전: 44.1.0 (도우미 단독 실행으로 재현)
- Node.js·pnpm: `xcrun swiftc -O -target arm64-apple-macos14.0`
- 화면 기록 권한: 허용 (`CGPreflightScreenCaptureAccess() == true`)
- 카메라 권한: 해당 없음

## 재현 방법

1. `node scripts/build-native.cjs`로 `build/native/focuson-observer`를 빌드한다.
2. 도우미에 `{"id":1,"cmd":"frontmost"}`를 보내 맨 앞 창의 `windowId`를 받는다.
3. 같은 도우미에 `{"id":2,"cmd":"ocr","windowId":<받은 값>}`를 보낸다.

## 기대 결과

- `{"id":2,"ok":true,"text":"..."}`로 창의 글자를 돌려주고 도우미는 계속 실행된다.

## 실제 결과

- 응답 없이 도우미 프로세스가 종료됐다(exit 134). Main의 `ObserverHelper`는 시간 초과로 null을 받아 OCR 다시 보내기를 건너뛰었다.

## 오류 메시지

```text
Assertion failed: (did_initialize), function CGS_REQUIRE_INIT, file CGInitialization.c, line 44.
```

## 원인 및 해결

- 추정 원인: 도우미가 `NSApplication` 없이 `RunLoop.main.run()`만 돌려 WindowServer(CoreGraphics Services) 연결이 초기화되지 않았다. `ScreenCaptureKit`의 창 캡처가 이 연결을 요구한다.
- 실제 원인: 추정과 같다. `NSApplication.shared`를 만든 뒤에는 같은 요청이 정상 응답했다. `frontmost`·`apps`는 CGS 연결 없이도 동작해서 단위 테스트(가짜 도우미 응답)와 앞선 QA에서 드러나지 않았다.
- 해결 방법: 입력 처리 전에 `NSApplication.shared.setActivationPolicy(.prohibited)`를 호출해 연결을 만들고, Dock·메뉴에는 나타나지 않게 했다.
- 변경 파일: `native/observer/main.swift`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (OCR 0.8~1.5초 응답, 이어진 `frontmost` 요청도 정상)
- [x] 권한 허용·거부 양쪽을 확인했다. (허용에서 확인. 거부는 기존 `CAPTURE_FAILED` 경로 그대로)
- [x] 앱 재실행 후 정상 동작한다. (실제 Electron 세션에서 `APP_META` → `DESKTOP_APP_OCR` 다시 보내기 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`)

## 재발 방지 및 남은 작업

- 도우미를 바꾸면 단위 테스트(가짜 응답)만으로 끝내지 말고 실제 도우미에 `frontmost`·`apps`·`ocr` 세 명령을 모두 보내 확인한다.

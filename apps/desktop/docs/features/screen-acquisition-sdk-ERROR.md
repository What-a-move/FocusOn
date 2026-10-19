# Desktop 화면 수집 빌드 오류

## 오류 기본 정보

- 오류 식별명: screen-acquisition-sdk
- 발생일: 2026-10-19
- 담당 영역: Desktop, 관련 #11 연동 작업
- 심각도: 높음, 상태: 해결

## 재현과 실행 환경

- Node 22.14.0, pnpm 11.19.0, Apple Silicon, CommandLineTools Swift 6.0.3.
- 최초 Swift helper를 swiftc로 컴파일한다. 화면·카메라 권한과 무관한 빌드 오류다.

## 기대 결과와 실제 결과

native helper 빌드가 기대값이나 로컬 SDK에서 `redefinition of module 'SwiftBridging'`, `this SDK is not supported by the compiler`가 발생했다. SDK 6.0.3.1.5와 compiler 6.0.3.1.10 조합 및 중복 modulemap이 관찰됐다.

## 원인 및 해결

- 시스템 SDK를 수정하지 않고 같은 Apple API를 Objective-C·clang으로 호출하도록 변경했다.
- 변경: `electron/native/ScreenCapture.m`, `scripts/build-screen-capture.mjs`.
- 확인: clang native 빌드 통과. 실제 사용자 화면·권한 검증은 보류한 4번 작업이다.

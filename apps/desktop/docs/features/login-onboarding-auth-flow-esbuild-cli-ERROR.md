# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #22-03
- 오류 식별명: `ESBUILD_CLI_WRAPPER_FAILURE`
- 기능명: 로그인·온보딩 인증 흐름
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-18
- 작성자: Codex
- 심각도: 보통
- 상태: 해결
- 관련 Issue·PR: [#22](https://github.com/What-a-move/FocusOn/issues/22)

## 오류 제목

- pnpm Script에서 esbuild CLI 실행 실패

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: 확인하지 않음
- Electron 버전: 44.1.0
- Node.js·pnpm: 24.14.1 / 11.19.0
- 화면 기록 권한: 요청하지 않음
- 카메라 권한: 요청하지 않음

## 재현 방법

1. esbuild 0.28.2를 Desktop 개발 의존성으로 설치한다.
2. `build:electron`에서 `esbuild electron/preload/index.cts ...` CLI를 직접 실행한다.

## 기대 결과

- Preload 번들이 생성된다.

## 실제 결과

- pnpm의 실행 경로가 esbuild 실행 파일을 Node.js 코드로 해석해 SyntaxError가 발생했다.

## 오류 메시지

```text
SyntaxError: Invalid or unexpected token
```

## 원인 및 해결

- 실제 원인: 설치된 pnpm 실행 래퍼와 esbuild 실행 파일의 호출 방식이 맞지 않았다.
- 해결 방법: Node.js 스크립트에서 esbuild의 `buildSync` API를 호출한다.
- 변경 파일: `scripts/build-electron-preload.cjs`, `package.json`

## 확인 결과

- [x] 같은 명령에서 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다. 이번 오류는 OS 권한과 무관하다.
- [x] 앱 재실행 후 Preload가 정상 동작했다.
- [x] Desktop 빌드와 Electron IPC 왕복을 확인했다.

## 재발 방지 및 남은 작업

- 설치 환경의 CLI 래퍼 동작에 의존하지 않도록 빌드 스크립트는 esbuild API를 사용한다.

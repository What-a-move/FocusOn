# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #22-02
- 오류 식별명: `SANDBOX_PRELOAD_MODULE_NOT_LOADED`
- 기능명: 로그인·온보딩 인증 흐름
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-18
- 작성자: Codex
- 심각도: 높음
- 상태: 해결
- 관련 Issue·PR: [#22](https://github.com/What-a-move/FocusOn/issues/22)

## 오류 제목

- 실제 Electron 창에서 `window.focusOn`이 생성되지 않음

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: 확인하지 않음
- Electron 버전: 44.1.0
- Node.js·pnpm: 24.14.1 / 11.19.0
- 화면 기록 권한: 요청하지 않음
- 카메라 권한: 요청하지 않음

## 재현 방법

1. Preload에서 `../shared/auth-contract.cjs`를 import하고 TypeScript Compiler만 실행한다.
2. 정적 앱을 Electron에서 열고 Renderer의 `window.focusOn`을 조회한다.

## 기대 결과

- `window.focusOn.auth.getSession`이 함수로 보인다.

## 실제 결과

- `window.focusOn`이 `undefined`였고, `Object.keys` 호출에서 예외가 발생했다.

## 오류 메시지

```text
TypeError: Cannot convert undefined or null to object
```

## 원인 및 해결

- 실제 원인: `sandbox: true` Preload에서 상대 경로로 분리된 로컬 모듈을 직접 불러올 수 없었다.
- 해결 방법: TypeScript 타입 검사 후 esbuild로 Preload와 공유 런타임 코드를 단일 `.cjs` 파일로 묶었다.
- 변경 파일: `scripts/build-electron-preload.cjs`, `package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml`

## 확인 결과

- [x] 실제 Electron 창에서 `window.focusOn.auth`와 IPC 왕복을 확인했다.
- [ ] 권한 허용·거부 양쪽을 확인했다. 이번 오류는 OS 권한과 무관하다.
- [x] 앱 재실행 후 정적 화면과 Preload가 정상 동작했다.
- [x] 인증 상태 조회 및 미연결 명령의 오류 응답을 확인했다.

## 재발 방지 및 남은 작업

- 샌드박스 Preload에 런타임 import를 추가하면 단일 파일 번들 결과에서 실제 앱 호출까지 확인한다.

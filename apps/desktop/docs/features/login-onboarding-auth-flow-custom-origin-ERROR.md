# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #22-01
- 오류 식별명: `CUSTOM_SCHEME_ORIGIN_COMPARISON`
- 기능명: 로그인·온보딩 인증 흐름의 Electron 실행 기반
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-17
- 작성자: Codex
- 심각도: 높음
- 상태: 해결
- 관련 Issue·PR: [#22](https://github.com/What-a-move/FocusOn/issues/22)

## 오류 제목

- `app://` 화면 이동 검사에서 다른 호스트가 허용될 수 있음

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: 확인하지 않음
- Electron 버전: 44.1.0
- Node.js·pnpm: 24.14.1 / 11.19.0
- 화면 기록 권한: 요청하지 않음
- 카메라 권한: 요청하지 않음

## 재현 방법

1. `isAllowedNavigation`에서 `URL.origin`끼리 비교한다.
2. 현재 화면을 `app://focuson/`, 이동 대상을 `app://untrusted/`로 둔다.
3. `pnpm --filter @focuson/desktop test:electron`을 실행한다.

## 기대 결과

- 다른 호스트로의 이동을 거부해야 한다.

## 실제 결과

- 최초 테스트에서 이동 허용값 `true`가 나와 기대값 `false`와 달랐다.

## 오류 메시지

```text
AssertionError: Expected values to be strictly equal: true !== false
```

## 원인 및 해결

- 실제 원인: Custom Scheme의 `URL.origin`이 두 주소 모두 `null`이 될 수 있어 문자열 비교가 호스트 차이를 구분하지 못했다.
- 해결 방법: 프로토콜·호스트·포트를 각각 비교하고 사용자 정보가 있는 URL을 거부한다.
- 변경 파일: `electron/main/renderer-path.cts`, `tests/electron/renderer-path.test.cjs`

## 확인 결과

- [x] 같은 테스트에서 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다. 이번 오류는 OS 권한과 무관하다.
- [x] 앱 재실행 후 개발·정적 화면이 정상 표시됐다.
- [x] 동일 출처 이동과 다른 호스트 차단 테스트가 통과했다.

## 재발 방지 및 남은 작업

- Custom Scheme 이동 검사에서는 `origin` 문자열만 믿지 않고 프로토콜·호스트·포트를 개별 비교한다.

# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-RACE-01
- 오류 식별명: auth-races-terminal-auth
- 기능명: Desktop 로그인·인증 수명주기
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27 (팀원 검토 #74에서 발견)
- 작성자: jae1 (팀원 수정안 PR #75 기반)
- 심각도: 보통
- 상태: 해결
- 관련 Issue·PR: #77 (원 검토 #74, PR #75)

## 오류 제목

- 일반 API가 재로그인 오류를 돌려줘도 로그인 상태와 저장 토큰이 그대로 남음

## 실행 환경

- macOS 버전: Darwin 25.6.0 (Apple Silicon)
- Electron 버전: 44.1.0 (Main `AuthService`를 `node --test`로 실행, 실제 앱 UI는 미확인)
- Node.js·pnpm: Node 24.14.1
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음
- 재현 방식: 지연시킨 가짜 API·Keychain(vault)과 실제 `AuthSessionStore`. 토큰·인증 코드 원문은 기록하지 않는다.

## 재현 방법

1. 로그인한 상태에서 일반 인증 요청을 보낸다.
2. 서버가 `AUTH_RELOGIN_REQUIRED`·`AUTH_TOKEN_REUSE_DETECTED`·`AUTH_OAUTH_REAUTH_REQUIRED` 중 하나를 돌려준다(첫 응답 또는 만료 뒤 재시도 응답).
3. 화면 상태와 Keychain을 확인한다.

## 기대 결과

- 현재 로그인을 정리하고 `SIGNED_OUT`으로 바꾼다. 네트워크·503·알 수 없는 401은 토큰을 유지한다.

## 실제 결과

- `SIGNED_IN`과 저장 토큰이 그대로 남는다. 다음 요청도 계속 같은 오류를 받는다.

## 오류 메시지

```text
화면 오류 메시지 없음. `tests/electron/auth-races.test.cjs` 27개 중 수정 전 20개 실패·7개 통과 → 수정 후 27개 통과.
```

## 원인 및 해결

- 추정 원인: 재로그인 오류 처리가 refresh 경로에만 있다고 봤다.
- 실제 원인: `authorizedRequest`는 첫 응답과 재시도 응답 모두 `AUTH_ACCESS_TOKEN_EXPIRED`만 따로 처리하고, 나머지 오류는 그대로 돌려줬다. 재로그인 오류로 정리하는 코드는 refresh 실패 경로에만 있었다.
- 공통 원인: "요청을 시작할 때의 로그인 = 지금 로그인인가?"를 Google 로그인 교환에서만 확인했다.
- 해결 방법: 요청 결과가 재로그인 오류이고 요청을 시작한 로그인이 아직 현재 로그인일 때만 토큰 큐 안에서 Keychain을 지우고 `SIGNED_OUT`으로 바꾼다(`finishRequest`·`clearSession`).
- 변경 파일: `electron/main/auth-service.cts`, `electron/main/auth-ipc.cts`, `tests/electron/auth-races.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (재로그인 오류 3종 × 만료 재시도 여부 6개, 일시 오류 3종 토큰 유지)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (실제 Google 계정·Electron 앱 UI 미검증)
- [x] 관련 기능의 회귀가 없다. (Desktop 전체 테스트 통과)

## 재발 방지 및 남은 작업

- 토큰을 쓰거나 지우는 작업은 `runAuthOperation` 큐 밖에서 하지 않는다. 비동기 결과를 반영하기 전에는 시작 세대를 확인한다.
- 실제 Google 계정으로 로그인·계정 전환·로그아웃을 함께 확인한다.

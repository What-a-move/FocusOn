# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-RACE-03
- 오류 식별명: auth-races-restore-refresh
- 기능명: Desktop 로그인·인증 수명주기
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27 (팀원 검토 #74에서 발견)
- 작성자: jae1 (팀원 수정안 PR #75 기반)
- 심각도: 높음
- 상태: 해결
- 관련 Issue·PR: #77 (원 검토 #74, PR #75)

## 오류 제목

- 앱 복구(restore)와 토큰 갱신(refresh)이 같은 Refresh Token을 두 번 보냄

## 실행 환경

- macOS 버전: Darwin 25.6.0 (Apple Silicon)
- Electron 버전: 44.1.0 (Main `AuthService`를 `node --test`로 실행, 실제 앱 UI는 미확인)
- Node.js·pnpm: Node 24.14.1
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음
- 재현 방식: 지연시킨 가짜 API·Keychain(vault)과 실제 `AuthSessionStore`. 토큰·인증 코드 원문은 기록하지 않는다.

## 재현 방법

1. 앱 시작 복구가 진행 중일 때 인증 요청이 refresh를 시작하게 한다.
2. 서버로 간 refresh 요청 수를 센다.

## 기대 결과

- 같은 토큰 회전 한 번을 함께 기다린다.

## 실제 결과

- 같은 Refresh Token으로 refresh가 2번 전송된다. 서버는 두 번째를 재사용으로 보고 로그인 family를 폐기할 수 있다.

## 오류 메시지

```text
화면 오류 메시지 없음. `tests/electron/auth-races.test.cjs` 27개 중 수정 전 20개 실패·7개 통과 → 수정 후 27개 통과.
```

## 원인 및 해결

- 추정 원인: refresh가 single-flight라 중복 전송은 없을 것이라고 봤다.
- 실제 원인: `restoreSession`은 Keychain을 직접 읽고 `rotateRefresh`를 불러 single-flight(`refreshPromise`)를 거치지 않았다.
- 공통 원인: "요청을 시작할 때의 로그인 = 지금 로그인인가?"를 Google 로그인 교환에서만 확인했다.
- 해결 방법: 복구도 같은 `refreshTokens` 경로를 쓰게 해 갱신 경로를 하나로 합쳤다. 처음 설치처럼 저장 토큰이 없으면 오류 없이 `SIGNED_OUT`으로 시작한다.
- 변경 파일: `electron/main/auth-service.cts`, `electron/main/auth-ipc.cts`, `tests/electron/auth-races.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (refresh 전송 2회 → 1회, 늦은 복구 me 응답이 새 로그인을 덮지 않음)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (실제 Google 계정·Electron 앱 UI 미검증)
- [x] 관련 기능의 회귀가 없다. (Desktop 전체 테스트 통과)

## 재발 방지 및 남은 작업

- 토큰을 쓰거나 지우는 작업은 `runAuthOperation` 큐 밖에서 하지 않는다. 비동기 결과를 반영하기 전에는 시작 세대를 확인한다.
- 실제 Google 계정으로 로그인·계정 전환·로그아웃을 함께 확인한다.

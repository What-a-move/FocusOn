# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-RACE-04
- 오류 식별명: auth-races-logout-refresh
- 기능명: Desktop 로그인·인증 수명주기
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27 (팀원 검토 #74에서 발견)
- 작성자: jae1 (팀원 수정안 PR #75 기반)
- 심각도: 보통
- 상태: 해결
- 관련 Issue·PR: #77 (원 검토 #74, PR #75)

## 오류 제목

- 로그아웃 뒤 늦게 끝난 토큰 갱신이 Keychain에 토큰을 다시 저장함

## 실행 환경

- macOS 버전: Darwin 25.6.0 (Apple Silicon)
- Electron 버전: 44.1.0 (Main `AuthService`를 `node --test`로 실행, 실제 앱 UI는 미확인)
- Node.js·pnpm: Node 24.14.1
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음
- 재현 방식: 지연시킨 가짜 API·Keychain(vault)과 실제 `AuthSessionStore`. 토큰·인증 코드 원문은 기록하지 않는다.

## 재현 방법

1. Access Token이 만료 30초 이내인 상태에서 선갱신이 응답을 기다리게 한다(집중 중에는 heartbeat가 10초마다 인증 요청을 보내 자주 겹친다).
2. 아직 유효한 토큰으로 로그아웃을 완료한다.
3. 늦은 갱신 응답을 돌려준다.

## 기대 결과

- 로그아웃 뒤 저장 토큰은 0개다.

## 실제 결과

- 늦은 갱신이 새 Refresh Token을 Keychain에 다시 저장해 1개가 남는다.

## 오류 메시지

```text
화면 오류 메시지 없음. `tests/electron/auth-races.test.cjs` 27개 중 수정 전 20개 실패·7개 통과 → 수정 후 27개 통과.
```

## 원인 및 해결

- 추정 원인: 로그아웃이 서버 session을 되살리는 문제라고 봤다.
- 실제 원인: 서버 문제가 아니라 로컬 저장 경쟁이었다. 로그아웃은 진행 중인 갱신을 기다리지 않고 Keychain을 지웠고, 그 뒤 갱신이 `vault.write`를 실행했다.
- 공통 원인: "요청을 시작할 때의 로그인 = 지금 로그인인가?"를 Google 로그인 교환에서만 확인했다.
- 해결 방법: 로그아웃도 같은 토큰 큐에서 실행해 진행 중인 갱신의 저장이 끝난 뒤 지운다. 로그아웃이 실패하면 보존한 로그인은 새 요청에 계속 쓸 수 있다.
- 변경 파일: `electron/main/auth-service.cts`, `electron/main/auth-ipc.cts`, `tests/electron/auth-races.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (로그아웃 뒤 저장 토큰 1개 → 0개, Keychain 읽기 실패 로그아웃의 큐 복구, 로그아웃 실패 뒤 로그인 유지)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (실제 Google 계정·Electron 앱 UI 미검증)
- [x] 관련 기능의 회귀가 없다. (Desktop 전체 테스트 통과)

## 재발 방지 및 남은 작업

- 토큰을 쓰거나 지우는 작업은 `runAuthOperation` 큐 밖에서 하지 않는다. 비동기 결과를 반영하기 전에는 시작 세대를 확인한다.
- 실제 Google 계정으로 로그인·계정 전환·로그아웃을 함께 확인한다.

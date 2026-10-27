# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-RACE-02
- 오류 식별명: auth-races-oauth-order
- 기능명: Desktop 로그인·인증 수명주기
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27 (팀원 검토 #74에서 발견)
- 작성자: jae1 (팀원 수정안 PR #75 기반)
- 심각도: 높음
- 상태: 해결
- 관련 Issue·PR: #77 (원 검토 #74, PR #75)

## 오류 제목

- 이전 Google 교환·Keychain 쓰기가 늦게 끝나 새 로그인 토큰을 덮어쓰고 지움

## 실행 환경

- macOS 버전: Darwin 25.6.0 (Apple Silicon)
- Electron 버전: 44.1.0 (Main `AuthService`를 `node --test`로 실행, 실제 앱 UI는 미확인)
- Node.js·pnpm: Node 24.14.1
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음
- 재현 방식: 지연시킨 가짜 API·Keychain(vault)과 실제 `AuthSessionStore`. 토큰·인증 코드 원문은 기록하지 않는다.

## 재현 방법

1. A 로그인의 Google 교환 또는 Keychain 쓰기를 지연시킨다.
2. B 로그인을 시작해 완료한다.
3. A의 교환·쓰기를 끝낸다.

## 기대 결과

- 늦게 끝난 A 작업은 B의 토큰·상태를 바꾸지 않는다.

## 실제 결과

- A 쓰기가 B 토큰을 먼저 덮어쓴 다음 A 정리(clear)가 저장소를 비운다. 같은 설치의 A 교환이 늦게 끝나면 서버의 "같은 설치 = 기존 family 교체" 규칙으로 B family가 폐기될 수도 있다.

## 오류 메시지

```text
화면 오류 메시지 없음. `tests/electron/auth-races.test.cjs` 27개 중 수정 전 20개 실패·7개 통과 → 수정 후 27개 통과.
```

## 원인 및 해결

- 추정 원인: 로그인 세대(generation) 확인이 이미 있으니 늦은 결과는 버려질 것이라고 봤다.
- 실제 원인: 세대 확인은 화면 반영만 막고 Keychain 쓰기·삭제 순서는 막지 못했다. A의 `vault.write`가 B 뒤에 실행되고, 이어서 A의 `vault.clear`가 실행되어 B 토큰까지 지웠다. 교환도 동시에 진행돼 서버에서 A가 B를 교체할 수 있었다.
- 공통 원인: "요청을 시작할 때의 로그인 = 지금 로그인인가?"를 Google 로그인 교환에서만 확인했다.
- 해결 방법: Keychain 읽기·쓰기·삭제와 Google 교환을 한 줄(`runAuthOperation` 큐)로 세운다. A 교환이 끝나기 전에 B 교환을 시작하지 않고, 로그인 시작 때마다 이전 시도를 무효화한다(`auth-ipc.cts`).
- 변경 파일: `electron/main/auth-service.cts`, `electron/main/auth-ipc.cts`, `tests/electron/auth-races.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (겹친 교환 순서, 이전 쓰기 뒤 정리, 로그아웃 중 시작한 로그인, 복구 중 늦은 빈 Keychain 읽기)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (실제 Google 계정·Electron 앱 UI 미검증)
- [x] 관련 기능의 회귀가 없다. (Desktop 전체 테스트 통과)

## 재발 방지 및 남은 작업

- 토큰을 쓰거나 지우는 작업은 `runAuthOperation` 큐 밖에서 하지 않는다. 비동기 결과를 반영하기 전에는 시작 세대를 확인한다.
- 실제 Google 계정으로 로그인·계정 전환·로그아웃을 함께 확인한다.

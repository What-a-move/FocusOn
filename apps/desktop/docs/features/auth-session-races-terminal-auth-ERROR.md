# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-RACE-01
- 오류 식별명: auth-session-races-terminal-auth
- 기능명: Desktop 인증 수명주기
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27
- 작성자: 개발 작업 기록
- 심각도: 보통(P2)
- 상태: 로컬 수정·검증 완료 / PR·원격 검토 별도 확인
- 관련 Issue·PR: #74 (PR은 Issue의 연결 항목 참고)

## 오류 제목

명시적 재로그인 오류 뒤 인증 상태 유지

## 실행 환경

- macOS 15.1 arm64, Node24.19.0, 현재 저장소의 Electron44.1.0 의존성
- 컴파일된 AuthService·AuthSessionStore, 모의 API/vault 및 별도 PostgreSQL·실제 HTTP/Keychain
- 화면 기록·카메라 권한: 이 수정의 변경·검증 대상 아님
- 실제 사용자 데이터·Google 성공 token을 사용하지 않는다.

## 재현 방법

일반 API와 만료 후 재시도에 AUTH_RELOGIN_REQUIRED·AUTH_TOKEN_REUSE_DETECTED·AUTH_OAUTH_REAUTH_REQUIRED를 반환한다.

## 기대 결과

현재 사용자 작업만 처리하고, 이전 작업이 새 token·계정 상태를 변경하지 않아야 한다. 토큰 회전은 중복 소비하지 않고 logout 완료 뒤 로컬 token은 없어야 한다.

## 실제 결과

API 오류만 반환하고 SIGNED_IN·저장 token이 남았다.

## 오류 메시지

토큰·인증 코드·비밀번호 원문은 기록하지 않는다. 수정 전 같은27개 회귀에서20개 실패·7개 통과, 수정 후27개 통과로 확인했다. 이는 통제된 기능 검증이며 운영 장애율이 아니다.

## 원인 및 해결

- 실제 원인: 인증 작업의 완료 순서·계정 세대·저장소 변경을 일관되게 보호하지 못한 경로.
- 해결 방법: 모든 요청 결과의 terminal 오류를 중앙 처리하고 현재 인증 세대에 대해서만 Keychain·메모리·화면을 정리한다. 네트워크·503·알 수 없는401은 보존한다.
- 변경 파일: `electron/main/auth-service.cts`, `electron/main/auth-ipc.cts`, `tests/electron/auth-races.test.cjs`

## 확인 결과

- [x] auth-races.test.cjs의 인증 오류3종 × 초기/만료 재시도6개, transient/unknown 보존3개
- [x] Desktop 전체130개, TypeScript·preload·관련 lint 통과
- [x] Backend 58개·PostgreSQL 28개·실제 HTTP/Keychain 45건 회귀 확인
- [x] 독립 리뷰 지적 반영 및 재검토에서 남은 actionable 구현 지적 없음
- [ ] 실제 Google 계정 성공 왕복·실제 앱 UI QA (본인 인증 필요, 합성 세션과 구분)

## 재발 방지 및 남은 작업

`auth-session-races-QA.md`의 범위와 한계를 따른다. API·DB schema·공개 IPC 필드 변경은 없다. 새 PR의 CI·CodeRabbit을 확인하고 merge·Issue종료는 별도 승인받는다.

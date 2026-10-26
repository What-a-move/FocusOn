# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-RACE-06
- 오류 식별명: auth-session-races-keychain-recovery
- 기능명: Desktop 인증 수명주기
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27
- 작성자: 개발 작업 기록
- 심각도: 보통(P2)
- 상태: 로컬 수정·검증 완료 / PR·원격 검토 별도 확인
- 관련 Issue·PR: #74 (PR은 Issue의 연결 항목 참고)

## 오류 제목

자격 증명 보강 중 Keychain 실패 복구 상태 누락

## 실행 환경

- macOS 15.1 arm64, Node24.19.0, 현재 저장소의 Electron44.1.0 의존성
- 컴파일된 AuthService·AuthSessionStore, 모의 API/vault 및 별도 PostgreSQL·실제 HTTP/Keychain
- 화면 기록·카메라 권한: 이 수정의 변경·검증 대상 아님
- 실제 사용자 데이터·Google 성공 token을 사용하지 않는다.

## 재현 방법

근접 만료의 자동 refresh에서 Keychain read/write를 실패시킨다.

## 기대 결과

현재 사용자 작업만 처리하고, 이전 작업이 새 token·계정 상태를 변경하지 않아야 한다. 토큰 회전은 중복 소비하지 않고 logout 완료 뒤 로컬 token은 없어야 한다.

## 실제 결과

독립 리뷰에서 forget 후 credentialGeneration guard로 후속 요청이 막히는데 화면은 SIGNED_IN에 머무르는 회귀를 찾았다.

## 오류 메시지

토큰·인증 코드·비밀번호 원문은 기록하지 않는다. 수정 전 같은27개 회귀에서20개 실패·7개 통과, 수정 후27개 통과로 확인했다. 이는 통제된 기능 검증이며 운영 장애율이 아니다.

## 원인 및 해결

- 실제 원인: 인증 작업의 완료 순서·계정 세대·저장소 변경을 일관되게 보호하지 못한 경로.
- 해결 방법: 현재 인증 세대의 저장소 실패는 OFFLINE으로 전환한다. read실패는 재조회로 복구하고, 회전 후write실패의 옛 token 재사용은 재로그인으로 끝나도록 검증했다.
- 변경 파일: `electron/main/auth-service.cts`, `electron/main/auth-ipc.cts`, `tests/electron/auth-races.test.cjs`

## 확인 결과

- [x] read/write 실패 및 복구2개, clear실패 뒤 큐 복구, 실패logout 후 유효 인증 재사용 회귀
- [x] Desktop 전체130개, TypeScript·preload·관련 lint 통과
- [x] Backend 58개·PostgreSQL 28개·실제 HTTP/Keychain 45건 회귀 확인
- [x] 독립 리뷰 지적 반영 및 재검토에서 남은 actionable 구현 지적 없음
- [ ] 실제 Google 계정 성공 왕복·실제 앱 UI QA (본인 인증 필요, 합성 세션과 구분)

## 재발 방지 및 남은 작업

`auth-session-races-QA.md`의 범위와 한계를 따른다. API·DB schema·공개 IPC 필드 변경은 없다. 새 PR의 CI·CodeRabbit을 확인하고 merge·Issue종료는 별도 승인받는다.

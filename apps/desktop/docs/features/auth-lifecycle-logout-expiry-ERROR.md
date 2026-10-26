# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-LIFECYCLE-001
- 오류 식별명: logout-expiry-401
- 기능명: 로그인 수명주기
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-26
- 작성자: 개발 검토/QA
- 심각도: 보통(P2)
- 상태: 해결
- 관련 Issue·PR: #59 / #60

## 오류 제목

Access Token 만료 401을 logout 성공으로 처리해 서버 로그인 상태가 남음.

## 실행 환경

- macOS 15.1 / arm64, Electron 44.1.0, Node 24.19.0·pnpm 11.19.0.
- 화면 기록·카메라 권한은 이 인증 오류의 재현과 무관하며 권한 변경 없음.
- 실제 QA 서버/DB/Keychain은 임시 데이터와 별도 origin으로 분리.

## 재현 방법

1. 활성 Desktop과 연결 Extension family를 준비한다.
2. 실제 서명된 만료 JWT를 Desktop 메모리에 넣고 클라이언트의 만료 인식을 늦춘다.
3. logout 요청이 실제 서버에서 AUTH_ACCESS_TOKEN_EXPIRED 401을 받게 한다.

## 기대 결과

refresh 후 새 JWT로 logout을 한 번 재요청해 서버 폐기와 로컬 정리를 완료한다.

## 실제 결과

기존 API client는 모든 401을 성공으로 바꿔 Keychain만 지우고 서버 family·연결 Extension·기기 슬롯을 남겼다.

## 오류 메시지

```text
401 AUTH_ACCESS_TOKEN_EXPIRED
```

## 원인 및 해결

- 실제 원인: logout의 401 일괄 성공 처리와 전송 중 만료에 대한 재시도 누락.
- 해결: 204만 API 성공으로 반환하고 code를 서비스에 전달한다. single-flight refresh 후 logout을 한 번 재시도하며 실패 때 token을 유지한다. 명시적 재로그인 오류만 로컬 정리를 허용한다.
- 변경 파일: electron/main/auth-api.cts, auth-service.cts, tests/electron/auth-service.test.cjs.

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다: 실제 HTTP 401→refresh200→logout204, DB 폐기·Extension 거절·슬롯 반환 확인.
- [ ] 권한 허용·거부 양쪽: 이 인증 결함과 무관하여 미실행.
- [x] AuthService 재실행 복구→회전 token Keychain 저장→logout 경로 확인.
- [x] 관련 회귀: Desktop 전체 Electron 89개 테스트 통과.

## 재발 방지 및 남은 작업

만료·갱신 실패·재시도 만료·알 수 없는 401·이미 폐기된 로그인 등 회귀 7개를 추가했다. 실제 Google 계정 인증과 잠금 해제된 전체 앱 UI는 별도 확인한다.

# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #22-OAUTH-01
- 오류 식별명: loopback-sandbox-EPERM
- 기능명: Desktop Google 로그인·온보딩 화면 진입
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-19
- 작성자: 허재원
- 심각도: 낮음
- 상태: 해결
- 관련 Issue·PR: #22

## 오류 제목

- 개발 샌드박스에서 OAuth callback 테스트용 `127.0.0.1` 포트 열기 거부

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: 확인하지 않음
- Electron 버전: package.json 기준 44.1.0
- Node.js·pnpm: 24.14.1 / 11.19.0
- 화면 기록·카메라 권한: 해당 없음

## 재현 방법

1. 제한된 셸에서 `pnpm --filter @focuson/desktop test:electron`을 실행한다.
2. `GoogleOAuthFlow.start()`가 `127.0.0.1`의 임시 포트를 열려고 한다.

## 기대 결과

- 콜백 서버가 로컬 포트에 바인딩되고 테스트 요청을 받는다.

## 실제 결과

- `start()`가 실패를 반환해 콜백 테스트가 진행되지 않았다.

## 오류 메시지

```text
EPERM listen EPERM: operation not permitted 127.0.0.1
```

## 원인 및 해결

- 실제 원인: 테스트 실행 샌드박스가 loopback 포트 바인딩을 제한했다.
- 해결 방법: 로컬 임시 포트 사용을 허용하는 실행 권한으로 같은 테스트를 다시 실행했다.
- 변경 파일: 없음. 앱 코드 오류가 아니다.

## 확인 결과

- [x] 허용된 실행 권한에서 같은 callback 테스트가 통과했다.
- [x] 기존 Electron 테스트도 통과했다.

## 재발 방지 및 남은 작업

- 로컬 listener가 필요한 테스트는 포트 바인딩을 허용하는 환경에서 실행한다. 실제 Google 계정 왕복은 별도 수동 QA로 남는다.

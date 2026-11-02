# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #22-OAUTH-02
- 오류 식별명: test-host-header
- 기능명: Desktop Google 로그인·온보딩 화면 진입
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-19
- 작성자: 허재원
- 심각도: 낮음
- 상태: 해결
- 관련 Issue·PR: #22

## 오류 제목

- 잘못된 Host 요청 테스트가 실제 Host를 바꾸지 못함

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: 확인하지 않음
- Electron 버전: package.json 기준 44.1.0
- Node.js·pnpm: 24.14.1 / 11.19.0
- 화면 기록·카메라 권한: 해당 없음

## 재현 방법

1. `fetch(url, { headers: { Host: 'evil.test' } })`로 callback에 요청한다.
2. `400`을 기대하고 반환된 상태를 확인한다.

## 기대 결과

- 서버가 잘못된 Host를 보고 `400`을 반환한다.

## 실제 결과

- Node `fetch`가 Host를 URL 주소로 다시 설정해 정상 Host 요청 `200`이 발생했다.

## 오류 메시지

```text
AssertionError: 200 !== 400
```

## 원인 및 해결

- 실제 원인: 테스트 도구가 수동 Host 헤더를 그대로 보내지 않았다. 앱의 Host 검증 오류는 아니다.
- 해결 방법: `node:http.get`으로 Host 헤더를 직접 지정해 요청했다.
- 변경 파일: `apps/desktop/tests/electron/google-oauth.test.cjs`

## 확인 결과

- [x] 잘못된 Host는 `400`, 올바른 callback은 `200`으로 확인했다.
- [x] 기존 Electron 테스트도 통과했다.

## 재발 방지 및 남은 작업

- HTTP Host 검증 테스트에는 실제 요청 헤더를 제어할 수 있는 Node HTTP 클라이언트를 사용한다.

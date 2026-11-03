# Extension 개발 중 오류 보고서

> Extension 오류는 Chrome 버전, 권한, 탭 상태를 포함해 재현 가능한 정보로 기록한다.
> URL의 토큰, 개인 데이터, 페이지 본문 원문은 기록하지 않는다.

## 오류 기본 정보

- 오류 번호: tab-analysis-004
- 오류 식별명: dev-stale-service-worker
- 기능명: Chrome 탭·페이지 분석 (화면 분석 3단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 낮음 (개발·QA 환경에서만, 다시 빌드한 뒤 Service Worker만 예전 코드로 동작)
- 상태: 해결 (확인 방법 정리)
- 관련 Issue·PR: #61 실제 기기 QA

## 오류 제목

- 같은 프로필로 확장을 다시 올렸는데 Service Worker만 예전 빌드(다른 서버 주소)로 요청함

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Chrome 버전: Chrome for Testing 153.0.8010.12 (`--load-extension`, 같은 `--user-data-dir` 재사용)
- Extension 버전: 0.1.0 (빌드 결과물 `out/`, 서버 주소만 바꿔 다시 빌드)
- Desktop 연결 상태: 실제 로컬 서버
- 권한 상태: 사이트 접근 허용

## 재현 방법

1. `out/`을 서버 주소 A로 빌드해 프로필 P로 올린다.
2. 브라우저를 끄고 서버 주소 B로 다시 빌드한 뒤 같은 프로필 P로 올린다.
3. 탭에 15초 머문다.

## 기대 결과

- 팝업과 Service Worker 모두 서버 주소 B로 요청한다.

## 실제 결과

- 팝업은 B로 요청했지만 Service Worker의 `sessions/active`·`analysis-runs`는 A로 가서 404를 받았다.
- 디스크의 `service-worker.js`에는 B만 들어 있었다(팝업에서 `fetch('/service-worker.js')`로 확인).

## 오류 메시지

```text
decision.request.fail /api/v1/sessions/6/analysis-runs status 404 code NOT_FOUND (중계 서버에는 요청 기록 없음)
```

## 원인 및 해결

- 추정 원인: 중계 서버의 분석 경로 정규식이 요청 경로와 맞지 않는다.
- 실제 원인: 추정과 달랐다(같은 경로를 curl로 보내면 중계 서버가 정상 응답). Chrome이 프로필의 Service Worker 스크립트 캐시(`Default/Service Worker`)를 그대로 써서, 다시 빌드한 Worker 코드가 반영되지 않았다. 버전 번호(0.1.0)가 같아 새 Worker로 인식하지 않은 것으로 본다.
- 해결 방법: 브라우저를 끄고 프로필의 `Default/Service Worker` 폴더를 지운 뒤 다시 올린다(권한·연결 정보는 유지됨). 또는 `chrome://extensions`에서 확장 새로고침.
- 변경 파일: 없음(QA 절차)

## 확인 결과

- [x] 캐시를 지운 뒤 Service Worker가 새 서버 주소로 요청 (`TAB_META` → `CHROME_DOM` 정상 전송)
- [ ] 웹스토어 업데이트 경로: 버전이 올라가 해당 없음

## 재발 방지 및 남은 작업

- 빌드를 바꾼 뒤 QA할 때는 Service Worker가 새 코드인지 먼저 본다(로그의 요청 주소, 또는 캐시 삭제 후 실행).

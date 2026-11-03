# Extension 개발 중 오류 보고서

> Extension 오류는 Chrome 버전, 권한, 탭 상태를 포함해 재현 가능한 정보로 기록한다.
> URL의 토큰, 개인 데이터, 페이지 본문 원문은 기록하지 않는다.
## 오류 기본 정보

- 오류 번호: tab-analysis-003
- 오류 식별명: host-permission-cors
- 기능명: Chrome 탭·페이지 분석 (화면 분석 3단계)
- 담당 영역: Frontend(`apps`) · Backend 확인 필요
- 대상 앱: Chrome Extension
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 높음 (사이트 접근을 끄면 Extension이 서버와 통신하지 못해 권한 해제 알림·팝업이 모두 멈춤)
- 상태: 보류 (Backend CORS 허용 필요, Frontend는 개발용 Extension ID 고정 완료)
- 관련 Issue·PR: #52·#61 실제 연동 QA

## 오류 제목

- 사이트 접근을 끄면 서버 주소 권한도 사라져 모든 서버 요청이 CORS로 막힘

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Chrome 버전: Chrome for Testing 153.0.8010.12 (Playwright Chromium, `--load-extension`)
- Extension 버전: 0.1.0 (빌드 결과물 `out/`)
- Desktop 연결 상태: 실제 로컬 서버(dev 최신)로 연결
- 권한 상태: `https://*/*`·`http://*/*` 허용 뒤 해제

## 재현 방법

1. 연결하고 사이트 접근을 허용한 뒤 Desktop에서 집중을 시작한다.
2. 사이트 접근을 끈다(`chrome.permissions.remove` 또는 Chrome 확장 메뉴 › 사이트 액세스).
3. 팝업을 연다.

## 기대 결과

- 팝업은 E06(사이트 접근 꺼짐)과 멈춘 세션을 보여 주고, service worker는 `local-events`로 세션 자동 멈춤을 요청한다.

## 실제 결과

- `chrome.permissions.getAll().origins`가 `[]`. 매니페스트 필수 권한(`http://127.0.0.1:8080/*`)까지 사라짐.
- 팝업·service worker의 모든 서버 요청이 CORS로 실패. 팝업은 세션을 못 받아 E01b(허용 안내)를 보이고, `local-events`는 나가지 못함.

## 오류 메시지

```text
Access to fetch at 'http://127.0.0.1:8080/api/v1/sessions/active' from origin 'chrome-extension://…' has been blocked by CORS policy: Response to preflight request doesn't pass access control check
curl -X OPTIONS …/api/v1/sessions/active (Origin: chrome-extension://…) → 401
```

## 원인 및 해결

- 추정 원인: 사이트 접근(선택 권한)만 꺼지고 서버 주소(필수 권한)는 남는다.
- 실제 원인: 추정과 달랐다. Chrome은 넓은 범위를 끄면 그 안에 들어가는 필수 host 권한도 함께 끈다. Extension은 host 권한에만 기대 서버를 불렀고, 서버에는 CORS 설정이 없어 preflight가 401로 실패한다.
- 해결 방법:
  - Backend: `/api/v1/**`에 Extension origin CORS 허용(허용 origin은 환경 변수), preflight는 인증 없이 통과 — 요청 메시지 전달(2026-11-03), Notion `Chrome · macOS 권한 정책` 2.5
  - Frontend: 팀원마다 달랐던 개발용 Extension ID를 매니페스트 `key`로 `fbhlmioghblalhmcokcpoapjpnjccbde`에 고정(결정 017)
- 변경 파일: `public/manifest.json`

## 확인 결과

- [x] 개발용 ID 고정 확인 (새 프로필에서 `chrome-extension://fbhlmioghblalhmcokcpoapjpnjccbde/service-worker.js`)
- [ ] Backend CORS 반영 뒤 권한 해제 → E06·`local-events` 재확인

## 재발 방지 및 남은 작업

- 권한을 줄이는 QA는 서버 요청까지 함께 확인한다(`chrome.permissions.getAll()`과 네트워크 오류).
- 웹스토어 배포본은 `key`를 빼고 스토어 ID를 서버 허용 목록에 추가한다.

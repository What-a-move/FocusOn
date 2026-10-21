# Desktop 다음 작업

> Frontend(`apps`) 담당자는 Desktop 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Backend 인증 API 병합 후 Desktop 실제 로그인 왕복 QA
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 우선순위: 높음
- 상태: Main 인증 흐름과 Renderer 로그인·온보딩 시작·홈 화면 연결 완료. 서버 인증 API 4개 미구현으로 실제 계정 왕복 미검증

## 작업 목적

Backend의 exchange·refresh·auth/me·logout 구현이 병합되면 실제 Google 계정으로 로그인·재실행·로그아웃을 확인하고, 서버의 `onboardingStatus`에 따라 온보딩 시작 화면 또는 홈에 진입하는지 검증한다.

## 선행 조건

- Desktop 기본 화면이 실행되어야 한다.
- `ELECTRON_ARCHITECTURE.md`, `IPC_CONTRACT.md`, `WINDOW_LIFECYCLE.md`의 확정 기준을 따라야 한다.
- 관련 기획서를 `features/`에 작성해야 한다.
- [x] Google exchange·refresh·auth/me·logout 서버 API의 구현 상태를 확인했다. 원격 `dev`(`0776ce3`)에는 Access JWT 발급·검증 서비스만 있고 4개 API Controller와 Refresh Token 처리는 없다. Notion 항목의 `READY`는 계약 준비 상태다.
- [ ] 서버 인증 API 구현 일정을 Backend 담당자와 확인한다. Google code + PKCE + `redirectUri` 요청 형식은 Notion 정본과 Backend 작업 문서에 반영했다.
- [x] Google token endpoint가 요구하는 `redirect_uri` 계약을 확정했다. Desktop은 최초 인증에 쓴 `redirectUri`를 Backend에 보내고 Backend는 검증 후 동일한 값을 Google에 전달한다.
- 계약만 있고 서버 구현이 준비되지 않았다면 서버 응답을 임의로 만들지 않는다. 이번 Issue #22는 인증 완료 후 온보딩 시작 화면 진입까지이며 온보딩 단계 진행·Extension 연결은 별도 Issue다.

## 예상 작업 순서

1. [x] Main·Preload TypeScript 진입점과 전용 Build 설정을 만든다.
2. [x] 개발 `127.0.0.1` Loading과 Production `app://` Loading을 구현한다.
3. [x] Single Instance와 Main Window 숨김·재실행 흐름을 구현한다.
4. [x] 공통 IPC 결과, Main sender 검증, 인증 `getSession`·`google-login:start`·`logout`·상태 Event 경계를 구현한다. 로그인 시작의 브라우저 부분은 5번에서, Backend 인증·로그아웃은 6번에서 연결한다.
5. [x] Google OAuth 기본 브라우저·임시 loopback callback, PKCE·state 검증을 Main에 연결한다. Client ID는 `FOCUSON_GOOGLE_CLIENT_ID`로 받는다.
6. [x] macOS Keychain과 Backend code 교환·refresh·auth/me·logout 요청을 Main에 연결했다. 서버 API가 준비되기 전에는 code callback을 로그인 성공으로 처리하지 않는다. 실제 Google 계정 왕복과 Keychain 수동 QA는 서버 병합 뒤 확인한다.
7. [x] 로그인 화면과 인증 상태에 따른 온보딩 시작 화면·홈 진입, 오프라인 재시도, 로그아웃 버튼을 연결했다. `FocusOn-Design` HTML의 레이아웃·색·포키 에셋으로 시안을 맞췄다.
8. [x] Desktop lint·Build와 인증 테스트 24개를 확인했다. Google 재인증 필요(`AUTH_OAUTH_REAUTH_REQUIRED`)도 재로그인으로 처리한다. 전체 OAuth callback 테스트는 loopback을 허용하는 환경에서 실행했다. 실제 Google 계정 왕복은 Client ID·Backend 준비 뒤 확인한다.

## Backend 병합 뒤 실제 계정 QA 순서

1. 최신 `dev`에서 exchange·refresh·auth/me·logout 네 API와 Google code + PKCE + `redirectUri` 계약이 구현됐는지 확인한다. 요청·응답 필드가 다르면 Notion 정본과 대조해 Backend 담당자와 맞춘다.
2. Google Cloud의 **데스크톱 앱** Client ID와 실행할 Backend origin을 준비하고, Electron 실행 셸에 `FOCUSON_GOOGLE_CLIENT_ID`, `FOCUSON_API_BASE_URL`을 설정한다. 예시는 `apps/desktop/.env.example`을 따른다.
3. 신규 계정으로 `Google로 계속하기`를 누른다. 기본 브라우저 인증 뒤 앱으로 돌아왔을 때 exchange 요청이 성공하고 Refresh Token이 Keychain에 저장되며 온보딩 시작 화면에 진입하는지 확인한다.
4. `onboardingStatus: COMPLETED`인 기존 계정은 홈으로, `INCOMPLETE`인 계정은 온보딩 시작 화면으로 가는지 확인한다. `LINK_REQUIRED` 응답이면 재연결 안내를 확인한다.
5. 앱을 종료·재실행해 Keychain의 Refresh Token으로 refresh rotation과 auth/me 조회가 이루어지는지 확인한다. 네트워크가 끊기면 오프라인 안내와 재시도가 동작하는지 확인한다.
6. 취소·잘못된 callback·만료된 토큰·기기 한도·서버 오류를 재현해 인증 상태와 사용자 안내를 확인한다. 로그아웃 뒤 서버 토큰 폐기와 Keychain 삭제를 확인하고, Token·code·PKCE verifier가 Renderer나 로그에 노출되지 않았는지 점검한다.
7. 결과와 남은 제한을 기능 REPORT에 기록하고 Issue #22 완료 조건을 확인한 뒤 `dev` 대상 PR을 만든다. 온보딩 단계 진행·완료와 홈의 목표·기록 연결은 별도 Issue로 진행한다.

## 이후 작업 후보

- 학습 세션 타이머
- 화면 부유형 두더지 마스코트 피드백 및 표시 모드 설정
- 일회용 연결 코드 기반 Chrome Extension pairing
- Desktop·Extension 학습 세션 상태 동기화
- macOS 활성 앱과 Bundle Identifier 확인
- 분석 제외 앱 설정
- 화면 캡처 권한과 캡처 중지
- MediaPipe 카메라 권한 및 상태 연결

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] Electron 영역별 책임을 문서화했다.
- [x] 현재 범위의 정상 실행, 숨김·재표시를 확인했다.
- [ ] 로그인 권한 거부 상황을 확인했다.
- [ ] 새 기능을 완료했다면 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `ELECTRON_ARCHITECTURE.md`를 확인했다.
- [ ] `IPC_CONTRACT.md`에서 이번 기능에 필요한 Channel과 Payload를 확인했다.
- [ ] `MACOS_PERMISSION_FLOW.md`와 `WINDOW_LIFECYCLE.md`의 확정 기준을 확인했다.
- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 관련 Issue와 Branch가 있다.

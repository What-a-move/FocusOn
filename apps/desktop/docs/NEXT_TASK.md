# Desktop 다음 작업

> Frontend(`apps`) 담당자는 Desktop 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Google OAuth와 Backend 인증 연동
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 우선순위: 높음
- 상태: Electron 실행·Window·인증 IPC 경계 완료, 실제 인증 연동 예정

## 작업 목적

구현된 Electron 인증 IPC 경계 위에 Google 브라우저 인증, 토큰 보관 및 Backend API를 연결한다.

## 선행 조건

- Desktop 기본 화면이 실행되어야 한다.
- `ELECTRON_ARCHITECTURE.md`, `IPC_CONTRACT.md`, `WINDOW_LIFECYCLE.md`의 확정 기준을 따라야 한다.
- 관련 기획서를 `features/`에 작성해야 한다.
- Google exchange·refresh·auth/me 서버 API의 구현 상태와 담당 일정은 구현 전에 확인한다. 계약만 있고 서버 구현이 준비되지 않았다면 Main·Preload 기반과 IPC까지만 연결하고 서버 응답을 임의로 만들지 않는다.

## 예상 작업 순서

1. [x] Main·Preload TypeScript 진입점과 전용 Build 설정을 만든다.
2. [x] 개발 `127.0.0.1` Loading과 Production `app://` Loading을 구현한다.
3. [x] Single Instance와 Main Window 숨김·재실행 흐름을 구현한다.
4. [x] 공통 IPC 결과, Main sender 검증, 인증 `getSession`·`google-login:start`·`logout`·상태 Event 경계를 구현한다. 로그인 시작·로그아웃의 실제 동작은 5번에서 연결한다.
5. [ ] Google OAuth 기본 브라우저·임시 loopback callback, macOS Keychain 및 Backend 인증 API를 Main에 연결한다.
6. [x] 현재 범위의 Desktop Build와 개발·정적 실행을 확인한다.

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

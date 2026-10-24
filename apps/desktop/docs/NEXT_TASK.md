# Desktop 다음 작업

> Frontend(`apps`) 담당자는 Desktop 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Desktop 온보딩 단계 구현과 대시보드 진입
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop (Extension pairing UI 연동 포함)
- 우선순위: 높음
- 상태: 온보딩 4단계 UI(닉네임 → Extension 연결 → 권한·동의(동의 → 권한 확인) → 제외 사이트 `/onboarding/sites` → 완료 `/onboarding/done`)와 다크 모드를 `FocusOn-design/onboarding.html` 시안대로 구현했다. macOS 권한(2026-10-22)과 온보딩 Backend 연결 구조(Main `OnboardingService`·온보딩 IPC·토큰 만료 재시도, 2026-10-22)를 구현했다. 남은 일은 Backend 온보딩 API가 완성된 뒤 실제 계정 QA, 결과 REPORT, #36 PR이다. Backend 합의안은 2026-10-24 확정했다(결정 019).

## 작업 목적

로그인 뒤 닉네임, Extension 연결, 필수 권한·동의, 분석 제외 사이트 설정을 구현하고 모든 필수 단계를 완료하면 대시보드로 이동한다. 목표·시간·개입은 온보딩에서 설정하지 않고 대시보드의 `오늘 학습` 흐름으로 분리한다.

## 선행 조건

- 인증 기반과 로그인 화면이 `dev`에 반영되어 있어야 한다 (Issue #22).
- `ELECTRON_ARCHITECTURE.md`, `IPC_CONTRACT.md`, `WINDOW_LIFECYCLE.md`의 확정 기준을 따라야 한다.
- 기획서 [`onboarding-flow-PLAN.md`](./features/onboarding-flow-PLAN.md)와 Frontend [Issue #36](https://github.com/What-a-move/FocusOn/issues/36)를 확인한다. Backend API 구현은 담당 Backend 작업에서 진행한다.
- 닉네임·pairing·consent·analysis-excluded-domains API 정본을 따른다.
- 앱 시작·복귀 때 Desktop Main이 로컬 필수 권한을 다시 확인한다. 서버 응답만으로 OS 권한 허용을 가정하지 않는다.
- 사용자가 제외 도메인을 저장하거나 빈 목록 건너뛰기를 명시적으로 선택한 뒤에만 완료 marker를 기록한다.
- 서버 API 구현 전에는 저장·온보딩 완료 성공을 임의로 처리하지 않는다.

## 예상 작업 순서

1. 부분 완료: 로그인 뒤 서버 `onboardingStatus`가 `INCOMPLETE`면 `/onboarding/`(재연결 필요면 `/onboarding/extension/`)으로 보낸다(2026-10-22). 남은 일은 `GET /api/v1/users/me/onboarding-readiness`의 `currentStep`으로 이어갈 단계를 정하는 것이다.
2. 완료: 공통 온보딩 틀과 닉네임 입력 UI, 클라이언트 형식 검증을 구현했다. 닉네임 저장·중복 확인은 `OnboardingService.saveProfile`(`PUT /users/me/profile`, 409 중복)로 연결했다(2026-10-22).
3. 완료: Desktop 연결 안내·코드 발급(`POST /pairing-codes`)·3초 간격 readiness 확인(`extensionLinked`)·완료·오류 표시를 연결했다.
4. 완료: 권한·동의 UI(동의 → 권한 확인)와 Main/Preload의 macOS 화면 기록 확인·시스템 설정 열기·카메라 요청·시험 알림 연결(2026-10-22). 시스템 설정에서 돌아오면(창 포커스) 화면 기록을 자동으로 다시 확인한다. 동의 저장은 `savePermissionsStep`으로 연결했다. 알림은 필수이며(결정 018) `PermissionsStep`은 화면 기록과 알림이 모두 준비되어야(`screenReady && notifyReady`) 다음으로 넘어간다. 카메라 동의만 선택이다.
5. 완료: 제외 사이트 등록·빈 목록 건너뛰기(`/onboarding/sites`)와 저장 API·완료 marker(`analysisExcludedDomainsStepCompleted`) 기록을 연결했다.
6. 서버가 `COMPLETED`를 반환하면 대시보드로 이동한다. 목표·시간·개입은 후속 대시보드 Issue에 남긴다.
7. 백엔드 API 병합 뒤 신규 계정, 앱 재실행, 권한 변경, 네트워크 오류, Extension 연결을 실제 계정으로 검증한다.

## Issue #22 로그인: Backend 병합 뒤 실제 계정 QA 순서 (PR #39 병합 완료)

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

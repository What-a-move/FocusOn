# Desktop 다음 작업

> Frontend(`apps`) 담당자는 Desktop 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: 모듈 연동 로거 (Issue #101, `feat/101-desktop-logger`)
- 상태: Main 로거·요청 traceId·상태 전이·알림·절전 기록 구현(2026-10-29). 남은 일: 서버가 `X-Trace-Id`를 로그에 찍으면 같은 traceId로 추적표 1·7·8번 확인, 그 뒤 REPORT.
- 다음 Issue 후보: Extension 로거·로그 복사 버튼, 세션 상태 전이표를 shared-types로 모으는 리팩토링.

- 작업명: 설정 화면 집중·분석·권한·화면 (Issue #80, `feat/80-desktop-settings`)
- 상태: 실제 계정·로컬 서버 QA와 REPORT 완료(2026-10-31, `settings-REPORT.md`). 저장 실패 시 이전 값 되돌림까지 확인. 남은 일 없음.
- 다음 Issue 후보: 오류 문구 개선(애매한 문구, 로그인 진행 중 빨간색, 시험 알림 실패를 성공으로 표시, 개발 실행의 `Electron` 이름 안내).
- 다음 Issue 후보: 설정 계정·기기 연결 탭(D24·D25, 계정 삭제 API는 아직 없음), 집중 기록 화면.

- 작업명: 로그인·인증 경계 5건 수정 (Issue #77, `fix/77-desktop-auth-races`)
- 상태: 수정·회귀 테스트 완료(2026-10-27). 남은 일: 실제 Google 계정으로 로그인·계정 전환·로그아웃·집중 중 갱신을 팀원과 함께 확인.


- 작업명: 세션 결과 화면 (Issue #73, `feat/73-desktop-session-result`)
- 상태: 결과 화면·summary/learning-summary IPC·세션 종료 후 이동 구현(2026-10-27). 남은 일: 백엔드에 summary 응답 필드 확인(결정 027 제안), API 병합 뒤 실제 세션 QA와 REPORT.
- 다음 Issue 후보: 학습 정리 상세(`정리 보기`), 집중 기록(달력·일간) 화면, 설정 화면(제외 앱·사이트·알림 방식).


- 작업명: Desktop 화면 OCR (Issue #71, `feat/71-desktop-ocr`)
- 상태: 도우미 창 캡처·Vision OCR, 다시 보내기·글자 정리·contentSource별 순서 번호 구현(2026-10-26). 남은 일: 화면 기록 권한이 있는 실제 Electron에서 창 캡처 확인, 백엔드 `analysis-runs` 병합 뒤 실제 QA와 REPORT.
- 다음 Issue 후보: 화면 위 포키 F06·F07, 세션 결과 화면, 설정 화면(제외 앱·사이트·알림 방식).


- 작업명: 화면 위 포키 이탈 알림 (Issue #63, `feat/63-desktop-floating-poki`)
- 상태: 알림 조회·포키 창·acknowledgements 구현(2026-10-26). 남은 일: 실제 Electron에서 전체화면 앱 위 표시·포커스 QA, 백엔드 interventions 병합 뒤 실제 QA와 REPORT, 알림 목록 응답 형식 백엔드 확인. F06(분석 제외·민감)·F07(연결 끊김) 말풍선, 휴식 포키(sleepy) 에셋, 위치 옮기기는 다음 작업.
- 다음 Issue 후보: 화면 분석 5단계 Desktop OCR(메타 근거 UNRELATED는 이탈로 세지 않으므로 Desktop 앱 이탈 감지에 필요), 세션 결과 화면.


- 작업명: 화면 관측 결과 서버 전송 (Issue #56, `feat/56-desktop-analysis-sending`)
- 상태: `AnalysisSender` 구현(2026-10-25). 남은 일: `analysis-runs` API 병합 뒤 실제 QA와 REPORT.
- 다음 Issue 후보: 화면 분석 3단계 — Extension 탭·DOM 분석(결정 013: `optional_host_permissions` 전환, `TAB_META` → 관련 없음·판단 보류면 `CHROME_DOM`(2026-10-26 기준), 민감·제외 판정, `local-events`). 그다음 4단계 개입 표시(Floating Poki), 5단계 Desktop OCR.


- 작업명: 화면 관측 기반과 분석 제외 앱 (Issue #54, `feat/54-desktop-screen-observation`)
- 상태: Swift 도우미·관측 상태 기계·제외 앱(로컬)·화면 권한 철회 처리 구현(2026-10-25). 남은 일: `local-events` API 병합 뒤 실제 QA와 REPORT, 실제 Electron 세션에서 관측 확인.
- 다음 Issue: 화면 분석 2단계 `analysis-runs` 전송은 Issue #56에서 진행했다(위 항목). OCR(대상 창 캡처·Vision OCR, 텍스트 2,000자·이메일/전화/토큰 제거·민감 패턴 차단, `APP_META` 결과가 관련 없음·판단 보류면 항상(2026-10-26 변경), 같은 창 내용 변경 30초)은 순서를 바꿔 5단계로 미뤘다(결정 024). 다른 브라우저는 `EXCLUDED`로 처리하기로 확정했다.


- 작업명: 집중 화면 (Issue #48, `feat/48-desktop-focus-session`)
- 상태: 화면·SessionRuntime·세션 명령 IPC 구현 완료(2026-10-25). 남은 일: 세션 API 병합 뒤 실제 계정 QA와 REPORT.
- 다음 Issue 후보: Extension 팝업 세션 화면(E02·E03·E06)과 Extension Access Token 갱신. 홈에서 진행 중 세션으로 돌아가는 진입점도 함께 검토.


- 작업명: 세션 설정 (Issue #46, `feat/46-desktop-session-setup`)
- 상태: 화면·세션 IPC 구현 완료(2026-10-24). 남은 일: 세션 API 병합 뒤 실제 계정 QA와 REPORT.
- 다음 Issue 후보: 집중 화면(`session.html`, D10) — 시작한 세션은 `sessionStorage['focuson.activeSession']`에 있다. 그 전까지는 시작 성공 뒤 `집중 화면은 준비 중이에요` 안내만 있다.


- 작업명: 목표 설정 (Issue #44, `feat/44-desktop-goal-setting`)
- 상태: 화면·목표 IPC·수동 확정 구현 완료(2026-10-24). 남은 일: 목표 API 병합 뒤 실제 계정 QA와 REPORT.
- 다음 Issue 후보: 세션 설정(`session-setup.html`, D09) — 완료 화면 `집중 시간 정하기`와 연결. 저장한 목표는 `sessionStorage['focuson.confirmedGoal']`에 있다.


- 작업명: 홈 대시보드 화면 구조 (Issue #41, `feat/41-desktop-dashboard`)
- 상태: 화면·리포트 IPC·상태 UI 구현 완료(2026-10-24). 남은 일: 리포트 API 병합 뒤 실제 계정 QA와 REPORT. 날짜·지난 목표 클릭 이동은 집중 기록 화면 Issue에서 연결한다.
- 다음 Issue 후보: 목표 설정(`goal-setting.html`, D06·D07).

## 이전 작업

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
- 카메라 권한 및 로컬 카메라 분석(AVFoundation + Apple Vision) 상태 연결

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

## 로그인 최종 QA 후속 (2026-10-26)

- PR #60에서 logout 만료 401을 구분하고 refresh 뒤 한 번 재요청하도록 수정했다. 이 Desktop 재시도 수정 자체는 endpoint를 추가하지 않는다. PR #60의 Server 작업은 POST /api/v1/auth/refresh와 POST /api/v1/auth/logout을 추가한다.
- Mac 잠금 해제 후 실제 Google 테스트 계정으로 시스템 브라우저 인증→callback→교환→온보딩/홈→재실행→logout을 확인한다.
- SwiftBridging의 오래된 중복 modulemap 때문에 일반 빌드가 실패한다. 임시 VFS 환경에서는 전체 build 성공을 확인했지만 정식 Command Line Tools 설치 복구는 관리자 인증 후 별도 진행한다. 배포 앱 사용자에게 이 임시 환경을 요구하지 않는다.

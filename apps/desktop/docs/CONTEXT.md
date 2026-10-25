# Desktop 작업 컨텍스트

> Frontend(`apps`) 담당자가 Desktop 작업을 시작할 때 읽는 플랫폼 전용 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 담당 영역

- Frontend 공통 담당 범위: `apps/`
- 이 문서의 대상 앱: `apps/desktop/`
- macOS Electron 데스크톱 앱
- React + TypeScript 화면
- 학습 세션과 타이머
- 화면 부유형 두더지 마스코트와 집중 상태 피드백
- Google 로그인 및 Chrome Extension 연결 상태
- 활성 앱 확인과 화면 분석 연결
- 카메라 권한 및 MediaPipe 결과 연결

## 현재 상태

### 완료된 작업

- Next.js 기본 화면을 FocusOn 로그인·온보딩 시작·홈 화면으로 교체했다.
- Desktop 앱 기본 실행·빌드 구조를 확인했다.
- 공통 모노레포와 pnpm Workspace·Turborepo를 사용한다.
- Next.js App Router와 Tailwind CSS v4 기반 화면 구조를 확인했다.
- Main·Preload TypeScript Build, Next.js Static Export, `app://` Production Loading 방향을 확정했다.
- Single Instance, Main Window 닫기, Settings Route, Floating Window 위치·Drag 기준을 확정했다.
- IPC Channel·오류 형식과 macOS 권한 요청 UX를 확정했다.
- Google OAuth 기본 브라우저·loopback callback 방식과 로그인용 Main·Preload IPC 계약을 확정했다.
- Electron Main·Preload 진입점과 전용 TypeScript Build를 추가했다. 개발 `127.0.0.1:3000` 및 정적 `app://focuson/` 화면을 실제 앱에서 확인했다.
- Main Window 닫기 시 숨김, 두 번째 실행 시 기존 창 재표시, 외부 Navigation·새 Window 차단과 기본 권한 거부를 구현했다.
- `window.focusOn.auth`의 상태 조회·로그인 시작·로그아웃·상태 Event 구독 경계와 공통 Type을 구현했다. Main은 현재 창의 Main Frame과 입력값을 검증한다.
- 초기 IPC 경계만 있을 때는 인증 정보 저장소와 Backend API가 없어 `SERVICE_UNAVAILABLE`을 반환했다. 이후 Main에 Backend 인증 요청과 macOS Keychain 보관을 연결했다.
- Issue #22 범위는 로그인 완료 후 온보딩 시작 화면 진입까지로 정리했다. 닉네임·Extension 연결·권한·제외 사이트 설정은 후속 Issue로 분리한다.
- 2026-10-19 원격 `dev`(`0776ce3`)에서 Backend Access JWT 서비스 병합을 확인했다. exchange·refresh·auth/me·logout API는 여전히 미구현이며 서버 담당 일정은 미확인이다.
- Google 데스크톱 Client ID가 있을 때 Main이 기본 브라우저를 열고 `127.0.0.1` 임시 callback에서 state·code를 검사하는 흐름을 구현했다. 5분 timeout·취소·중복 요청·listener 정리도 다룬다. 서버가 실제 토큰을 발급하기 전에는 로그인 성공으로 바꾸지 않는다.
- Main에 Google code 교환, refresh rotation, auth/me 복구, 서버 로그아웃 요청과 macOS Keychain Refresh Token 보관을 연결했다. `FOCUSON_API_BASE_URL` 설정이 필요하다. 서버 인증 API는 원격 `dev`에서 아직 미구현이므로 실제 Google 계정 왕복은 확인하지 못했다.
- Renderer에 Google 로그인 버튼, 오프라인 재시도, 로그아웃과 `onboardingStatus` 기반 온보딩 시작 화면·홈 전환을 연결했다. 온보딩 단계 진행은 Issue #22에 포함하지 않는다.
- `FocusOn-Design`의 로그인·온보딩·홈 HTML과 동일한 로고·포키·메뉴 에셋을 사용해 Renderer 시안을 맞췄다. 브라우저에서 세 화면을 원본 HTML과 대조하고 Production Electron `app://focuson/` 로그인 화면의 에셋 로딩도 확인했다. Figma `Desktop UI` 캔버스 구조·스크린샷은 확인했지만 세부 `get_design_context`는 Figma Starter 호출 한도에 막혀 HTML 시안을 세부 기준으로 삼았다.

### 진행 중인 작업

- Issue #36에서 `FocusOn-design/onboarding.html` 시안(2026-10-21 합의 순서)에 맞춰 온보딩 4단계 UI를 구현했다: 닉네임(`/onboarding`) → Extension 연결(`/onboarding/extension`) → 권한·동의(`/onboarding/permissions`, 동의 → 권한 확인) → 분석 제외 사이트(`/onboarding/sites`). 서버 호출은 Main `OnboardingService`가 Desktop 토큰으로 처리한다(만료 시 refresh 1회·재시도 1회). Backend 온보딩 API가 아직 없어 실제 왕복은 미검증이다. macOS 권한(화면 기록·카메라·시험 알림)은 Main IPC로 실제 연결했다. 로그인 뒤 온보딩이 필요한 사용자는 `/onboarding/`으로 이동한다(2026-10-22).
- 서버 API 병합 뒤 실제 Google 계정 왕복, Keychain 저장·복구, 신규·기존 사용자 화면 진입의 수동 QA가 필요하다.
- 로그인 후 온보딩은 닉네임 → Extension 연결 → 필수 권한·동의 → 분석 제외 사이트 설정 순서로 진행하며, 완료 뒤 대시보드에 진입한다. 목표·시간·개입 설정은 대시보드 `오늘 학습`의 별도 흐름이다.
- 온보딩 재개는 `GET /api/v1/users/me/onboarding-readiness`의 `currentStep`으로 결정한다. 앱 시작·복귀 때 Desktop Main이 OS 권한을 다시 확인하고 준비 상태를 갱신한다. API 구현은 Backend 담당 범위다.
- 확정된 Server 세션 상태와 타이머 필드를 Desktop 상태 모델에 반영해야 한다.
- 학습 상태에 따른 화면 부유형 마스코트·팻말·말풍선 표현을 확정해야 한다.
- 일회용 연결 코드 pairing과 상태 명령·Heartbeat 계약을 구현해야 하며 실시간 동기화 방식은 추가 확정이 필요하다.

- Issue #41(2026-10-24): 홈 대시보드를 `FocusOn-design/main.html`대로 `features/dashboard/`에 구현했다. 집중 달력·오늘 집중 분·이번 달 일수·지난 목표는 Main `ReportService`(리포트 IPC)로만 받고, 로딩·오류·오프라인(마지막 확인 시각)·빈 상태를 보여 준다. 리포트 API가 dev에 없어 실제 왕복은 미검증이며, 가짜 bridge로 라이트·다크·오프라인 화면을 확인했다.

- Issue #44(2026-10-24): 목표 설정(D06·D07)을 `FocusOn-design/goal-setting.html`대로 `/goal/`에 구현했다. 홈 `목표 설정하기`에서 들어오고 ✕로 홈에 돌아간다. AI 결과 4종·질문 최대 2회·AI 장애/오프라인/질문 소진 시 수동 확정·저장 성공 후 완료 화면을 Main `GoalService`(목표 IPC)로 처리한다. 목표 API가 dev에 없어 실제 왕복은 미검증이며, 가짜 bridge로 약어 확인→확정→저장, AI 장애, 추천 선택(다크)을 확인했다. 완료 화면 `집중 시간 정하기`는 세션 설정 Issue에서 연결한다.

- Issue #46(2026-10-24): 세션 설정(D09)을 `FocusOn-design/session-setup.html` 레이아웃으로 `/session-setup/`에 구현했다. 시간 선택지는 Notion 계약(30분·1시간·2시간·직접 5~300분)을 따른다. 기본값은 집중 설정 API, 시작은 Main `SessionService`(Idempotency-Key·X-Device-Id·기기 시간대)로 보낸다. 목표 설정 완료 화면에서 들어오고 `홈으로`로 나간다. 세션 API가 dev에 없어 실제 시작은 미검증이며, 가짜 bridge로 키보드 이동·범위 제한·알림 0개 차단·오프라인 뒤 같은 키 재시도·시작 성공(다크)을 확인했다. 집중 화면(타이머)은 다음 Issue다.

- Issue #48(2026-10-25): 집중 화면(D10·D11·D15·D16)을 `FocusOn-design/session.html`대로 `/session/`에 구현했다. 타이머는 Main `SessionRuntime`이 monotonic clock으로 세고 1초 목표 도달·10초 heartbeat를 처리한다. 일시정지·재개·연장·종료는 Idempotency-Key·If-Match-Version, 버전 충돌 시 최신 조회, 앱 재실행 시 APP_RESTART 일시정지, 서버 자동 종료(ABANDONED) 안내를 처리한다. 세션 API가 dev에 없어 실제 왕복은 미검증이며, 가짜 bridge로 실행·멈춤·끝내기 확인·시간 종료(Esc로 안 닫힘)·연장·완료(다크)를 확인했다. 개발 중 오류 2건(Tailwind `ring` 클래스 충돌, dialog margin 초기화)을 ERROR 문서로 남겼다.

- Issue #54(2026-10-25): 화면 분석 1단계. Swift 도우미(`native/observer`, macOS 14+)로 맨 앞 앱·창 제목·설치된 앱 목록을 읽고, Main `ScreenObserver`가 세션 `RUNNING` 중 같은 화면 15초 유지 시 관측 1건(Chrome 건너뜀, 제외 앱·FocusOn 창·지원하지 않는 브라우저 `EXCLUDED`, 고정 민감 앱·창 제목 단어 `PRIVACY_BLOCKED`, 그 외 `APP_META`)을 만든다. 서버 전송은 2단계다. 분석 제외 앱은 번들 ID로 이 기기에만 저장하고 온보딩 제외 단계에서 고른다(`exclusions.html` 앱 대화상자). 세션 중 화면 기록 권한이 꺼지면 바로 `AUTO_PAUSED` + `local-events`를 보내고 집중 화면에 `권한 꺼짐` 안내를 보여 준다. dialog 가운데 정렬을 `globals.css`에서 한 번에 고쳤다(ERROR 문서). `local-events` API가 dev에 없어 실제 왕복은 미검증이다.

### 아직 진행하지 않은 작업

- 화면 분석 2단계: 대상 창 캡처·Apple Vision OCR·`analysis-runs` 전송
- 설정 › 분석·권한의 제외 앱 관리 (설정 화면 작업 때)
- MediaPipe 카메라 분석 연동
- 인증 외 Server API 연결
- 화면 부유형 마스코트 표시 및 사용자 문구 설정
- 일회용 연결 코드 기반 Extension pairing

## 현재 기술

- 프레임워크: Next.js, React, Electron
- 언어: TypeScript
- 스타일: Tailwind CSS v4, PostCSS, `app/globals.css`
- 패키지 관리: pnpm Workspace, 작업 실행: Turborepo
- 실행: Renderer는 `pnpm dev:desktop`, Electron은 별도 터미널에서 `pnpm --filter @focuson/desktop dev:electron`
- 빌드: `pnpm build:desktop` 또는 `pnpm turbo run build --filter=@focuson/desktop`
- 정적 앱 미리보기: `pnpm --filter @focuson/desktop preview:electron`
- 확정한 Build 방향: Main·Preload는 `dist-electron/`, Renderer는 Next.js `out/`, Packaging은 electron-builder

## 현재 데이터 흐름

```text
사용자
  → Desktop Renderer
  → Electron Main / macOS 기능
  → Server API 또는 AI 분석
  → 집중 상태·화면 부유형 마스코트·알림·리포트 화면

학습 세션의 시작·일시정지·재개·종료 상태와 기준 시간은 Server를 기준으로 관리하고,
Desktop은 카메라·화면·활성 앱 결과를 반영하는 주 클라이언트로 동작한다.
```

## 반드시 지켜야 하는 조건

- macOS 권한이 없는 상태에서도 앱이 비정상 종료되지 않아야 한다.
- 사용자가 분석 제외 앱이나 도메인으로 지정한 대상에서는 화면·OCR·AI 콘텐츠 분석을 중지하고, 타이머와 동의된 로컬 카메라 분석은 유지한다. 민감 화면 `PRIVACY_BLOCKED`에서는 카메라 분석도 중지한다.
- 카메라 분석은 명시적인 동의 후에만 시작한다.
- 원본 카메라 영상과 원본 화면을 기본 저장하지 않는다.
- 화면 부유형 마스코트 피드백은 집중 상태를 돕기 위한 안내이며 사용자를 모욕하거나 단정하지 않는다.
- 응원·경고 문구와 표시 모드는 사용자가 설정에서 변경할 수 있어야 한다.
- Extension에서 세션을 일시정지하면 Desktop도 동일한 세션 상태를 반영해야 한다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 만든다.

## 참고 문서

- Desktop: `apps/desktop/docs/ELECTRON_ARCHITECTURE.md`
- Desktop: `apps/desktop/docs/IPC_CONTRACT.md`
- Desktop: `apps/desktop/docs/MACOS_PERMISSION_FLOW.md`
- Desktop: `apps/desktop/docs/WINDOW_LIFECYCLE.md`
- 루트: `docs/ARCHITECTURE.md`
- 루트: `docs/API_CONTRACT.md`
- 루트: `docs/DATA_PRIVACY.md`
- 루트: `docs/WORKFLOW.md`
- Extension: `apps/extension/docs/README.md`

## 작업 종료 시 갱신

- 완료·진행 중·미착수 작업
- 변경된 결정과 관련 문서
- 발생한 오류 보고서
- 다음 작업과 선행 조건

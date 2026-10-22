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

- 서버 API 병합 뒤 실제 Google 계정 왕복, Keychain 저장·복구, 신규·기존 사용자 화면 진입의 수동 QA가 필요하다.
- 닉네임 중복 확인·저장, 온보딩 다음 단계, 홈 목표·기록 API는 별도 기능 작업이다. 현재 화면은 해당 값을 임의로 성공 처리하지 않는다.
- 확정된 Server 세션 상태와 타이머 필드를 Desktop 상태 모델에 반영해야 한다.
- 학습 상태에 따른 화면 부유형 마스코트·팻말·말풍선 표현을 확정해야 한다.
- 일회용 연결 코드 pairing과 상태 명령·Heartbeat 계약을 구현해야 하며 실시간 동기화 방식은 추가 확정이 필요하다.

### 아직 진행하지 않은 작업

- macOS 활성 앱 감지
- 화면 캡처 권한 및 캡처 처리
- 분석 제외 앱 처리
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

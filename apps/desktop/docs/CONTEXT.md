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

- Next.js 기본 화면을 FocusOn 기본 대시보드로 교체했다.
- Desktop 앱 기본 실행·빌드 구조를 확인했다.
- 공통 모노레포와 pnpm Workspace·Turborepo를 사용한다.
- Next.js App Router와 Tailwind CSS v4 기반 화면 구조를 확인했다.
- Main·Preload TypeScript Build, Next.js Static Export, `app://` Production Loading 방향을 확정했다.
- Single Instance, Main Window 닫기, Settings Route, Floating Window 위치·Drag 기준을 확정했다.
- IPC Channel·오류 형식과 macOS 권한 요청 UX를 확정했다.

### 진행 중인 작업

- 확정한 Electron Main·Preload·Renderer 구조를 실제 코드와 실행 Script로 구현해야 한다.
- 학습 세션 상태와 타이머 상태 모델을 확정해야 한다.
- 학습 상태에 따른 화면 부유형 마스코트·팻말·말풍선 표현을 확정해야 한다.
- Desktop과 Chrome Extension의 로그인·기기 연결 및 실시간 동기화 방식은 추가 확정이 필요하다.

### 아직 진행하지 않은 작업

- macOS 활성 앱 감지
- 화면 캡처 권한 및 캡처 처리
- 분석 제외 앱 처리
- MediaPipe 카메라 분석 연동
- Server API 연결
- 화면 부유형 마스코트 표시 및 사용자 문구 설정
- Google 로그인 후 Extension 연결

## 현재 기술

- 프레임워크: Next.js, React, Electron
- 언어: TypeScript
- 스타일: Tailwind CSS v4, PostCSS, `app/globals.css`
- 패키지 관리: pnpm Workspace, 작업 실행: Turborepo
- 실행: `pnpm dev:desktop` 또는 `pnpm turbo run dev --filter=@focuson/desktop`
- 빌드: `pnpm build:desktop` 또는 `pnpm turbo run build --filter=@focuson/desktop`
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
- 사용자가 분석 제외 앱이나 도메인으로 지정한 대상에서는 화면·OCR·AI·카메라 분석을 중지한다.
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

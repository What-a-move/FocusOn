# Desktop 작업 컨텍스트

> Desktop 담당자가 작업을 시작할 때 가장 먼저 읽는 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 담당 영역

- macOS Electron 데스크톱 앱
- React + TypeScript 화면
- 학습 세션과 타이머
- 활성 앱 확인과 화면 분석 연결
- 카메라 권한 및 MediaPipe 결과 연결

## 현재 상태

### 완료된 작업

- Next.js 기본 화면을 FocusOn 기본 대시보드로 교체했다.
- Desktop 앱 기본 실행·빌드 구조를 확인했다.
- 공통 모노레포와 pnpm Workspace를 사용한다.

### 진행 중인 작업

- Electron Main/Renderer 구조와 React 화면 연결을 구체화해야 한다.
- 학습 세션 상태와 타이머 상태 모델을 확정해야 한다.

### 아직 진행하지 않은 작업

- macOS 활성 앱 감지
- 화면 캡처 권한 및 캡처 처리
- 분석 제외 앱 처리
- MediaPipe 카메라 분석 연동
- Server API 연결

## 현재 기술

- 프레임워크: Next.js, React, Electron 예정
- 언어: TypeScript
- 스타일: CSS, Tailwind 도입 여부 검토
- 패키지 관리: pnpm Workspace
- 실행: `pnpm --filter @focuson/desktop dev`
- 빌드: `pnpm --filter @focuson/desktop build`

## 현재 데이터 흐름

```text
사용자
  → Desktop Renderer
  → Electron Main / macOS 기능
  → Server API 또는 AI 분석
  → 집중 상태·알림·리포트 화면
```

## 반드시 지켜야 하는 조건

- macOS 권한이 없는 상태에서도 앱이 비정상 종료되지 않아야 한다.
- 사용자가 분석 제외 앱으로 지정한 앱은 화면·카메라 분석을 중지한다.
- 카메라 분석은 명시적인 동의 후에만 시작한다.
- 원본 카메라 영상과 원본 화면을 기본 저장하지 않는다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 만든다.

## 참고 문서

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

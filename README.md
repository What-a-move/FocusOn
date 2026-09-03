# FocusOn

FocusOn은 사용자가 설정한 학습 목표를 기준으로 macOS 앱과 Chrome 브라우저의 학습 흐름을 기록하고, 집중 상태를 확인하도록 돕는 데스크톱 기반 학습 보조 서비스다.

## 핵심 구성

- `apps/desktop`: Electron + React 기반 macOS 데스크톱 앱
- `apps/extension`: Chrome Extension 기반 브라우저 분석 모듈
- `packages/shared-types`: Desktop·Extension·Server·AI가 공유할 타입
- `server`: Spring Boot 백엔드
- `ai`: AI 분석 서버 예정
- `docs`: 공통 개발 문서

## 현재 MVP 방향

- 학습 목표와 학습 시간 설정
- 데스크톱 앱의 타이머와 학습 세션 관리
- 현재 활성 앱과 Chrome 페이지 흐름 기록
- 목표와 화면·페이지의 기본 관련성 판단
- 목표 이탈 알림과 집중·비집중 시간 기록
- 분석 제외 앱·도메인 설정
- 사용자 선택에 따른 카메라 및 MediaPipe 분석
- 학습 종료 후 집중 상태와 활동 리포트 제공

## 시작하기

```bash
pnpm install
pnpm --filter @focuson/desktop dev
pnpm --filter @focuson/extension dev
./server/gradlew bootRun
```

## 빌드

```bash
pnpm --filter @focuson/desktop build
pnpm --filter @focuson/extension build
./server/gradlew build
```

Extension을 테스트할 때는 Build 결과물인 `apps/extension/dist/`를 Chrome의 `chrome://extensions`에서 개발자 모드로 불러온다.

## 작업 시작 순서

1. [공통 문서 안내](docs/README.md) 확인
2. [개발 워크플로우](docs/WORKFLOW.md) 확인
3. [Branch 전략](docs/BRANCH_STRATEGY.md)에 맞는 Branch 생성
4. `.github/ISSUE_TEMPLATE.md`로 Issue 작성
5. 작업 영역의 `CONTEXT.md`, `NEXT_TASK.md`, `DECISION_RECORD.md` 확인
6. 작업 완료 후 `.github/PULL_REQUEST_TEMPLATE.md`로 PR 작성

## 기술 문서

- [시스템 아키텍처](docs/ARCHITECTURE.md)
- [API 계약](docs/API_CONTRACT.md)
- [데이터 처리 기준](docs/DATA_PRIVACY.md)
- [개발 환경](docs/ENVIRONMENT.md)
- [QA 규칙](docs/QA_CONVENTION.md)

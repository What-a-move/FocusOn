# Frontend(apps) 문서 안내

이 폴더는 `apps/desktop`과 `apps/extension`에서 함께 사용하는 프론트엔드 개발 규칙을 관리한다.

Desktop과 Extension은 실행 환경이 다르지만 React, Next.js, TypeScript, Tailwind CSS, API 연결, 상태 관리 규칙은 최대한 공유한다. 플랫폼 전용 규칙과 기능 기록은 각 앱의 `docs/`에서 관리한다.

현재 공통 기술 방향은 클라이언트 공유 상태에 Zustand, Server 상태에 TanStack Query, HTTP 요청에 Axios를 사용하는 것으로 확정했다. 세 Package는 전체 문서 검토가 끝난 뒤 설치한다.

## 담당 범위

- 프론트엔드 담당 범위: `apps/`
- Desktop 전용 구현과 기록: `apps/desktop/`
- Chrome Extension 전용 구현과 기록: `apps/extension/`
- 여러 앱이 공유하는 타입: `packages/shared-types/`

## 작업 시작 순서

1. 루트 `docs/README.md`와 `docs/WORKFLOW.md`를 읽는다.
2. 이 문서와 작업에 필요한 공통 프론트엔드 규칙을 읽는다.
3. 작업 대상 앱의 `docs/CONTEXT.md`, `docs/NEXT_TASK.md`, `docs/DECISION_RECORD.md`를 읽는다.
4. 기능 문서와 Issue를 확인한 뒤 Issue Branch에서 작업한다.

## 문서 목록

| 문서 | 확인할 내용 |
| --- | --- |
| `FRONTEND_ARCHITECTURE.md` | 화면에서 API까지의 책임과 데이터 흐름 |
| `FOLDER_STRUCTURE.md` | 새 파일을 만들 위치 |
| `COMPONENT_CONVENTION.md` | React 컴포넌트 작성 규칙 |
| `TYPESCRIPT_CONVENTION.md` | 타입 작성과 공통 타입 분리 기준 |
| `STATE_MANAGEMENT.md` | 로컬·서버·플랫폼 상태 관리 기준 |
| `API_CLIENT_RULES.md` | Server API 호출과 인증·재시도 규칙 |
| `ERROR_HANDLING.md` | 오류 분류와 사용자 안내 기준 |
| `UI_STATE_GUIDE.md` | 로딩·빈 상태·오류·권한 거부 UI |
| `TESTING_STRATEGY.md` | 자동·수동 테스트 범위 |
| `CODE_REVIEW_CHECKLIST.md` | Commit·PR 전 최종 확인 항목 |
| `DESIGN_SYSTEM_RULES.md` | 공통 UI Token·Component·접근성 기준 |
| `AUTH_SESSION_FLOW.md` | 로그인·앱 연결·인증 만료 흐름 |
| `DEPENDENCY_POLICY.md` | Package 선택·설치·업데이트 규칙 |
| `LOGGING_POLICY.md` | Log Level과 개인정보 제외 기준 |
| `FRONTEND_SECURITY.md` | 인증·Electron·Extension 보안 원칙 |
| `FORM_VALIDATION.md` | 입력 검증과 오류 표시 기준 |
| `MOCKING_GUIDE.md` | 실제 기능을 대신하는 Mock과 Fixture 규칙 |

## 규칙 충돌 시 우선순위

1. 루트 개인정보·보안 기준
2. 확정된 API·공유 데이터 계약
3. 상태가 `확정`인 최신 결정 기록
4. 기능별 `*-PLAN.md`
5. 작업 대상 앱의 전용 문서
6. 이 폴더의 공통 프론트엔드 문서

`제안` 상태의 결정과 기능 PLAN은 개인정보·보안 기준 또는 확정된 공통 계약을 덮을 수 없다. 충돌을 발견하면 임의로 선택하지 않고 Issue에 남긴 뒤 관련 문서를 먼저 갱신한다.

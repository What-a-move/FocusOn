# FocusOn 작업 지침

## 백엔드 PR 리뷰 요청

사용자가 영역을 따로 지정하지 않고 `PR 확인해줘` 또는 비슷하게 요청하면 백엔드 PR 리뷰 요청으로 처리한다. `server/docs/PR_REVIEW_GUIDE.md`를 읽고 대상 PR을 찾은 뒤 검토와 GitHub 댓글까지 진행한다. 다른 영역을 명시한 요청에는 이 규칙을 적용하지 않는다.

## 새 채팅에서 프로젝트 학습 요청

사용자가 `FocusOn 학습해`처럼 짧게 요청하면 추가 설명을 요구하지 말고 이 저장소의 시작 문서를 읽는다. `docs/PROJECT_CONTEXT.md`에서 전체 흐름과 Notion 정본 링크를 확인하고, `docs/API_CONTRACT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_PRIVACY.md`, `docs/WORKFLOW.md` 및 각 영역의 `CONTEXT.md`, `NEXT_TASK.md`, `DECISION_RECORD.md`를 읽는다. 관련 Notion에 접근할 수 있으면 현재 내용을 확인한다. 확정 정책·제안·실제 구현 상태를 구분하고, 접근하지 못한 정본은 확인했다고 말하지 않는다. 학습 요청만 받은 경우 파일은 수정하지 않고 핵심 내용과 남은 충돌을 짧게 보고한다.

## 모든 작업 시작 전

1. 새 채팅을 포함해 모든 작업에서 `docs/PROJECT_CONTEXT.md`, `docs/README.md`, `docs/WORKFLOW.md`를 먼저 읽고 프로젝트 목적·문서 정본·현재 상태를 파악한다. `PROJECT_CONTEXT.md`는 시작용 요약이므로 세부 계약과 구현 여부는 해당 정본·코드에서 다시 확인한다.
2. API·DB·세션·분석 정책을 다루면 `docs/API_CONTRACT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_PRIVACY.md`와 관련 Notion 정본을 확인한다. Notion 최신 내용을 확인하지 못했으면 계약 변경을 추측해 확정하지 않는다.
3. Frontend 작업이면 `apps/docs/README.md`를 먼저 읽고, 작업 대상 앱의 `docs/README.md`, `docs/CONTEXT.md`, `docs/NEXT_TASK.md`, `docs/DECISION_RECORD.md`를 읽는다. Server·AI 작업은 해당 영역의 같은 문서를 읽는다.
4. Frontend의 새 기능(`type:feature`)이면 대상 앱의 `docs/templates/FEATURE_PLAN_TEMPLATE.md`를 기준으로 `docs/features/기능명-PLAN.md`를 먼저 작성한다. ERROR와 REPORT 문서는 미리 만들지 않는다.
5. Server·AI의 기능 문서 생성 시점과 형식은 해당 영역의 `docs/README.md`와 `docs/templates/`를 따른다. Frontend의 문서 생성 규칙을 Server·AI에 자동 적용하지 않는다.
6. Issue 생성 전 `.github/ISSUE_LABELS.md`를 읽고 새 기능이면 PLAN, 그 외 작업이면 Issue 내용을 기준으로 영역·유형·우선순위 Label을 선택한다.
7. Issue를 생성하고 번호를 확인한다. AI가 Issue를 생성할 때는 선택한 Label을 함께 적용하고, 생성 직후 실제 적용 여부를 확인한다.
8. `dev`를 최신화한 뒤 Issue 번호를 포함한 작업 Branch를 생성한다.
9. 기획서 또는 Issue의 완료 조건과 담당 범위를 확인한 뒤 개발을 시작한다.

## 작업 중

- 설계나 기술 선택이 바뀌면 `DECISION_RECORD.md`를 갱신한다.
- Frontend 기능 개발 중 실제 오류가 발생하면 `DEVELOPMENT_ERROR_TEMPLATE.md` 형식으로 오류마다 `docs/features/기능명-오류명-ERROR.md`를 생성한다. 오류가 없으면 ERROR 문서를 만들지 않는다.
- 기능 범위를 임의로 넓히지 않고, 추가 작업은 Issue로 분리한다.
- Issue Branch의 PR 대상은 `dev`로 설정하고, `dev`에서 `main`으로 가는 PR은 최종 통합 시에만 생성한다.
- 카메라·화면·페이지 데이터는 루트 `docs/DATA_PRIVACY.md` 기준을 따른다.
- 제품 범위나 확정 계약이 바뀌면 `docs/PROJECT_CONTEXT.md`의 요약도 같은 작업에서 갱신한다. 단순 진행 상황은 각 영역의 `CONTEXT.md`와 `NEXT_TASK.md`에 기록한다.

## 작업 종료 전

1. Frontend의 새 기능을 완료한 경우에만 `RESULT_REPORT_TEMPLATE.md` 형식으로 `docs/features/기능명-REPORT.md`를 작성한다. 단순 버그 수정에는 REPORT를 작성하지 않는다.
2. Frontend 기능 결과 리포트에 테스트 결과와 남은 제한사항을 기록한다.
3. `CONTEXT.md`의 현재 상태를 갱신한다.
4. `NEXT_TASK.md`에 다음 작업을 남긴다.
5. 관련 문서와 PR을 연결한다.

## 영역별 문서 위치

- Frontend 공통 작업과 담당 범위: `apps/docs/`
- Desktop 전용 작업 기록: `apps/desktop/docs/`
- Chrome Extension 전용 작업 기록: `apps/extension/docs/`
- Server 작업: `server/docs/`
- AI 작업: `AI/docs/`

AI 폴더는 `AI/docs/`를 기준으로 `CONTEXT.md`, `NEXT_TASK.md`, `DECISION_RECORD.md`, `templates/`, `features/` 구조를 관리한다.

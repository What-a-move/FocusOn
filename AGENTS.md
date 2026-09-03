# FocusOn 작업 지침

## 모든 작업 시작 전

1. 루트 `docs/README.md`와 `docs/WORKFLOW.md`를 확인한다.
2. 작업 영역의 `docs/README.md`, `docs/CONTEXT.md`, `docs/NEXT_TASK.md`, `docs/DECISION_RECORD.md`를 읽는다.
3. 기능 작업이면 해당 영역의 `docs/templates/FEATURE_PLAN_TEMPLATE.md`를 기준으로 `docs/features/기능명-PLAN.md`를 먼저 작성한다.
4. 같은 기능의 `기능명-ERROR.md`와 `기능명-REPORT.md`도 함께 생성한다.
5. 기획서의 완료 조건과 담당 범위를 확인한 뒤 개발을 시작한다.
6. `dev`에서 Issue 번호를 포함한 작업 Branch를 생성한다.

## 작업 중

- 설계나 기술 선택이 바뀌면 `DECISION_RECORD.md`를 갱신한다.
- 오류가 발생하면 `DEVELOPMENT_ERROR_TEMPLATE.md` 형식으로 `docs/features/기능명-ERROR.md`에 기록한다.
- 기능 범위를 임의로 넓히지 않고, 추가 작업은 Issue로 분리한다.
- Issue Branch의 PR 대상은 `dev`로 설정하고, `dev`에서 `main`으로 가는 PR은 최종 통합 시에만 생성한다.
- 카메라·화면·페이지 데이터는 루트 `docs/DATA_PRIVACY.md` 기준을 따른다.

## 작업 종료 전

1. `RESULT_REPORT_TEMPLATE.md` 형식으로 `docs/features/기능명-REPORT.md`를 작성한다.
2. 테스트 결과와 남은 제한사항을 결과 리포트에 기록한다.
3. `CONTEXT.md`의 현재 상태를 갱신한다.
4. `NEXT_TASK.md`에 다음 작업을 남긴다.
5. 관련 문서와 PR을 연결한다.

## 영역별 문서 위치

- Desktop 작업: `apps/desktop/docs/`
- Chrome Extension 작업: `apps/extension/docs/`
- Server 작업: `server/docs/`
- AI 작업: `ai/docs/`

AI 폴더가 생성되면 담당자의 기술과 작업 방식에 맞춰 `CONTEXT.md`, `NEXT_TASK.md`, `DECISION_RECORD.md`, `templates/`, `features/` 구조를 구성한다.

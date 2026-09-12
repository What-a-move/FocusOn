# FocusOn 개발 워크플로우

모든 작업은 작은 단위로 나누고, 기록을 남긴 상태에서 진행한다.

## 전체 흐름

```text
Frontend 새 기능이면 기획 문서 작성
  ↓
결정 기록
  ↓
Issue 생성
  ↓
`dev` 최신화 및 Issue별 Branch 생성
  ↓
개발 및 Frontend 오류 발생 시 오류별 기록
  ↓
테스트
  ↓
새 기능이면 결과 리포트 작성
  ↓
Pull Request
  ↓
Review 및 수정
  ↓
Merge
```

## 작업 시작 전

- 담당 영역의 `CONTEXT.md`를 읽는다.
- `NEXT_TASK.md`에서 진행 중인 작업과 다음 작업을 확인한다.
- 관련 `DECISION_RECORD.md`와 기능 기획서를 확인한다.
- 모호하거나 충돌하는 요구사항은 개발 전에 Issue에 남긴다.

## Issue 작성

Issue에는 작업 목적, 작업 범위, 완료 조건, 예상 변경 파일을 작성한다. 하나의 Issue에는 하나의 기능 또는 하나의 명확한 수정 단위만 포함한다.

Issue 생성 전 `.github/ISSUE_LABELS.md`를 확인한다. 새 기능은 PLAN, 그 외 작업은 Issue 내용을 기준으로 영역·유형·우선순위 Label을 선택한다. AI가 Issue를 생성하는 경우 선택한 Label을 생성 요청에 함께 포함하고, 생성 직후 실제 적용 여부까지 확인한다. 누락된 Label이 있으면 Issue 작업을 시작하기 전에 추가한다.

## 개발 중

- Issue에 연결된 Branch에서만 작업한다.
- 기능 범위를 임의로 넓히지 않는다.
- Frontend 기능 개발 중 오류가 발생하면 대상 앱의 `docs/features/기능명-오류명-ERROR.md`에 오류별 재현 방법과 원인을 기록한다.
- Frontend 기능에서 오류가 발생하지 않으면 ERROR 문서를 생성하지 않는다.
- Server·AI의 오류 문서 생성 시점과 형식은 각 영역의 문서 규칙을 따른다.
- 화면, API, 데이터 구조가 변경되면 관련 문서를 함께 갱신한다.

## 작업 완료 전

- 정상 흐름을 확인한다.
- 예외 흐름과 권한 거부 상황을 확인한다.
- 다른 영역과의 연결이 깨지지 않았는지 확인한다.
- Frontend의 새 기능을 완료하면 `기능명-REPORT.md`에 변경 내용과 테스트 결과를 기록한다.
- Frontend의 단순 버그 수정은 별도 REPORT 없이 Issue, 오류 문서, PR에 결과를 기록한다.
- `NEXT_TASK.md`의 상태와 다음 작업을 갱신한다.

## Pull Request

Issue Branch에서 작업한 PR의 대상은 `dev`로 설정한다. PR에는 작업 목적, 주요 변경 사항, 테스트 여부, 관련 문서를 작성한다. UI가 변경되면 실행 화면 또는 스크린샷을 첨부한다.

`dev`에 통합된 기능을 최종 검수한 뒤 `dev → main` PR을 생성한다.

## Merge 후

- Issue Branch를 `dev`에 Merge한 뒤 관련 Issue를 종료한다.
- `dev`를 최종 검수한 뒤 `main`에 Merge한다.
- 결정이 확정되었다면 `DECISION_RECORD.md`를 갱신한다.
- 다른 담당자가 이어서 작업할 수 있도록 `CONTEXT.md`와 `NEXT_TASK.md`를 갱신한다.

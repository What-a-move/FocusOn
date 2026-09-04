# 문서 작성 및 관리 규칙

## 문서 위치

| 문서 종류 | 위치 | 목적 |
| --- | --- | --- |
| 공통 개발 규칙 | `docs/` | 모든 영역이 공유하는 기준 |
| Desktop 문서 | `apps/desktop/docs/` | Electron·데스크톱 UI·macOS 연동 |
| Extension 문서 | `apps/extension/docs/` | Chrome Extension·브라우저 분석 |
| Server 문서 | `server/docs/` | Spring Boot·API·DB |
| AI 문서 | `AI/docs/` | 분석 모델·추론·AI API |

AI 폴더는 현재 `AI/`로 구성하며 같은 규칙을 적용한다.

각 담당 영역의 템플릿은 해당 영역의 `docs/templates/` 안에서 관리한다. 영역별 기술과 작업 흐름이 다를 수 있으므로 루트에 공통 기능 템플릿을 두지 않는다.

## 작업 기록 문서

각 담당 영역의 `docs/`에는 다음 세 파일을 유지한다.

- `CONTEXT.md`: 현재 구조, 완료 기능, 주의사항
- `NEXT_TASK.md`: 다음 작업, 진행 상태, 우선순위
- `DECISION_RECORD.md`: 합의된 기술·정책·설계 결정

작업 시작 시 세 파일을 읽고, 작업 종료 시 변경된 내용을 갱신한다.

## 기능 문서

기능 하나당 담당 영역의 `docs/features/` 안에 다음 세 문서를 만든다.

- `기능명-PLAN.md`: 기능 목적, 사용자 흐름, 완료 조건
- `기능명-ERROR.md`: 개발 중 오류와 해결 과정
- `기능명-REPORT.md`: 구현 결과, 테스트 결과, 남은 작업

기능명은 소문자 kebab-case를 사용한다. 예: `study-session-timer-PLAN.md`

## 갱신 시점

- 설계가 바뀌면 즉시 결정 기록을 갱신한다.
- API나 데이터 구조가 바뀌면 공통 문서를 갱신한다.
- 작업 완료 시 결과 리포트를 작성한다.
- 문서에 아직 확정되지 않은 내용은 `검토 필요`라고 표시한다.

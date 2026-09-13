# 문서 작성 및 관리 규칙

## 문서 위치

| 문서 종류 | 위치 | 목적 |
| --- | --- | --- |
| 공통 개발 규칙 | `docs/` | 모든 영역이 공유하는 기준 |
| Frontend 공통 문서 | `apps/docs/` | `apps/` 담당 범위와 공통 구현 규칙 |
| Desktop 문서 | `apps/desktop/docs/` | Electron·데스크톱 UI·macOS 연동 |
| Extension 문서 | `apps/extension/docs/` | Chrome Extension·브라우저 분석 |
| Server 문서 | `server/docs/` | Spring Boot·API·DB |
| AI 문서 | `AI/docs/` | 분석 모델·추론·AI API |

AI 폴더는 현재 `AI/`로 구성하며 같은 규칙을 적용한다.

Frontend 담당 범위는 `apps/` 전체다. 공통 아키텍처·컴포넌트·타입·상태·API·오류·테스트 규칙은 `apps/docs/`에서 관리하고, 플랫폼별 CONTEXT·NEXT_TASK·DECISION_RECORD·기능 템플릿은 `apps/desktop/docs/`와 `apps/extension/docs/`에서 관리한다.

Server와 AI의 템플릿은 해당 영역의 `docs/templates/` 안에서 관리한다. 영역별 기술과 작업 흐름이 다를 수 있으므로 루트에 공통 기능 템플릿을 두지 않는다.

## 작업 기록 문서

각 담당 영역의 `docs/`에는 다음 세 파일을 유지한다.

- `CONTEXT.md`: 현재 구조, 완료 기능, 주의사항
- `NEXT_TASK.md`: 다음 작업, 진행 상태, 우선순위
- `DECISION_RECORD.md`: 합의된 기술·정책·설계 결정

작업 시작 시 세 파일을 읽고, 작업 종료 시 변경된 내용을 갱신한다.

## Frontend 기능 문서

Frontend의 새 기능 하나당 대상 앱의 `docs/features/` 안에서 다음 문서를 필요할 때 생성한다.

- `기능명-PLAN.md`: 새 기능 개발 전에 생성하며 기능 목적, 사용자 흐름, 완료 조건을 기록한다.
- `기능명-REPORT.md`: 해당 기능이 완료된 뒤 한 번 생성하며 구현 결과, 테스트 결과, 남은 작업을 기록한다.
- `기능명-오류명-ERROR.md`: 실제 오류가 발생했을 때 오류마다 생성해 재현과 해결 과정을 기록한다.

기능명과 오류명은 소문자 kebab-case를 사용한다.

```text
study-session-timer-PLAN.md
study-session-timer-permission-denied-ERROR.md
study-session-timer-sync-timeout-ERROR.md
study-session-timer-REPORT.md
```

- 오류가 없으면 ERROR 문서를 만들지 않는다.
- 단순 버그 수정은 REPORT를 만들지 않고 Issue, 오류 문서, PR에 결과를 기록한다.
- 문서·스타일·설정처럼 새 기능이 아닌 작업은 PLAN과 REPORT를 필수로 만들지 않는다.
- Server·AI의 기능 문서 생성 시점과 파일 형식은 각 영역의 `docs/README.md`와 `docs/templates/`를 따른다.

## 갱신 시점

- 설계가 바뀌면 즉시 결정 기록을 갱신한다.
- API나 데이터 구조가 바뀌면 공통 문서를 갱신한다.
- Frontend 새 기능 완료 시 결과 리포트를 작성한다.
- 문서에 아직 확정되지 않은 내용은 `검토 필요`라고 표시한다.

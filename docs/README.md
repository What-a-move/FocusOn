# FocusOn 문서 안내

FocusOn은 macOS 데스크톱 앱과 Chrome Extension을 연결해 사용자의 학습 흐름을 기록하고, 화면 분석과 선택적 카메라 분석을 바탕으로 집중 상태를 확인하는 서비스다.

## 문서 구조

```text
docs/
├── README.md
├── WORKFLOW.md
├── DOCUMENTATION_RULES.md
├── ARCHITECTURE.md
├── API_CONTRACT.md
├── DATA_PRIVACY.md
├── BRANCH_STRATEGY.md
├── COMMIT_CONVENTION.md
├── SENTENCE_WRITING.md
├── QA_CONVENTION.md
├── ENVIRONMENT.md
└── PROJECT_SCHEDULE.md
```

## 먼저 읽을 문서

1. `WORKFLOW.md` - 작업 시작부터 PR까지의 전체 흐름
2. `BRANCH_STRATEGY.md` - Branch 이름과 작업 분리 규칙
3. `COMMIT_CONVENTION.md` - Commit 메시지 규칙
4. `DOCUMENTATION_RULES.md` - 문서 작성 위치와 갱신 시점
5. 담당 영역의 `docs/README.md`, `docs/CONTEXT.md`, `docs/NEXT_TASK.md`, `docs/DECISION_RECORD.md`

## 문서 운영 원칙

- 기능을 시작하기 전에 기획 문서를 먼저 작성한다.
- 작업 중 결정이 바뀌면 결정 기록을 바로 갱신한다.
- 오류는 숨기지 않고 재현 방법과 해결 결과를 남긴다.
- 작업이 끝나면 결과 리포트와 관련 문서를 함께 갱신한다.
- 문서는 구현 상태와 일치해야 하며, 확정되지 않은 내용은 `검토 필요`로 표시한다.
- 기능별 템플릿은 각 담당 영역의 `docs/templates/`에서 관리한다.

## 문서 우선순위

기능 기획서와 결정 기록이 가장 최신의 합의 내용을 담는다. 코드와 문서가 다르면 담당자는 Issue에서 차이를 알리고, 합의 후 문서를 먼저 갱신한다.

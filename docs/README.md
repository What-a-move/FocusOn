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
└── ENVIRONMENT.md
```

## 먼저 읽을 문서

1. `WORKFLOW.md` - 작업 시작부터 PR까지의 전체 흐름
2. `BRANCH_STRATEGY.md` - Branch 이름과 작업 분리 규칙
3. `COMMIT_CONVENTION.md` - Commit 메시지 규칙
4. `DOCUMENTATION_RULES.md` - 문서 작성 위치와 갱신 시점
5. Frontend 작업이면 `apps/docs/README.md`와 작업 대상 앱의 문서, Server·AI 작업이면 해당 영역의 `docs/README.md`, `docs/CONTEXT.md`, `docs/NEXT_TASK.md`, `docs/DECISION_RECORD.md`

## 문서 운영 원칙

- Frontend의 새 기능을 시작하기 전에 대상 앱의 기획 문서를 먼저 작성한다.
- 작업 중 결정이 바뀌면 결정 기록을 바로 갱신한다.
- Frontend 기능 개발 중 실제 오류가 발생하면 오류별 ERROR 문서에 재현 방법과 해결 결과를 남긴다.
- Frontend의 새 기능을 완료하면 결과 리포트와 관련 문서를 함께 갱신한다.
- 문서는 구현 상태와 일치해야 하며, 확정되지 않은 내용은 `검토 필요`로 표시한다.
- Frontend 공통 규칙은 `apps/docs/`, 플랫폼별 기능 기록과 템플릿은 각 앱의 `docs/`에서 관리한다.
- Server·AI 기능별 문서 생성 시점과 템플릿은 각 담당 영역의 규칙을 유지하며, Frontend 규칙을 자동 적용하지 않는다.

## 문서 우선순위

개인정보·보안 기준과 확정된 API·공유 데이터 계약은 기능 기획서가 임의로 덮을 수 없는 공통 기준이다. 같은 기술 주제에서는 상태가 `확정`인 최신 결정 기록을 기능 기획서보다 우선하며, `제안` 상태의 결정은 확정 규칙을 덮지 않는다. 코드와 문서가 다르면 담당자는 Issue에서 차이를 알리고, 합의 후 문서를 먼저 갱신한다.

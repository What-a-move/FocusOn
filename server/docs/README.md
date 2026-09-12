# Backend 문서 안내

FocusOn Spring Boot 백엔드의 API, 데이터, 인증, 연동 작업을 기록하는 문서 공간이다.

## 작업 시작 순서

1. `CONTEXT.md`에서 현재 Backend 상태를 확인한다.
2. `NEXT_TASK.md`에서 이번 작업 범위를 확인한다.
3. `DECISION_RECORD.md`에서 기존 API·DB 결정을 확인한다.
4. 기능 작업이면 `templates/FEATURE_PLAN_TEMPLATE.md`를 기준으로 기획서를 먼저 작성한다.
5. 오류는 `templates/DEVELOPMENT_ERROR_TEMPLATE.md` 형식으로 기록한다.
6. 테스트가 끝나면 `templates/RESULT_REPORT_TEMPLATE.md`로 결과를 작성한다.

## 문서 구조

```text
server/docs/
├── README.md
├── CONTEXT.md
├── NEXT_TASK.md
├── DECISION_RECORD.md
├── DEVELOPMENT_RULES.md
├── rules/
│   ├── api-design.md
│   ├── branch-workflow.md
│   ├── progress-tracking.md
│   ├── code-style.md
│   ├── test-convention.md
│   ├── migration-convention.md
│   ├── code-review-isolation.md
│   ├── code-review-template.md
│   └── notion-api-spec.md
├── templates/
│   ├── FEATURE_PLAN_TEMPLATE.md
│   ├── DEVELOPMENT_ERROR_TEMPLATE.md
│   └── RESULT_REPORT_TEMPLATE.md
└── features/
    └── 기능명-PLAN.md / 기능명-ERROR.md / 기능명-REPORT.md
```

## Backend 전용 확인 항목

- API 요청·응답 형식과 HTTP 상태 코드
- 인증·인가와 사용자 식별
- Entity·DTO·Repository·Service·Controller 책임
- 학습 목표·세션·이벤트·분석 결과 저장
- Desktop·Extension·AI 연동
- 예외 처리·검증·로그·테스트

## 세부 규칙 (`rules/`)

작업 단계별 상세 규칙은 아래 문서에 나눠 두었다. `CONTEXT.md`/`NEXT_TASK.md`/`DECISION_RECORD.md`를 먼저 읽은 뒤, 해당 단계에서 필요한 규칙만 열어본다.

- [API 설계 원칙](rules/api-design.md)
- [기능 구현 워크플로우 상세](rules/branch-workflow.md) (루트 `docs/WORKFLOW.md`, `docs/BRANCH_STRATEGY.md`를 Backend 작업에 맞춰 단계별로 풀어쓴 것)
- [진행 상태 추적](rules/progress-tracking.md)
- [코드 스타일 (Checkstyle/Google Java Style)](rules/code-style.md)
- [테스트 코드 규칙](rules/test-convention.md)
- [Flyway 마이그레이션 규칙](rules/migration-convention.md)
- [코드 리뷰 컨텍스트 격리](rules/code-review-isolation.md)
- [코드 리뷰 결과 문서 템플릿](rules/code-review-template.md)
- [Notion API 명세서 작성 규칙](rules/notion-api-spec.md)

커밋·Issue·PR·문장 작성 규칙은 루트 공통 문서(`docs/COMMIT_CONVENTION.md`, `.github/ISSUE_TEMPLATE.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `docs/SENTENCE_WRITING.md`)를 그대로 따른다 — Backend 전용 버전을 별도로 만들지 않는다(팀 전체가 같은 규칙을 쓴다).

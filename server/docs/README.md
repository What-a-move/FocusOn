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

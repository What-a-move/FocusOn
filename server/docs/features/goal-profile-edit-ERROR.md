# 개발 오류 보고서 — 목표 범위 수정·다시 만들기 (#314)

## 1. 할 일 상태만 바꾸는 PATCH가 동시 수정·삭제를 되돌릴 수 있음

- 발견: CodeRabbit 리뷰(PR #336, 2026-11-25). 원인 코드는 #313에서 들어왔다.
- 증상: `{"todoStatus":"DONE"}` 요청은 사용자 잠금 없이 목표를 읽는다. 그 뒤 다른 요청의 범위·문구 수정이나 삭제가 먼저 커밋되면, 이 요청이 커밋할 때 Hibernate가 읽었던 옛 값 전체를 다시 써서 그 변경이 사라진다(`GoalEntity`에 `@Version`·`@DynamicUpdate` 없음).
- 조치: 상태만 바꾸는 경로도 다른 수정·삭제와 같은 사용자 잠금을 먼저 잡는다.
- 검증: `GoalUpdateDeleteApiTests.postgresTodoOnlyUpdateWaitsForUserLock`(PostgreSQL, 수정 전 실패·수정 후 통과).

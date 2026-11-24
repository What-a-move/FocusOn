# 개발 오류 보고서 — 목표 범위 수정·다시 만들기 (#314)

## 1. 할 일 상태만 바꾸는 PATCH가 동시 수정·삭제를 되돌릴 수 있음

- 발견: CodeRabbit 리뷰(PR #336, 2026-11-25). 원인 코드는 #313에서 들어왔다.
- 증상: `{"todoStatus":"DONE"}` 요청은 사용자 잠금 없이 목표를 읽는다. 그 뒤 다른 요청의 범위·문구 수정이나 삭제가 먼저 커밋되면, 이 요청이 커밋할 때 Hibernate가 읽었던 옛 값 전체를 다시 써서 그 변경이 사라진다(`GoalEntity`에 `@Version`·`@DynamicUpdate` 없음).
- 조치: 상태만 바꾸는 경로도 다른 수정·삭제와 같은 사용자 잠금을 먼저 잡는다.
- 검증: `GoalUpdateDeleteApiTests.postgresTodoOnlyUpdateWaitsForUserLock`(PostgreSQL, 수정 전 실패·수정 후 통과).

## 2. 다시 만들기가 AI를 기다리는 사이 사용자가 고친 범위를 덮어씀

- 발견: PR 전 결함 점검(2026-11-25).
- 증상: 「다시 만들기」 요청이 AI 결과를 기다리는 동안 같은 목표의 범위를 PATCH로 고치면, 이후 AI 결과가 문구만 같은지 보고 그 수정을 덮어썼다.
- 조치: 처음 읽은 문구·범위를 기억해 두고, 저장 직전 둘 다 그대로일 때만 새 범위로 바꾼다. 바뀌었으면 현재 목표를 돌려준다.
- 검증: `GoalProfileEditApiTests.regenerateDoesNotOverwriteEditMadeDuringAiCall`.

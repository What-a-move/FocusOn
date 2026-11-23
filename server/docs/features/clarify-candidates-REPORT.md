# 결과 리포트 — 목표 다듬기 NEEDS_SELECTION 폐기와 goalCandidates (#315)

## 변경 요약

- `ClarifyStatus`에서 `NEEDS_SELECTION` 삭제. 저장된 옛 상태는 `CLEAR`로 읽는다(`@JsonCreator`).
- `GoalAiResponseMapper`: AI `NEEDS_SELECTION` → `CLEAR` + `goalCandidates`, 질문 없음, 후보 2개 미만은 오류.
- `GoalService`: `AI_CONFIRMED` 저장 시 후보 문구도 허용(옛 `NEEDS_SELECTION` 상태는 후보만). 후보 저장은 다듬기 세분화를 붙이지 않고 따로 세분화(#314).
- `ClarifyGoalResponse`: 후보가 있는 `CLEAR`는 질문을 비운다(저장된 옛 응답 재전송 포함).
- `docs/API_CONTRACT.md` 목표 다듬기 절 갱신.

## 테스트

- `GoalAiResponseMapperTests`: 후보 → CLEAR·질문 없음, 후보 1개 오류, NEEDS_SUGGESTION·CLEAR 그대로, 옛 상태 읽기.
- `GoalProfileExpandTests.candidateFromClearResponseCanBeSavedAndIsExpanded`: 다듬기 응답, 후보 밖 문구 400, 후보 저장 201 + 그 문구로 세분화.
- `GoalProfileExpandTests.legacySelectionStateReplaysAsClearAndAllowsOnlyCandidates`: 옛 상태 재전송은 질문 없는 CLEAR, 후보 아닌 문구("null") 400, 후보 201.
- H2 전체 437개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과.
- 임시 PostgreSQL 17(Flyway V29 + validate)에서 #313~#315 쌓은 상태로 목표 패키지 + CI 범위 268개 통과(1개 생략). `goals.todo_status` 기본값 `OPEN` 확인.

## 남은 제한

- Desktop의 다듬기 파서(`apps/desktop/electron/shared/goal-contract.cts`)는 지금도 `clarityStatus`가 아니라 옛 계약의 `decision`(`CLEAR`·`AMBIGUOUS`·`MULTI_GOAL`·`UNKNOWN_TERM`)을 읽어 서버 응답을 해석하지 못한다. 이 PR 전부터 있던 불일치로 Frontend 담당 확인이 필요하다. Desktop을 맞출 때 후보 선택은 `CLEAR` + `goalCandidates`로 구현해야 한다.
- AI는 계속 `NEEDS_SELECTION`을 보내도 된다(서버가 바꿈).

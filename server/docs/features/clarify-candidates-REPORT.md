# 결과 리포트 — 목표 다듬기 NEEDS_SELECTION 폐기와 goalCandidates (#315)

## 변경 요약

- `ClarifyStatus`에서 `NEEDS_SELECTION` 삭제. 저장된 옛 상태는 `CLEAR`로 읽는다(`@JsonCreator`).
- `GoalAiResponseMapper`: AI `NEEDS_SELECTION` → `CLEAR` + `goalCandidates`, 질문 없음, 후보 2개 미만은 오류.
- `GoalService`: `AI_CONFIRMED` 저장 시 후보 문구도 허용. 후보 저장은 다듬기 세분화를 붙이지 않고 따로 세분화(#314).
- `docs/API_CONTRACT.md` 목표 다듬기 절 갱신.

## 테스트

- `GoalAiResponseMapperTests`: 후보 → CLEAR·질문 없음, 후보 1개 오류, NEEDS_SUGGESTION·CLEAR 그대로, 옛 상태 읽기.
- `GoalProfileExpandTests.candidateFromClearResponseCanBeSavedAndIsExpanded`: 다듬기 응답, 후보 밖 문구 400, 후보 저장 201 + 그 문구로 세분화.
- H2 전체 435개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과.
- 임시 PostgreSQL 17(Flyway V29 + validate)에서 #313~#315 쌓은 상태로 목표 패키지 + CI 범위 266개 통과(1개 생략). `goals.todo_status` 기본값 `OPEN` 확인.

## 남은 제한

- Desktop은 `NEEDS_SELECTION` 화면 대신 `CLEAR`의 `goalCandidates`를 보여 줘야 한다. 바꾸기 전에는 후보가 있는 CLEAR를 일반 CLEAR로 보고 `normalizedGoalText`(null일 수 있음)를 쓰게 되므로, 배포 순서를 Desktop과 맞춰야 한다.
- AI는 계속 `NEEDS_SELECTION`을 보내도 된다(서버가 바꿈).

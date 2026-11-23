# 결과 리포트 — 할 일 목록 조회와 완료 상태 (#313)

## 변경 요약

- `GoalEntity.TodoStatus`(`OPEN`/`DONE`)와 `todo_status` 컬럼(V29), `markTodo`.
- `GET /api/v1/goals`: 상태 필터·cursor·limit 목록(`GoalListResponse`).
- `PATCH /api/v1/goals/{id}`: `todoStatus` 수용. 상태만 바꾸는 요청은 활성 세션 참조 중에도 허용.
- 목표 응답에 `todoStatus`. `docs/API_CONTRACT.md` 목표 절 갱신.

## 테스트

- `GoalTodoApiTests`: 최신순·cursor 2페이지, OPEN/DONE 필터, 다른 사용자·삭제 목표 제외, 잘못된 limit·cursor·상태 400, 미인증 401 / 활성 세션 중 상태만 변경 200, 문구+상태 409(상태도 안 바뀜), 빈 본문 409, 잘못된 상태 400.
- 기존 목표 테스트 그대로 통과. H2 전체 425개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과.
- PostgreSQL 확인은 #313~#315를 쌓은 브랜치에서 함께 한다(#315 리포트).

## 남은 제한

- Notion API 명세(연결된 사본은 11-18 기준)에 `GET /goals` 응답 형식이 보이지 않아, 기존 학습 노트 목록(`items`·`nextCursor`)과 같은 형식으로 정했다. 문서 오너 확인 필요.
- 머지 순서: #319(V28) → 이 브랜치(V29).

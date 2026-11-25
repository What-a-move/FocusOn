# Desktop 기능 기획서

## 기본 정보

- 기능명: 세션 끝 「다 했어요」
- 대상 앱: Desktop
- 디자인 원본: `FocusOn-design/todo-goals.html?view=done`
- 작성일: 2026-11-25
- 작성자: jae1
- 서버 의존: `PATCH /api/v1/goals/{goalId}` `{ todoStatus: DONE }` (#322, 세션 중에도 가능)

## 기능 목적

- 집중이 끝나면 이번 할 일 중 끝낸 것을 표시해, 다음 할 일 고르기 목록에서 빠지게 한다.

## 사용자 흐름

1. 집중 종료(직접·시간 종료·자동 종료) → 할 일이 있으면 `/todo-done/?session=ID`, 없으면 바로 결과
2. 할 일마다 `다 했어요` 토글 → `결과 보기`
3. 고른 할 일을 DONE으로 저장한 뒤 `/session-result/`

## Desktop 상세 동작

- 끝난 세션의 할 일은 `sessionStorage` `focuson.endedGoals` `{ sessionId, goals }`로 넘긴다. 세션 id가 다르거나 비어 있으면 결과로 보낸다.
- 저장 실패: `‘다 했어요’를 저장하지 못했어요. / 할 일 목록에는 그대로 남아 있어요.` + `다시 시도`, 버튼은 `그냥 결과 보기`. 이미 지운 할 일(`GOAL_NOT_FOUND`)은 성공으로 본다.
- 시안의 할 일별 집중 시간 막대는 서버에 할 일별 시간이 없어 넣지 않았다.
- 시안의 순서(종료 → 회고 → 다 했어요)는 회고 저장(서버 #318) 전이라 종료 → 다 했어요 → 결과다.

## 데이터 및 IPC/API

- `goal:complete(goalId)` → `PATCH /api/v1/goals/{goalId}` `{ todoStatus: "DONE" }` (200)

## 완료 조건

- [x] 화면·토글·저장 실패·다시 시도
- [x] 종료 → 다 했어요 → 결과 이동
- [ ] 실제 서버로 DONE 저장 확인

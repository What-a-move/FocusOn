# Backend 기능 기획서 — 할 일 목록 조회와 완료 상태(todoStatus)

## 기본 정보

- 기능명: 할 일 목록과 할 일 상태
- 기능 ID: goal-todo-list
- 작성자: 박진욱
- 작성일: 2026-11-23
- 우선순위: MVP - 핵심
- 관련 Issue: #313
- 예정 Branch: `feat/313-server-goal-todo-list`
- 관련 Client·AI 문서: 재설계 1차 정리(DR-024~030), ERD 8.1

## 기능 목적

- 해결할 문제: 목표를 「할 일 목록」으로 쓰려면 목록 조회와 사용자가 표시하는 완료 상태가 필요하다. 지금은 단건 조회만 있다.
- Backend가 담당할 역할: 본인 할 일을 상태별로 페이지 조회하고, 세션 중에도 완료 표시를 받는다.

## API 정의

- `GET /api/v1/goals?todoStatus=&cursor=&limit=`: `{items:[{goalId,goalText,confirmationSource,todoStatus}], nextCursor}`. 최신순, limit 기본 20·1~50.
- `PATCH /api/v1/goals/{goalId}`: `todoStatus` 추가. 상태만 바꾸면 활성 세션 참조 중에도 허용.
- 목표 응답 전체에 `todoStatus` 추가.
- Failure: 잘못된 `todoStatus`·`limit`·`cursor`는 400 `VALIDATION_ERROR`. 그 밖은 기존과 같다.

## 데이터 설계

- V29: `goals.todo_status` VARCHAR(8) NOT NULL DEFAULT 'OPEN', CHECK `OPEN`/`DONE`(`NOT VALID`), 목록용 부분 인덱스 `(user_id, todo_status, id DESC) WHERE deleted_at IS NULL`.
- V28은 #319가 쓰므로 #319 머지 뒤 머지한다(Flyway 순서).

## 처리 흐름

1. 목록: 본인·삭제 안 됨·`ACTIVE` 목표를 `id < cursor` 조건으로 limit+1개 읽어 다음 커서를 정한다.
2. 수정: `goalText`가 없고 `todoStatus`만 있으면 소유 확인 후 상태만 바꾼다(활성 세션 검사 없음). 그 밖은 기존 문구 수정 규칙 뒤 상태도 반영한다.

## 범위 밖

- `goalProfile` 응답·수정, 문구 수정 시 재확장: #314.
- 활성 세션 판정을 `session_goals` 기준으로 바꾸기: #316.

## 완료 조건

- [x] 목록 조회가 상태 필터·cursor로 동작하고 다른 사용자 목표·삭제 목표는 나오지 않는다.
- [x] 활성 세션 참조 중 `todoStatus`만 변경 가능, 다른 필드 변경은 409.
- [x] 마이그레이션·테스트·문서 갱신.

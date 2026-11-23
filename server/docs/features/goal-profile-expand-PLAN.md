# Backend 기능 기획서 — 모든 목표 세분화와 goalProfile 응답·수정

## 기본 정보

- 기능명: 모든 목표 세분화(GoalProfile)와 응답·수정
- 기능 ID: goal-profile-expand
- 작성자: 박진욱
- 작성일: 2026-11-23
- 우선순위: MVP - 핵심
- 관련 Issue: #314 (#298 동작 대체)
- 예정 Branch: `feat/314-server-goal-profile-expand` (#313 위에 쌓음)
- 관련 Client·AI 문서: 재설계 1차 정리(DR-024~030)

## 기능 목적

- 해결할 문제: #298은 AI 확인 목표만 세분화를 저장하고, 응답에 싣지 않고, 문구를 고치면 지운다. 직접 입력·수정한 목표는 판정 보조 정보가 없다.
- Backend가 담당할 역할: 모든 목표를 세분화해 응답에 싣고, 사용자의 직접 수정과 문구 수정 시 재확장을 처리한다.

## API 정의

- `POST /goals`: 응답에 `goalProfile`. AI 확인 목표는 다듬기 결과, 그 밖은 `POST /internal/v1/goals/profile` 호출. 실패해도 201, `goalProfile: null`.
- `PATCH /goals/{id}`: `goalProfile`(통째 덮어쓰기) 수용. 형식 위반 400 `VALIDATION_ERROR`, 활성 세션 참조 중 409. 문구만 바뀌면 재확장, 문구+`goalProfile`이면 보낸 값.
- AI 내부 계약(가정, AI 담당 확인 필요): 요청 `{requestId, goalText}`, 응답 `{data:{goalProfile:{...}}}`(다듬기와 같은 `goalProfileDraft` 키도 허용). 시간 제한 8초, 재시도 없음.

## 데이터 설계

- 새 컬럼 없음. `goals.profile_json`(V25)을 모든 목표에 쓴다.
- 세션 사본 `session_goals.goal_profile_snapshot_json`은 바꾸지 않는다.

## 처리 흐름

1. 저장·수정은 짧은 트랜잭션(사용자 잠금)으로 끝낸다.
2. 세분화가 필요하면 잠금·트랜잭션 밖에서 AI를 부른다.
3. 결과는 새 짧은 트랜잭션에서, 목표가 남아 있고 문구가 같고 세분화가 아직 비어 있을 때만 붙인다. 그 사이 바뀌었으면 버린다.

## 범위 밖

- 판정 AI에 세분화 전달, 세션 중 세분화 갱신.

## 완료 조건

- [x] 직접 입력·AI 확인 목표 모두 세분화가 저장되고 응답에 나온다(실패 시 null).
- [x] 문구 수정 시 재확장, 동시 수정 시 오래된 결과를 저장하지 않는다.
- [x] AI 호출 동안 사용자 잠금·트랜잭션을 잡지 않는다.
- [x] 테스트·문서 갱신.

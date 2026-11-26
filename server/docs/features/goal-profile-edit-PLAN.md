# Backend 기능 기획서 — 목표 범위(goalProfile) 수정·다시 만들기

## 기본 정보

- 기능명: 목표 범위 수정과 다시 만들기
- 기능 ID: goal-profile-edit
- 작성자: 박진욱
- 작성일: 2026-11-24
- 우선순위: MVP - 핵심
- 관련 Issue: #314 (PR #323을 #327 이후 dev 기준으로 대체)
- 예정 Branch: `feat/314-server-goal-profile-edit`
- 관련 Client·AI 문서: Desktop 「공부로 볼 범위」 화면 요청(2026-11-24), #327

## 기능 목적

- 해결할 문제: 범위는 할 일을 만들 때만 저장되고, 사용자가 고치거나 AI 실패 뒤 다시 만들 방법이 없다.
- Backend가 담당할 역할: PATCH로 범위를 통째 바꾸고, 저장된 문구로 AI 범위를 다시 만든다.

## API 정의

- `PATCH /api/v1/goals/{goalId}`: `goalProfile` 추가(만들 때와 같은 형식). 형식 위반 400 `VALIDATION_ERROR`, 활성 세션 참조 중 409.
- `POST /api/v1/goals/{goalId}/profile`: 본문 없음. 200 목표 응답. AI 실패 503 `GOAL_ANALYSIS_UNAVAILABLE`, 시간 초과 504 `GOAL_ANALYSIS_TIMEOUT`(기존 범위 유지), 활성 세션 409, 없음 404.

## 데이터 설계

- 새 컬럼 없음. `goals.profile_json`을 덮어쓴다. 세션 사본(`session_goals.goal_profile_snapshot_json`)은 바꾸지 않는다.

## 처리 흐름

1. PATCH: 형식 검증 → 사용자 잠금 → 활성 세션 확인 → 문구가 실제로 바뀌면 이전 범위 비움 → 보낸 범위로 덮어쓰기.
2. 다시 만들기: 짧은 트랜잭션으로 소유·활성 세션 확인 → 잠금 밖에서 AI 호출(#327의 재시도 규칙) → 짧은 트랜잭션으로 다시 확인하고 문구가 같을 때만 저장.

## 범위 밖

- 문구 수정 시 자동 재확장: Desktop이 「다시 만들기」로 직접 요청하므로 PATCH에서 AI를 부르지 않는다.
- Goal AI 요청 전체 마감(본문 지연) 보강: 별도 이슈 후보.

## 완료 조건

- [x] PATCH로 범위를 통째 바꾸고 형식 위반·활성 세션은 막는다.
- [x] 다시 만들기가 새 범위를 저장하고, AI 실패 시 기존 범위를 지킨다.
- [x] 테스트·문서 갱신.

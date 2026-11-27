# Backend 기능 기획서 — 세션 중 할 일 교체

## 기본 정보

- 기능명: 세션 중 할 일 교체(replaceGoalId)
- 기능 ID: session-goal-replace
- 작성자: 박진욱
- 작성일: 2026-11-25
- 우선순위: MVP - 핵심
- 관련 Issue: #320 (추가는 #327에서 구현, 교체만 남음)
- 예정 Branch: `feat/320-server-session-goal-replace`
- 관련 Client·AI 문서: 재설계 1차 정리(DR-024~030)

## 기능 목적

- 해결할 문제: 세션 할 일이 3개 찬 뒤에는 다른 할 일로 바꿀 수 없다.
- Backend가 담당할 역할: 기존 추가 API에서 지정한 자리를 새 할 일로 덮어쓰고 목록 버전을 올린다.

## API 정의

- `POST /api/v1/sessions/{sessionId}/goals`: `{goalId, goalVersion, replaceGoalId?}`. `replaceGoalId`가 있으면 교체.
- 응답: 기존과 같은 `{goals, goalVersion}`.
- Failure: 버전 불일치 409 `SESSION_VERSION_CONFLICT`, 교체 대상 없음·새 할 일 중복·같은 ID 400 `VALIDATION_ERROR`, 할 일 없음 404 `GOAL_NOT_FOUND`, 실행 중이 아님 409 `SESSION_STATE_INVALID`.

## 데이터 설계

- 마이그레이션 없음. `session_goals`의 해당 자리를 덮어쓰고 `learning_sessions.goal_version`을 올린다(이력 없음).
- 활성 세션 수정 잠금은 할 일 사본(`session_goals`) 기준으로 보고, 사본이 없는 옛 세션만 시작 목표(`learning_sessions.goal_id`)로 본다. 그래야 교체로 빠진 할 일이 풀린다.

## 처리 흐름

1. 기존 추가와 같은 인증·실행 Desktop·사용자/세션 잠금·RUNNING 확인.
2. 이미 반영된 교체 재전송이면 현재 목록 반환.
3. 버전 확인 → 교체 대상 존재·새 할 일 미포함 확인 → 새 할 일 소유·활성 확인 → 자리 덮어쓰기 → 버전 증가.

## 범위 밖

- 교체 이력, 세션 시작 목표(리포트 제목) 변경.

## 완료 조건

- [x] 교체·재전송·버전 충돌·중복·대상 없음 테스트.
- [x] 교체 뒤 goalVersion과 응답 goals[]가 일치한다.
- [x] 빠진 할 일은 수정·삭제 잠금에서 풀린다.
- [x] 테스트·문서 갱신.

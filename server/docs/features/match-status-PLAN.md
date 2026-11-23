# Backend 기능 기획서 — 화면 판정 matchStatus 저장과 응답

## 기본 정보

- 기능명: 화면 판정 할 일 일치 정도(matchStatus)
- 기능 ID: match-status
- 작성자: 박진욱
- 작성일: 2026-11-23
- 우선순위: MVP - 핵심
- 관련 Issue: #319
- 예정 Branch: `feat/319-server-match-status`
- 관련 Client·AI 문서: 재설계 1차 정리 DR-030(AI 담당과 합의), ERD 8.1

## 기능 목적

- 해결할 문제: RELATED 판정이 할 일과 맞는지, 공부지만 할 일과 먼지 구분할 수 없다.
- Backend가 담당할 역할: AI가 주는 `matchStatus`를 검증해 저장하고 공개 응답에 싣는다. 이후 할 일 추가 질문(`FAR` 10분)과 할 일별 시간(`MATCHED`만)의 근거가 된다.

## API 정의

- `POST /api/v1/sessions/{sessionId}/analysis-runs` 응답에 `matchStatus`(`MATCHED`/`FAR`/`NOT_ENOUGH`/null) 추가.
- `GET /api/v1/sessions/{sessionId}/analysis-runs` 이력 항목도 같은 응답 형식이라 함께 담긴다.
- AI 내부 응답(`/internal/v1/analysis-runs`)의 선택 필드 `matchStatus`를 읽는다.

## 데이터 설계

- V28: `analysis_runs.match_status` VARCHAR(16) NULL. CHECK: NULL이거나, 값 목록 안이면서 `relation = 'RELATED'` 그리고 `analysis_status = 'COMPLETED'`.
- ERD 8.1은 V26 목록에 적혀 있지만 V26·V27은 #300·#301이 이미 써서 V28로 둔다.
- 화면 원문은 저장하지 않는다.

## 처리 흐름

1. AI 응답의 기존 검증(decision·confidence·reasonCode·버전)은 그대로다.
2. 최종 판정이 RELATED·COMPLETED일 때만 `matchStatus`를 읽는다. 신뢰도 부족으로 UNCERTAIN이 되면 비운다.
3. 필드가 없거나(아직 배포 전인 AI) 허용 밖 값이면 판정은 유지하고 값만 null로 둔다. 보조 정보라 판정 전체를 버리지 않는다.

## 범위 밖

- `GOAL_ADD_PROMPT`(RELATED + `FAR` 10분), 할 일별 시간 집계.
- 할 일 여러 개를 AI에 보내고 `matchedGoalId`를 받는 일(#316 이후).

## 완료 조건

- [x] 세 값과 null·누락·잘못된 값 정규화 테스트.
- [x] DB 저장·POST 응답·GET 이력 확인, 기존 응답 필드는 그대로.
- [x] 마이그레이션(Flyway+validate)·테스트·문서 갱신.

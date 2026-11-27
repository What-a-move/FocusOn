# Backend 기능 기획서 — 리포트 진행 요약과 달력 하루 목표 달성

## 기본 정보

- 기능명: 진행 요약(`GET /reports/progress`)·달력 하루 목표 달성·하루 목표 고정
- 기능 ID: report-progress
- 작성자: 박진욱
- 작성일: 2026-11-27
- 우선순위: MVP - 기본
- 관련 Issue: #374 (선행 #373)
- 예정 Branch: `feat/374-server-report-progress` (#373 위에 쌓음)
- 관련 문서: Notion `GET /api/v1/reports/progress`, `GET /api/v1/reports/calendar`(재설계 1차)

## 기능 목적

- 해결할 문제: 홈·메뉴바·위젯에 오늘·이번 주 집중 시간과 목표, 연속 기록을 보여 줄 API가 없고, 달력에 하루 목표 달성 표시가 없다.
- Backend가 담당할 역할: 세션 시작 때 하루 목표를 고정하고, 확정 세션으로 달성 여부와 진행 요약을 계산한다.

## API 정의

- `GET /api/v1/reports/progress?date=` → `{ date, today, week, streak }`
- `GET /api/v1/reports/calendar` 응답 `days[].dailyTargetAchieved` 추가
- 오류: 400 `VALIDATION_ERROR`(date 형식), 401, 403(Extension)

## 데이터 설계

- V36 `learning_sessions.daily_target_ms_snapshot`(NULL 허용, 양수 CHECK `NOT VALID`).
- 별도 집계 테이블 없이 `learning_sessions`를 읽는다.

## 처리 흐름

1. 세션 시작: 사용자 `dailyTargetMs`(없으면 60분)를 처음 저장할 때 함께 고정한다.
2. 하루 달성: 그날 확정 세션 합계 ≥ 그날 첫 세션의 고정 목표(도입 전 세션은 60분).
3. 진행 요약: 기준일까지 확정 세션을 날짜별로 묶어 오늘·주(월요일 시작)·연속 기록을 계산한다.

## 범위 밖

- 진행 중 세션 합산(Desktop 담당), 위젯 저장.

## 완료 조건

- [x] 스냅샷 고정·달력 달성·진행 요약·연속 기록 테스트
- [x] 마이그레이션·문서 갱신

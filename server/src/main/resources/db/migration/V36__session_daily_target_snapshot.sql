-- #374 재설계 1차: 세션 시작 때 사용자의 하루 목표(ms)를 고정한다. 달력의 하루 목표 달성과 연속 기록은
-- 그날 첫 세션의 이 값으로 판정해, 나중에 목표를 바꿔도 지난 날짜의 결과가 바뀌지 않는다.
-- 도입 전 세션은 NULL이며 판정 때 기본값(60분)을 쓴다.
-- 복구: `ALTER TABLE learning_sessions DROP CONSTRAINT ck_learning_sessions_daily_target_snapshot;
-- ALTER TABLE learning_sessions DROP COLUMN daily_target_ms_snapshot;` 뒤 flyway_schema_history에서
-- version 36 행을 지운다(고정한 하루 목표만 사라진다).
ALTER TABLE learning_sessions ADD COLUMN daily_target_ms_snapshot BIGINT;
ALTER TABLE learning_sessions ADD CONSTRAINT ck_learning_sessions_daily_target_snapshot CHECK
    (daily_target_ms_snapshot IS NULL OR daily_target_ms_snapshot > 0) NOT VALID;

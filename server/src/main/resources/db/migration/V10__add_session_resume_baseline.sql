-- 기존 세션의 누적 시간은 보존하고 현재까지 확정된 값을 실행 구간 기준값으로 기록한다.
ALTER TABLE learning_sessions ADD COLUMN accumulated_at_resume_ms BIGINT NOT NULL DEFAULT 0;
UPDATE learning_sessions SET accumulated_at_resume_ms = accumulated_active_ms;
ALTER TABLE learning_sessions ADD CONSTRAINT ck_session_resume_baseline
    CHECK (accumulated_at_resume_ms >= 0 AND accumulated_at_resume_ms <= accumulated_active_ms);

-- 응답을 잃은 복수 목표 저장·세션 중 새 할 일 생성을 안전하게 재시도한다.
ALTER TABLE idempotency_records DROP CONSTRAINT ck_idempotency_scope;
ALTER TABLE idempotency_records ADD CONSTRAINT ck_idempotency_scope
    CHECK (scope IN ('CREATE_SESSION', 'PAUSE_SESSION', 'RESUME_SESSION',
        'EXTEND_SESSION', 'END_SESSION', 'CREATE_GOAL'));

-- V30 has committed by this point. Validate existing rows without its exclusive locks.
ALTER TABLE learning_sessions VALIDATE CONSTRAINT ck_learning_session_goal_version;
ALTER TABLE analysis_runs VALIDATE CONSTRAINT ck_analysis_run_goal_version;

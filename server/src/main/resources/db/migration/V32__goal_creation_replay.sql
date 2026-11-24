-- 응답을 잃은 복수 목표 저장·세션 중 새 할 일 생성을 안전하게 재시도한다.
ALTER TABLE idempotency_records DROP CONSTRAINT ck_idempotency_scope;
ALTER TABLE idempotency_records ADD CONSTRAINT ck_idempotency_scope
    CHECK (scope IN ('CREATE_SESSION', 'PAUSE_SESSION', 'RESUME_SESSION',
        'EXTEND_SESSION', 'END_SESSION', 'CREATE_GOAL'));

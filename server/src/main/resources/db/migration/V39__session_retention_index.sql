-- #389: 종료 후 1년 보관 정리가 종료 세션만 종료 시각 순으로 찾도록 부분 Index를 둔다.
-- 복구: DROP INDEX ix_learning_sessions_retention; 뒤 flyway_schema_history의 39행을 정리한다.
CREATE INDEX ix_learning_sessions_retention ON learning_sessions (ended_at, id)
    WHERE status IN ('ENDED', 'ABANDONED');

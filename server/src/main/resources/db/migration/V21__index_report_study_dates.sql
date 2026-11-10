-- 기존 세션과 고정 study_date를 보존하고 사용자별 기간 조회를 지원한다.
CREATE INDEX ix_learning_sessions_user_study_date
    ON learning_sessions (user_id, study_date);

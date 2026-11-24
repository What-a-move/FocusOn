-- 적용 중 오류가 나면 Flyway 트랜잭션이 열 추가와 제약을 함께 되돌린다.
-- 앱 롤백 시 추가한 데이터와 컬럼을 보존한다.
-- DR-030: 목표 집합 버전은 세션 실행 명령 버전과 독립적이다.
ALTER TABLE learning_sessions ADD COLUMN goal_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE learning_sessions ADD CONSTRAINT ck_learning_session_goal_version
    CHECK (goal_version > 0) NOT VALID;
ALTER TABLE analysis_runs ADD COLUMN goal_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE analysis_runs ADD CONSTRAINT ck_analysis_run_goal_version
    CHECK (goal_version > 0) NOT VALID;
ALTER TABLE analysis_runs ADD COLUMN matched_goal_id BIGINT;
ALTER TABLE analysis_runs ADD CONSTRAINT ck_analysis_matched_goal CHECK
    (matched_goal_id IS NULL OR (match_status IS NOT NULL AND match_status = 'MATCHED')) NOT VALID;

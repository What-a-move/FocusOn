-- #383: 메신저 원문과 식별자 없이 보호 관측의 시간 집계 구분만 저장한다.
-- 기존 행은 NULL이다. 운영 분석 이력의 전체 스캔을 피하고 새 쓰기부터 검증한다.
-- 복구: ALTER TABLE analysis_runs DROP CONSTRAINT ck_analysis_runs_messenger_scope;
-- ALTER TABLE analysis_runs DROP COLUMN messenger_scope;
-- 이전 서버 버전을 준비한 뒤 flyway_schema_history의 38행을 정리한다.
ALTER TABLE analysis_runs ADD COLUMN messenger_scope VARCHAR(16);
ALTER TABLE analysis_runs ADD CONSTRAINT ck_analysis_runs_messenger_scope CHECK
    (messenger_scope IS NULL OR (messenger_scope IN ('LEARNING', 'OTHER')
        AND relation = 'PRIVACY_BLOCKED' AND analysis_status = 'SKIPPED')) NOT VALID;

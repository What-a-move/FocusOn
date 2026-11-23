-- #319 DR-030: 확정 RELATED 판정이 할 일과 얼마나 맞는지 저장한다. 다른 판정에서는 NULL이다.
-- 화면 원문은 저장하지 않으며 값은 고정 목록뿐이다.
-- analysis_runs는 관측마다 쌓이는 큰 테이블이라 기존 행 검사 없이(NOT VALID) 새 행부터 검사한다.
-- 기존 행은 새 컬럼이 모두 NULL이라 제약을 어길 수 없다(V11·V20과 같은 방식).
-- 복구: 데이터 손실 없이 되돌릴 수 있다. `ALTER TABLE analysis_runs DROP CONSTRAINT
-- ck_analysis_runs_match_status; ALTER TABLE analysis_runs DROP COLUMN match_status;` 뒤
-- flyway_schema_history에서 version 28 행을 지운다(matchStatus 값만 사라지고 판정은 그대로).
ALTER TABLE analysis_runs ADD COLUMN match_status VARCHAR(16);
ALTER TABLE analysis_runs ADD CONSTRAINT ck_analysis_runs_match_status CHECK
    (match_status IS NULL
     OR (match_status IN ('MATCHED','FAR','NOT_ENOUGH') AND relation = 'RELATED'
         AND analysis_status = 'COMPLETED')) NOT VALID;

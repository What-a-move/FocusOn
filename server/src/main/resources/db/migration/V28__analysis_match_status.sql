-- #319 DR-030: 확정 RELATED 판정이 할 일과 얼마나 맞는지 저장한다. 다른 판정에서는 NULL이다.
-- 화면 원문은 저장하지 않으며 값은 고정 목록뿐이다. 기존 행은 모두 NULL이라 제약을 바로 만족한다.
ALTER TABLE analysis_runs ADD COLUMN match_status VARCHAR(16);
ALTER TABLE analysis_runs ADD CONSTRAINT ck_analysis_runs_match_status CHECK
    (match_status IS NULL
     OR (match_status IN ('MATCHED','FAR','NOT_ENOUGH') AND relation = 'RELATED'
         AND analysis_status = 'COMPLETED'));

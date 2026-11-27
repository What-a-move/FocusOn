-- #381 재설계 B: UNRELATED 판정의 딴짓 종류를 고정 목록 값으로만 저장한다. host·앱 이름은 저장하지 않는다.
-- analysis_runs는 관측마다 쌓이는 큰 테이블이라 기존 행 검사 없이(NOT VALID) 새 행부터 검사한다.
-- 기존 행은 새 컬럼이 모두 NULL이라 제약을 어길 수 없다(V28과 같은 방식).
-- 복구: 데이터 손실 없이 되돌릴 수 있다. `ALTER TABLE analysis_runs DROP CONSTRAINT
-- ck_analysis_runs_off_task_category; ALTER TABLE analysis_runs DROP COLUMN off_task_category;` 뒤
-- flyway_schema_history에서 version 37 행을 지운다(카테고리 값만 사라지고 판정은 그대로).
ALTER TABLE analysis_runs ADD COLUMN off_task_category VARCHAR(16);
ALTER TABLE analysis_runs ADD CONSTRAINT ck_analysis_runs_off_task_category CHECK
    (off_task_category IS NULL
     OR (off_task_category IN ('VIDEO','SNS','COMMUNITY','SHOPPING','GAME','MESSENGER','NEWS',
                               'OTHER') AND relation = 'UNRELATED')) NOT VALID;

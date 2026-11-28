-- #392: 종료 전에 접수한 분석이 마지막 실행 구간에 속하는지 학습 정리에서 검증한다.
-- 기존 종료 세션의 실행 구간은 복원하지 않는다.
ALTER TABLE learning_summaries ADD COLUMN input_running_since TIMESTAMPTZ;

-- #297 개입 이벤트에 알림을 만든 근거 판정 id를 남긴다. 기존 이벤트는 근거를 알 수 없어 NULL이다.
-- 제목·URL·본문은 저장하지 않고 판정 기록 id로만 잇는다.
ALTER TABLE intervention_events ADD COLUMN analysis_run_id BIGINT REFERENCES analysis_runs(id);

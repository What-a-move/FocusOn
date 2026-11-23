-- #300 사용자 반응 NOT_OFF_TASK(「이것도 공부야」)를 허용한다. 다른 반응처럼 actedAt이 필요하다.
-- 근거 판정은 V24의 analysis_run_id로만 잇고 제목·URL은 저장하지 않는다.
ALTER TABLE intervention_events DROP CONSTRAINT intervention_events_status_check;
ALTER TABLE intervention_events ADD CONSTRAINT intervention_events_status_check CHECK
    (status IN ('PENDING','ACKNOWLEDGED','DISMISSED','EXPIRED','NOT_OFF_TASK'));
ALTER TABLE intervention_events DROP CONSTRAINT ck_intervention_ack;
ALTER TABLE intervention_events ADD CONSTRAINT ck_intervention_ack CHECK
    ((status IN ('ACKNOWLEDGED','DISMISSED','NOT_OFF_TASK') AND acted_at IS NOT NULL)
     OR (status IN ('PENDING','EXPIRED') AND acted_at IS NULL));

-- 적용 중 오류가 나면 Flyway 트랜잭션이 전체 변경을 되돌린다.
-- 배포 완료 후 복구 시 이전 앱이 GOAL_ADD_PROMPT 행을 읽을 수 없으므로 호환 버전을 먼저 사용한다.
-- DR-030: 현재 검증된 FAR 공부 시간만 세고 세션당 안내를 한 번 만든다.
ALTER TABLE intervention_policy_states ADD COLUMN far_goal_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE intervention_policy_states ADD COLUMN far_duration_ms BIGINT NOT NULL DEFAULT 0
    CHECK (far_duration_ms BETWEEN 0 AND 600000);
ALTER TABLE intervention_policy_states ADD COLUMN far_checkpoint_at TIMESTAMPTZ;
ALTER TABLE intervention_events DROP CONSTRAINT intervention_events_type_check;
ALTER TABLE intervention_events ADD CONSTRAINT intervention_events_type_check
    CHECK (type IN ('OFF_TASK','BREAK_SUGGESTION','GOAL_ADD_PROMPT'));
CREATE UNIQUE INDEX uq_goal_add_prompt_once ON intervention_events(session_id)
    WHERE type = 'GOAL_ADD_PROMPT';

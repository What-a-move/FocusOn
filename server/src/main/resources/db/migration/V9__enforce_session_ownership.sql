-- 기존 세션에 다른 사용자의 목표 또는 기기가 연결돼 있으면 변경하지 않고 실패한다.
-- 실패한 경우 소유권을 임의로 바꾸지 말고 데이터를 확인한 후 다시 적용한다.
ALTER TABLE goals
    ADD CONSTRAINT uq_goals_id_user UNIQUE (id, user_id);

ALTER TABLE learning_sessions
    ADD CONSTRAINT fk_learning_sessions_goal_owner
        FOREIGN KEY (goal_id, user_id) REFERENCES goals (id, user_id),
    ADD CONSTRAINT fk_learning_sessions_device_owner
        FOREIGN KEY (runner_device_id, user_id) REFERENCES user_devices (id, user_id);

CREATE INDEX ix_learning_sessions_goal_user ON learning_sessions (goal_id, user_id);
CREATE INDEX ix_learning_sessions_device_user ON learning_sessions (runner_device_id, user_id);

-- #298 목표 세분화(GoalProfile)를 목표마다 저장하고, 세션 시작 때 고른 목표들의 사본을 고정한다.
-- 세션 하나에 할 일 최대 3개(재설계 「할 일 목록 + 최대 3개 선택」)를 담을 수 있게 목록 테이블로 둔다.
-- 지금 세션 시작 API는 목표 1개만 받으므로 position 1만 쓴다. 기존 세션·목표 행은 그대로다.
-- 목표 다듬기 질문·답변 원문, 화면 내용은 저장하지 않는다.
ALTER TABLE goals ADD COLUMN profile_json JSONB;

CREATE TABLE session_goals (
    session_id BIGINT NOT NULL REFERENCES learning_sessions(id) ON DELETE CASCADE,
    position SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 3),
    goal_id BIGINT NOT NULL REFERENCES goals(id),
    goal_text_snapshot VARCHAR(40) NOT NULL,
    goal_profile_snapshot_json JSONB,
    CONSTRAINT pk_session_goals PRIMARY KEY (session_id, position),
    CONSTRAINT uq_session_goals_goal UNIQUE (session_id, goal_id)
);

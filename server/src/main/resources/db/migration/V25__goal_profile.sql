-- #298 목표 세분화(GoalProfile)를 목표마다 저장하고 세션 시작 때 고정한다. 기존 행은 NULL이다.
-- 목표 다듬기 질문·답변 원문, 화면 내용은 저장하지 않는다.
ALTER TABLE goals ADD COLUMN profile_json JSONB;
ALTER TABLE learning_sessions ADD COLUMN goal_profile_snapshot_json JSONB;

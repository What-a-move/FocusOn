-- #313 재설계 1차: 목표를 할 일 목록으로 쓰며 사용자가 직접 표시하는 상태(OPEN/DONE)를 둔다.
-- 완료 판정이 아니라 표시값이다. 기존 목표는 모두 OPEN이다.
-- V28은 #319(analysis_runs.match_status)가 쓴다. 이 마이그레이션은 #319 머지 뒤에 머지한다.
ALTER TABLE goals ADD COLUMN todo_status VARCHAR(8) NOT NULL DEFAULT 'OPEN';
ALTER TABLE goals ADD CONSTRAINT ck_goals_todo_status CHECK (todo_status IN ('OPEN','DONE')) NOT VALID;
CREATE INDEX idx_goals_user_todo ON goals(user_id, todo_status, id DESC)
    WHERE deleted_at IS NULL;

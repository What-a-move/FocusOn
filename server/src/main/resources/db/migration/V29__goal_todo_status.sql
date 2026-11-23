-- #313 재설계 1차: 목표를 할 일 목록으로 쓰며 사용자가 직접 표시하는 상태(OPEN/DONE)를 둔다.
-- 완료 판정이 아니라 표시값이다. 기존 목표는 모두 OPEN이다.
-- V28은 #319(analysis_runs.match_status)가 쓴다. 이 마이그레이션은 #319 머지 뒤에 머지한다.
-- PostgreSQL 11+에서 상수 기본값 NOT NULL 컬럼 추가는 테이블을 다시 쓰지 않는다. CHECK는 NOT VALID로
-- 기존 행 검사를 생략한다(기존 행은 모두 기본값 OPEN).
-- 복구: `DROP INDEX idx_goals_user_todo; ALTER TABLE goals DROP CONSTRAINT ck_goals_todo_status;
-- ALTER TABLE goals DROP COLUMN todo_status;` 뒤 flyway_schema_history에서 version 29 행을 지운다.
-- 사용자가 표시한 완료 상태만 사라진다.
ALTER TABLE goals ADD COLUMN todo_status VARCHAR(8) NOT NULL DEFAULT 'OPEN';
ALTER TABLE goals ADD CONSTRAINT ck_goals_todo_status CHECK (todo_status IN ('OPEN','DONE')) NOT VALID;
CREATE INDEX idx_goals_user_todo ON goals(user_id, todo_status, id DESC)
    WHERE deleted_at IS NULL;

-- 수신 시각과 클라이언트 관측 순서를 분리하며 기존 세션은 그대로 보존한다.
ALTER TABLE learning_sessions ADD COLUMN last_heartbeat_observed_at TIMESTAMPTZ;

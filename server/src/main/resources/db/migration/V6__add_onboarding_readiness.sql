ALTER TABLE user_devices ADD COLUMN screen_analysis_ready BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_devices ADD COLUMN excluded_domains_step_completed BOOLEAN NOT NULL DEFAULT FALSE;

-- #373 재설계 1차: 집중 설정 새 필드 5개. 기존 행은 기본값을 받는다(메신저 없음, 미리 알림 켜짐,
-- 하루 목표 60분, 주간 목표 없음, 회고 켜짐). 새 필드는 세션 snapshot에 복사하지 않는다.
-- CHECK는 NOT VALID로 기존 행 검사를 생략한다(기존 행은 모두 기본값이라 통과한다).
-- 복구: 추가한 5개 컬럼과 제약을 DROP한 뒤 flyway_schema_history에서 version 35 행을 지운다
-- (새 설정값만 사라진다).
ALTER TABLE user_focus_settings ADD COLUMN learning_messengers VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE user_focus_settings ADD COLUMN pre_distraction_reminder_enabled BOOLEAN NOT NULL
    DEFAULT TRUE;
ALTER TABLE user_focus_settings ADD COLUMN daily_target_ms BIGINT NOT NULL DEFAULT 3600000;
ALTER TABLE user_focus_settings ADD COLUMN weekly_target_ms BIGINT;
ALTER TABLE user_focus_settings ADD COLUMN retrospective_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_focus_settings ADD CONSTRAINT ck_user_focus_settings_daily_target CHECK
    (daily_target_ms BETWEEN 600000 AND 36000000 AND daily_target_ms % 60000 = 0) NOT VALID;
ALTER TABLE user_focus_settings ADD CONSTRAINT ck_user_focus_settings_weekly_target CHECK
    (weekly_target_ms IS NULL
     OR (weekly_target_ms BETWEEN 3600000 AND 252000000 AND weekly_target_ms % 60000 = 0))
    NOT VALID;

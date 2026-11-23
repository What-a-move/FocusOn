-- #301 휴식 제안의 민감값 없는 근거(이어진 이탈 횟수, 관찰 분)를 남긴다. 이탈 알림·기존 행은 NULL이다.
ALTER TABLE intervention_events ADD COLUMN break_repeat_count INTEGER
    CHECK (break_repeat_count IS NULL OR break_repeat_count >= 3);
ALTER TABLE intervention_events ADD COLUMN break_window_minutes INTEGER
    CHECK (break_window_minutes IS NULL OR break_window_minutes >= 1);

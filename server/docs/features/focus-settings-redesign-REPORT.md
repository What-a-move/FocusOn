# 결과 리포트 — 집중 설정 새 필드 5개 (#373)

## 변경 요약

- V35: `learning_messengers`, `pre_distraction_reminder_enabled`, `daily_target_ms`(기본 3600000), `weekly_target_ms`, `retrospective_enabled` + 목표 CHECK.
- `UserFocusSettingsEntity.applyRedesign`, `UpdateFocusSettingsRequest`·`FocusSettingsResponse` 새 필드, 서비스 검증(분 단위·범위·메신저 목록·중복).
- `weeklyTargetMs`는 JSON 원본으로 받아 "없음(유지)"과 "null(지움)"을 구분한다.
- `docs/API_CONTRACT.md` 사용자 설정 절.

## 테스트

- `UserConfigurationApiTests.redesignFocusSettingsAreOptionalAndValidated`: 설정 없음 기본값, 기존 4필드만 저장, 새 필드 저장, 빠진 필드 유지, null로 주간 목표 지움, 잘못된 값 8종 400.
- 결과는 #374 리포트와 PR에서 함께 기록한다(같은 PR 묶음).

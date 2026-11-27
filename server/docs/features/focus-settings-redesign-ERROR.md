# 개발 오류 보고서 — 집중 설정 새 필드 (#373)

## 1. 소수 하루 목표가 잘려서 저장됨

- 발견: CodeRabbit 리뷰(PR #375, 2026-11-27).
- 증상: `dailyTargetMs: 36000000.5`를 보내면 Jackson이 정수로 잘라 `36000000`으로 바인딩해, 분 단위 검증을 통과하고 저장됐다. 계약은 분 단위가 아닌 값을 400으로 거절한다.
- 조치: `dailyTargetMs`도 `weeklyTargetMs`처럼 JSON 원본(`JsonNode`)으로 받아 정수가 아니거나 null·문자면 400으로 거절한다. 없으면 저장값을 유지한다.
- 검증: `UserConfigurationApiTests.redesignFocusSettingsAreOptionalAndValidated`에 소수·null·문자 하루 목표, 소수 주간 목표 사례 추가.

## 2. 테스트 도우미 이름 충돌

- 테스트 도우미 이름이 정적 import `put`과 겹쳐 `putFocus`로 바꿨다(제품 오류 아님).

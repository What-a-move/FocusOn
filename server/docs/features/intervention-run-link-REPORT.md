# 결과 리포트 — 개입 이벤트 근거 판정 id (#297)

## 변경 요약

- V24로 `intervention_events.analysis_run_id`를 추가하고, 알림 생성 시 타임라인의 `offTaskRun`을 저장한다.
- `OffTaskTimeline.Snapshot`에 `offTaskRun`을 추가했다(무관 판정에서 갱신, 판단 보류 중 유지, 구간 종료 시 삭제).
- 공개 API 변경 없음.

## 테스트

- 단위: 무관 판정 2건 → 보류 → 관련 순서에서 근거가 2번 판정으로 유지되고 관련 판정에서 지워짐, `offTaskRun` 없는 이전 snapshot 역직렬화.
- 통합(`ObservationDeliveryIntegrationTests`): 실제 정책 흐름으로 만든 이벤트의 `analysis_run_id`가 근거 판정 id와 같음.
- 전체 `./gradlew check`: 396개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과.

## 남은 제한

- 기존 이벤트는 근거를 알 수 없어 NULL이다.
- #296이 먼저 머지돼야 한다(같은 브랜치 위에 쌓음). 마이그레이션 번호 V24를 쓰므로 다른 브랜치의 V24와 겹치지 않게 머지 순서를 지킨다.

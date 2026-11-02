# 목표 수정·삭제 작업 결과

- Issue: #97
- Branch: `feat/97-goal-update-delete`
- 상태: 수정·삭제 구현, 사용자 Swagger 테스트 및 동시 출발 자동 검증 완료. Notion 상세 대조는 남아 있다.
- `PATCH /api/v1/goals/{goalId}`: 본인 목표만 수정하며 기존 목표 생성과 동일하게 양끝 공백 제거 후 2~40 코드 포인트를 검증한다.
- `DELETE /api/v1/goals/{goalId}`: 삭제 시각·상태를 설정하고 204 빈 본문을 반환한다. 삭제되거나 본인이 아닌 목표는 기존 조회 정책에 맞춰 404다.
- `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING` 세션이 해당 목표를 참조하면 `409 GOAL_UPDATE_BLOCKED`다. 세션 시작과 동일한 사용자 행 잠금을 먼저 획득한다.
- 종료 세션의 목표 문구 스냅샷은 수정·삭제해도 유지한다. 목표 문구 수정 시 이전 명확화 문구는 초기화한다.
- H2 자동 테스트로 인증·타인 목표·입력·soft delete·재삭제·활성 차단과 종료 세션 스냅샷을 확인했다.
- 임시 PostgreSQL에서 Flyway V1~V9, 목표 테스트와 세션 시작 대 수정·삭제 동시 출발 테스트가 통과했다. 특정 잠금 대기를 강제로 유도하거나 양쪽 요청의 DB 내 동시 진입을 계측한 것은 아니다. 임시 DB는 삭제했다.
- `./gradlew clean build --no-daemon`: 116개 중 115개 통과, 실패·오류 0, 기존 PostgreSQL 전용 닉네임 테스트 1개 건너뜀. Checkstyle 통과.
- Notion 정본 조회가 연결 도구에서 실패해 이슈와 기존 구현을 기준으로 했다. PATCH 응답·재삭제 정책의 정본 대조는 미완료다.
- 2026-11-01 Swagger 테스트: 별도 18080 서버와 격리 PostgreSQL에서 PATCH·DELETE 노출 및 무인증 각각 401을 Swagger UI로 확인했다. 정상·실패 요청은 같은 서버에 직접 HTTP로 실행했으며 Swagger UI 인증 요청은 수행하지 않았다.
- 실제 Google 계정 교환 201, 테스트 목표 생성 201, 수정 200, 한 글자 수정 400 `GOAL_TEXT_INVALID`, 삭제 204 빈 본문, 재삭제와 삭제 후 조회 각각 404 `GOAL_NOT_FOUND`를 확인했다. PostgreSQL에서 테스트 목표 행과 삭제 시각도 확인했다. 테스트용 DB는 삭제했고 기존 DB는 변경하지 않았다.
- 2026-11-02 사용자가 수정·삭제 API Swagger 테스트를 모두 완료했다고 확인했다. 개별 요청과 응답의 세부 결과는 전달받지 않았다.
- 2026-11-02 사용자 API 테스트 후 격리 PostgreSQL 17.11에서 Flyway V1~V9 적용 및 세션 시작과 목표 수정·삭제의 동시 출발 테스트 2개를 재실행해 모두 통과했다.
- 별도 트랜잭션으로 사용자 행을 잠근 뒤 세션 시작과 수정 또는 삭제 요청이 잠금 대기 사슬에 진입했음을 `pg_blocking_pids`로 확인하는 테스트 2개도 PostgreSQL에서 통과했다. 잠금을 해제한 뒤 허용되는 응답 조합과 목표 스냅샷 정합성을 검증했다. 이는 테스트용 트랜잭션이 유발한 대기이며 API 요청끼리 서로 잠근 시점을 직접 계측한 것은 아니다. 테스트 DB는 삭제했다.
- 테스트 추가 후 전체 `clean build`: 118개 중 115개 통과, 3개 건너뜀(기존 PostgreSQL 전용 1개, 신규 PostgreSQL 전용 2개). 신규 2개는 위 PostgreSQL 실행에서 별도 통과했으며 Checkstyle도 통과했다.

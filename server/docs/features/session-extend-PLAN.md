# 세션 시간 연장 계획

- Issue #168, `feat/168-session-extend`, 기준 `dev` cd0de54.
- POST `/api/v1/sessions/{sessionId}/extend`, Bearer·Idempotency-Key·If-Match-Version 필수. 기기 헤더는 필수가 아니며 토큰의 실행 Desktop 소유권을 검증한다.
- `additionalDurationMs`는 300000~18000000ms, 60000ms 단위. 사용자 제공 세션 정책과 Desktop 인계 계약을 따른다.
- RUNNING·PAUSED·AUTO_PAUSED는 상태를 유지하고 AWAITING_END_CONFIRMATION은 RUNNING으로 전환해 새 실행 구간을 기록한다.
- 확정된 연장 이력과 명령 재시도 결과를 세션 변경과 같은 트랜잭션에서 저장한다. 기존 값 및 합계의 정수 범위를 검증한다.
- 세부 기준은 이슈 댓글로 공유했다. Notion 원본은 접근 거부되어 최신 정본 대조가 남았다.
- 최신 dev `8a5933f`의 V11~V15 뒤에 연장 이력 migration을 V16으로 배치한다. 기존 마이그레이션을 수정하지 않는다.
- 실제 Google 인증 후 격리 서버에서 연장 HTTP 요청을 검증하고, PostgreSQL에서 연장과 일시정지의 동시 요청을 확인한다.

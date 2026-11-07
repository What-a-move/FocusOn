# 세션 시간 연장 계획

- Issue #168, `feat/168-session-extend`, 기준 `dev` cd0de54.
- POST `/api/v1/sessions/{sessionId}/extend`, Bearer·Idempotency-Key·If-Match-Version 필수. 기기 헤더는 필수가 아니며 토큰의 실행 Desktop 소유권을 검증한다.
- `additionalDurationMs`는 300000~18000000ms, 60000ms 단위. 사용자 제공 세션 정책과 Desktop 인계 계약을 따른다.
- RUNNING·PAUSED·AUTO_PAUSED는 상태를 유지하고 AWAITING_END_CONFIRMATION은 RUNNING으로 전환해 새 실행 구간을 기록한다.
- 확정된 연장 이력과 명령 재시도 결과를 세션 변경과 같은 트랜잭션에서 저장한다. 기존 값 및 합계의 정수 범위를 검증한다.
- 세부 기준은 이슈 댓글로 공유했다. Notion 원본은 접근 거부되어 최신 정본 대조가 남았다.
- V11은 #167 브랜치의 별도 V11과 충돌 가능하므로 dev 병합 순서에 따라 재번호가 필요하다. 기존 마이그레이션을 수정하지 않는다.

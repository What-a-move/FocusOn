# 세션 종료 API 계획

## 기본 정보·목적

- Issue #163 / `feat/163-session-end`, 2026-11-05, MVP 핵심.
- 사용자 승인: 이슈 생성·구현·검증. 기존 세션·인증·멱등 패턴을 재사용한다.
- 기준: `apps/docs/BETA-API-HANDOFF.md`, Notion 세션 상태 전이 정책 3.3 (브라우저 확인), 기존 #132.

## API 정의

- `POST /api/v1/sessions/{sessionId}/end`, 필수 JSON `{ "accumulatedActiveMs": 1800000 }`. 2026-11-05 사용자 승인으로 기존 본문 없음 계약을 대체한다.
- Authorization·Idempotency-Key(UUID)·If-Match-Version(0 이상 정수) 필수. X-Device-Id 불필요.
- 본인 세션을 실행하는 Desktop 토큰만 허용한다. 잘못된 소유자·실행 기기는 404다.
- 성공 200 `{ data: { status, version, accumulatedActiveMs, targetDurationMs, endedAt } }`.
- 인증 401, 헤더 400, 세션 없음 404, 상태·버전·멱등 충돌 409, 기존 공통 오류 형식을 사용한다.

## 데이터·처리 흐름

1. Controller → SessionCommandService → 사용자 행 잠금 → 소유·실행 기기 검증.
2. END_SESSION 멱등 기록이 유효하면 같은 hash의 기존 응답을 재생한다. 기기·세션·버전·최종 누적값을 hash에 포함한다.
3. 버전 검증 후 RUNNING·PAUSED·AUTO_PAUSED·AWAITING_END_CONFIRMATION에서만 ENDED로 변경한다.
4. endReason=USER_ACTION, endedAt=서버 UTC, lastResumedAt=null. RUNNING은 최종 누적값이 저장값 이상이고 현재 실행 구간 기준값 + 서버 경과시간 + 30초 이하인지 pause와 같은 함수로 검증한다. 멈춘 상태는 저장 누적값과 동일해야 한다. 검증된 최종 누적값을 저장한다. 상태와 24시간 멱등 응답 저장은 하나의 트랜잭션이다.
5. ENDED·ABANDONED는 되살리지 않는다. 외부 호출·원문 저장·신규 migration은 필요하지 않다.

## 시간·연동 범위

- 필수 누적값은 0 이상 Long 범위 정수(ms)다. 누락·null·음수·소수·범위 초과·감소·과다 증가는 400 VALIDATION_ERROR로 종료 없이 거절한다.
- 마지막 heartbeat 이후 종료까지의 최종 구간도 종료 요청이 전달한다. Heartbeat 미구현을 이유로 저장된 0으로 종료하지 않는다. 이전 본문 없는 Desktop은 400을 받으며 기록은 유지된다.
- Desktop은 종료 클릭 시 monotonic 누적값을 고정하고, 응답 전까지 복구 가능한 로컬 상태를 유지한다. 같은 의도 재시도에는 최초 본문·키·버전을 그대로 사용한다. 실제 Desktop 수정은 Frontend 담당 범위다.
- Notion end 항목 Request·예외/오류 처리를 브라우저로 수정·저장 확인했다. 기존 BETA-API-HANDOFF의 본문 없음 설명은 사용자 승인으로 대체되며 전달 문서를 참고한다.
- summary·heartbeat·분석·학습 정리 작업은 별도이며 전체 베타 흐름 완료를 주장하지 않는다.

## 완료 조건

- 정상 상태 4종, 최종 상태 거절, 시간·사유·버전 및 활성 조회 204 검증.
- 같은 키 응답 재생·상충 키·타 사용자·실행 기기·Extension·헤더 오류 검증.
- 동시 종료 및 pause/resume 경쟁, PostgreSQL 검증 결과와 미검증 범위 구분.
- Backend build·Checkstyle·REPORT·QA·CONTEXT·NEXT_TASK 갱신.

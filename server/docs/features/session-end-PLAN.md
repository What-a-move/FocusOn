# 세션 종료 API 계획

## 기본 정보·목적

- Issue #163 / `feat/163-session-end`, 2026-11-05, MVP 핵심.
- 사용자 승인: 이슈 생성·구현·검증. 기존 세션·인증·멱등 패턴을 재사용한다.
- 기준: `apps/docs/BETA-API-HANDOFF.md`, Notion 세션 상태 전이 정책 3.3 (브라우저 확인), 기존 #132.

## API 정의

- `POST /api/v1/sessions/{sessionId}/end`, 본문 없음.
- Authorization·Idempotency-Key(UUID)·If-Match-Version(0 이상 정수) 필수. X-Device-Id 불필요.
- 본인 세션을 실행하는 Desktop 토큰만 허용한다. 잘못된 소유자·실행 기기는 404다.
- 성공 200 `{ data: { status, version, accumulatedActiveMs, targetDurationMs, endedAt } }`.
- 인증 401, 헤더 400, 세션 없음 404, 상태·버전·멱등 충돌 409, 기존 공통 오류 형식을 사용한다.

## 데이터·처리 흐름

1. Controller → SessionCommandService → 사용자 행 잠금 → 소유·실행 기기 검증.
2. END_SESSION 멱등 기록이 유효하면 같은 hash의 기존 응답을 재생한다. 기기·세션·버전을 hash에 포함한다.
3. 버전 검증 후 RUNNING·PAUSED·AUTO_PAUSED·AWAITING_END_CONFIRMATION에서만 ENDED로 변경한다.
4. endReason=USER_ACTION, endedAt=서버 UTC, lastResumedAt=null. 누적값은 마지막 서버 검증값을 유지한다. 상태와 24시간 멱등 응답 저장은 하나의 트랜잭션이다.
5. ENDED·ABANDONED는 되살리지 않는다. 외부 호출·원문 저장·신규 migration은 필요하지 않다.

## 시간 계약의 제한·연동 범위

- 본문 없는 종료 요청은 최신 클라이언트 monotonic 누적값을 전달하지 않는다. 확인되지 않은 시간을 서버 벽시계로 추정하지 않는다.
- Heartbeat가 아직 없으므로 RUNNING에서 바로 종료하면 0 또는 마지막 pause 확정값이다. 종료 버튼·활성 슬롯 해제는 검증 가능하지만 정확한 종료 집중 시간과 전체 베타 QA는 heartbeat 연동 뒤 확인해야 한다.
- 최신 프론트 인수 계약은 AUTO_PAUSED 종료를 허용한다. Notion 상태 정책의 예시 목록에는 AUTO_PAUSED가 빠져 있어 최신 사용자 제공 확정 계약을 적용한다. 무기한 세션에 관한 오래된 Notion 표현은 이번 변경에서 적용하지 않는다.
- summary·heartbeat·AI·학습 정리 작업은 별도 이슈이며 Desktop 코드는 변경하지 않는다.

## 완료 조건

- 정상 상태 4종, 최종 상태 거절, 시간·사유·버전 및 활성 조회 204 검증.
- 같은 키 응답 재생·상충 키·타 사용자·실행 기기·Extension·헤더 오류 검증.
- 동시 종료 및 pause/resume 경쟁, PostgreSQL 검증 결과와 미검증 범위 구분.
- Backend build·Checkstyle·REPORT·QA·CONTEXT·NEXT_TASK 갱신.

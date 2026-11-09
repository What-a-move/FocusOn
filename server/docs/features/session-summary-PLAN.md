# 세션 결과 요약 조회 계획

## 기본 정보
- 기능: 세션 결과 요약 GET /api/v1/sessions/{sessionId}/summary
- 담당: Server, MVP 핵심. 관련 Client: Desktop report-contract.cts.
- Issue/Branch: #201 / feat/201-session-summary.

## 목적과 범위
- 종료된 본인 세션의 확정 목표·누적 시간과 연장 이력을 결과 화면에 제공한다.
- 필수 응답: sessionId, goalSnapshot, targetDurationMs, accumulatedActiveMs.
- 저장값으로 검증 가능한 선택 응답: status, startedAt, endedAt, extensionCount, extendedDurationMs.
- 분석 시간·멈춤 횟수·멈춤 시간·개입 횟수는 정확한 집계 근거/기능이 준비될 때 별도 확장한다. 현재 응답에 넣거나 0으로 추정하지 않는다.
- ENDED와 ABANDONED만 결과를 제공한다. 진행 중인 세션은 기존 SESSION_STATE_INVALID 409로 반환한다. 상세 조회와 결과 조회를 구분하기 위한 기준이다.
- 호출은 인증된 Desktop, 타인/잘못된 ID/미존재는 SESSION_NOT_FOUND404. Extension은 기존 SESSION_QUERY_FORBIDDEN403. 기기 헤더 불필요.
- 기존 테이블만 readOnly로 조회한다. 종료된 세션은 상태를 되살리거나 누적 시간을 추정하지 않는다.
- 세션과 연장 이력은 terminal 상태에서 조회하므로 같은 세션의 추가 연장과 경쟁하지 않는다. 동시 조회 시 불변값을 검증한다.
- Notion summary 원본은 접근404. 로컬 확정 API_CONTRACT와 Desktop 파서를 대조하며 원본 대조 완료로 주장하지 않는다.

## 검증·완료 조건
- 정상2상태, 타인/미존재/잘못된ID, 미인증, Extension, 진행 상태 거절, 연장0/다수 집계 검증.
- 실제 Google 로그인 HTTP, PostgreSQL 동시 조회/데이터 불변, build·Checkstyle.
- PLAN·ERROR·REPORT·CONTEXT·NEXT_TASK 갱신. 학습 정리 생성·일간/달력·Frontend는 제외.

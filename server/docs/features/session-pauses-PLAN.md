# Backend 기능 기획서 — 세션 일시정지 구간 기록과 조회

## 기본 정보

- 기능명: 세션 일시정지 구간(session_pauses)
- 기능 ID: session-pauses
- 작성자: 박진욱
- 작성일: 2026-11-26
- 우선순위: MVP - 기본
- 관련 Issue: #317
- 예정 Branch: `feat/317-server-session-pauses`
- 관련 문서: Notion API 명세 `GET /api/v1/sessions/{sessionId}/pauses`, ERD `session_pauses`(재설계 1차)

## 기능 목적

- 해결할 문제: 서버에 일시정지 이력이 없어 집중 리포트 가로축(일시정지를 뺀 경과 시간)을 그릴 수 없다.
- Backend가 담당할 역할: 일시정지·재개 때 구간을 기록하고 세션별로 돌려준다.

## API 정의

- `GET /api/v1/sessions/{sessionId}/pauses` → `{ data: [{ pausedAt, resumedAt | null, pauseReason }] }`, `pausedAt` 오름차순. 도입 전 세션은 빈 배열. 다른 사용자·없는 세션 404 `SESSION_NOT_FOUND`.

## 데이터 설계

- V33 `session_pauses`(id, session_id FK CASCADE, paused_at, resumed_at NULL, pause_reason), `resumed_at >= paused_at`, 세션당 열린 구간 하나(부분 유니크 인덱스).
- 계정 삭제 정리(`DeletedAccountPurge`)에 포함.

## 처리 흐름

1. `POST /pause`(사용자·자동 사유)와 권한 유실 로컬 이벤트의 자동 일시정지가 같은 트랜잭션에서 구간을 연다. 재개 기록 없이 남은 구간이 있으면 먼저 닫는다.
2. `POST /resume`이 열린 구간을 닫는다. 재개 없이 끝나면 `resumed_at`은 NULL로 남는다.
3. 멱등 재전송은 기존 응답만 돌려주므로 구간이 중복되지 않는다.

## 범위 밖

- 보관 기간(세션 종료 후 1년) 자동 정리, 리포트 그래프 계산.

## 완료 조건

- [x] 일시정지·자동 일시정지·재개·종료 흐름이 구간으로 남는다.
- [x] 본인만 조회, 순서·열린 구간 null 확인.
- [x] 마이그레이션·테스트·문서 갱신.

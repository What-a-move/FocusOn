# Extension 연결 개발 오류 기록

- Issue #58 / 2026-10-28 / Java25·Boot4.1.1·H2/PG17.11

## EP-001 — 만료 사유 덮어쓰기 (보통, 해결)

- 재현: 코드 발급 후5분 경과→worker 실행 전 재발급·logout·같은 설치 재로그인.
- 기대: EXPIRED, terminal_at=expires_at, LINK_002_EXPIRED.
- 원인: ACTIVE만 검사한 취소 일괄 갱신.
- 해결: 해당 Desktop 만료 행 선처리 후 미래 expires_at인 ACTIVE만 취소.
- 증거: 독립 재리뷰 해결 확인, 세 경로 회귀 테스트 및 실제 PostgreSQL14개 통과.
- 관련: extension-pairing-code-review.md.

## EP-002 — Swagger 경로 수 고정 검사 (낮음, 해결)

- 새 API3개로 paths가4개에서7개가 되자 SwaggerApiTests의 고정 개수 assertion만 실패했다.
- 전체 엔드포인트 수 대신 공개 claim의 빈 security 배열·코드 발급 경로 등 실제 보안 계약을 검사하도록 변경했다.
- 전체 JUnit76개 통과. 스타일 검사 중 줄 길이·변수 거리와 괄호 오류도 수정하고 build를 재실행했다.

인증값·코드 원문·설치 ID 원문은 기록하지 않는다. 미해결 실행 오류 없음.

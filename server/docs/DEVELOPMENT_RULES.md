# Backend 개발 불변 규칙

규칙이 바뀌면 이 문서와 `DECISION_RECORD.md`를 함께 갱신한다.

## BE-001 계약 원본

- 엔드포인트별 최종 계약은 Notion `FocusOn API 명세서`를 따른다.
- 공통 오류와 세션 전이는 Notion `공통 Error Code`, `세션 상태 정책`을 따른다.
- 로컬 요약은 루트 `docs/API_CONTRACT.md`에서 관리한다.
- 폐기된 예전 Notion `API 명세` 페이지는 사용하지 않는다.

## BE-002 응답과 오류

- 성공 응답은 `{ "data": ... }` 형식이며 `204`는 본문이 없다.
- 실패 응답은 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details`를 최상위에 둔다.
- 클라이언트는 `code`로 분기한다.
- 새 오류 코드는 Notion 공통 정책에 먼저 정의하고 구현한다.

## BE-003 계층 책임

- Controller: 요청·응답 변환과 입력 검증
- Service: 비즈니스 로직과 트랜잭션 경계
- Repository: 데이터 접근

Controller에 상태 전이와 정책 판단을 작성하지 않는다.

## BE-004 인증과 동시성

- 인증 API는 `Authorization: Bearer {token}`을 사용한다.
- Desktop·Extension 요청은 명세에 따라 `X-Device-Id`를 함께 사용한다.
- 세션 생성·일시정지·재개·연장·종료는 `Idempotency-Key`를 사용한다.
- 기존 세션 상태 변경은 `If-Match-Version`도 검증한다.
- Heartbeat는 두 동시성 헤더를 사용하지 않는다.

## BE-005 세션 시간

- 서버가 세션 상태와 기준 시간을 관리한다.
- 활성 시간은 `accumulatedActiveMs`, 목표 시간은 `targetDurationMs`로 표현한다.
- Heartbeat는 10초 간격, 무신호 60초를 `ABANDONED` 후보 기준으로 사용한다.
- 한 사용자에게 활성 세션은 하나만 허용한다.

## BE-006 재시도와 속도 제한

- 상태 명령은 동일한 idempotency key가 있을 때만 재시도한다.
- 분석 요청은 `clientRunId`로 중복을 막는다.
- `429`에는 `Retry-After`를 반환한다.
- 수치는 `docs/API_CONTRACT.md`의 표와 Notion 계약을 따른다.

## BE-007 개인정보와 로그

- `docs/DATA_PRIVACY.md`를 따른다.
- 토큰, API key, 연결 코드, 원본 화면·카메라 데이터, 원문 콘텐츠를 로그·오류 details·개발 오류 문서에 남기지 않는다.
- 로그에는 requestId, 안전한 리소스 식별자, 기능명, 오류 코드 정도만 남긴다.

## 확인표

| 규칙 | PR에서 확인할 내용 |
| --- | --- |
| BE-001 | Notion 최종 명세와 경로·필드·권한 일치 |
| BE-002 | 성공·오류 형식과 HTTP 상태 일치 |
| BE-003 | 계층 책임과 트랜잭션 경계 |
| BE-004 | 인증·기기·idempotency·version 헤더 |
| BE-005 | 세션 전이와 시간 누적 |
| BE-006 | 재시도 가능 조건과 rate limit |
| BE-007 | 민감 정보 비노출 |

# Notion API 명세서 운영 규칙

대상은 Notion **FocusOn API 명세서** 데이터베이스다. 폐기된 예전 `API 명세` 페이지를 수정하거나 구현 기준으로 사용하지 않는다.

## 원본 원칙

- Notion 명세가 엔드포인트의 경로, Method, Request, Response, 권한, 상태, 예외의 원본이다.
- 공통 오류는 `공통 Error Code`, 상태 전이는 `세션 상태 정책`을 참조한다.
- 계약을 바꾸는 작업은 코드보다 Notion을 먼저 갱신한다.
- 구현과 검증이 끝나면 `docs/API_CONTRACT.md`와 관련 영역 문서를 함께 갱신한다.

## 작성 기준

- 제목에는 `METHOD /api/v1/...`가 드러나게 작성한다.
- Request와 Response는 실제 필드명, 타입, 필수 여부, nullable 여부, enum, 단위를 적는다.
- 성공 예시는 `{ "data": ... }`, `204`는 빈 본문을 사용한다.
- 오류는 최상위 `code`를 사용하는 공통 오류 형식을 따른다.
- 인증 필요 여부, `X-Device-Id`, `Idempotency-Key`, `If-Match-Version` 적용 여부를 구분한다.
- 상태 명령과 Heartbeat를 혼동하지 않는다. Heartbeat에는 idempotency/version 헤더를 붙이지 않는다.
- MVP/P1, 구현 상태, 소비 클라이언트를 속성에 맞게 표시한다.
- 보류나 미정 필드는 구현자가 추측하지 않고 명세에서 먼저 확정한다.

## 변경 확인

1. 같은 도메인의 기존 행과 이름·enum·상태 코드를 비교한다.
2. 공통 정책 링크와 예외 코드가 실제로 존재하는지 확인한다.
3. Desktop·Extension·AI 소비자 영향을 기록한다.
4. 계약 테스트 또는 API 테스트로 구현을 확인한다.
5. PR에서 Notion 행과 로컬 문서 링크를 남긴다.

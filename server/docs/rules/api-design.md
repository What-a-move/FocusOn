# Backend API 설계 원칙

## 1. 원본 계약 준수

- 새 경로를 임의로 만들지 않고 Notion `FocusOn API 명세서`를 따른다.
- 명세에 없는 필드·enum·오류가 필요하면 Notion 계약을 먼저 변경한다.
- `/api/v1`, lower camel case, 문자열 식별자, UTC ISO 8601을 사용한다.

## 2. 하나의 사용 사례

- 엔드포인트 하나는 하나의 명확한 작업을 수행한다.
- 조회와 상태 변경을 분리한다.
- 전체 교체는 PUT, 일부 변경은 PATCH를 사용하되 최종 Method는 Notion 행을 따른다.

## 3. 명확한 성공과 오류

- 성공은 `{ "data": ... }`, `204`는 빈 본문이다.
- 오류는 최상위 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details` 형식이다.
- HTTP 상태와 error code가 같은 원인을 일관되게 표현해야 한다.
- 분석 상태값을 오류 코드로 바꾸지 않는다.

## 4. 인증·동시성·재시도

- 사용자와 호출 기기를 각각 검증한다.
- 상태 명령은 idempotency key와 version으로 중복·충돌을 제어한다.
- Heartbeat는 누적 시간의 단조 증가와 관찰 시각을 검증한다.
- 재시도 가능한 응답에는 계약에 맞는 `retryable`과 대기 시간을 제공한다.

## 5. 확장성과 개인정보

- 목록 API는 page size, cursor, 정렬 기준을 명세에 정의한다.
- rate limit 기준에 필요한 식별자를 저장·조회할 수 있어야 한다.
- 원본 화면·영상, token, 연결 코드, URL query 등 민감 데이터는 저장·로그하지 않는다.
- 응답 필드 제거나 의미 변경은 버전 변경 또는 모든 소비자 동시 변경 계획이 있어야 한다.

## 관련 문서

- `docs/API_CONTRACT.md`
- `server/docs/DEVELOPMENT_RULES.md`
- `server/docs/rules/notion-api-spec.md`

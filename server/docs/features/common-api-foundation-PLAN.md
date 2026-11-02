# 공통 API 기반 구현 계획

- 작성일: 2026-10-19
- 우선순위: MVP 기본
- 관련 Issue: [#30](https://github.com/What-a-move/FocusOn/issues/30)
- 기준: Notion `FocusOn API 명세서`, `공통 Error Code 정책`, `docs/API_CONTRACT.md`

## 목적과 범위

로그인·목표·세션 API가 같은 성공·오류 형식을 사용하도록 Backend 공통 코드를 마련한다. 새 공개 엔드포인트나 DB 테이블은 만들지 않는다.

## 계약

- 본문이 있는 성공 응답은 `{ "data": ... }`다. `204 No Content`에는 본문을 넣지 않는다.
- 오류 응답은 최상위 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details`를 모두 포함한다.
- 요청마다 서버가 `requestId`를 생성한다. 클라이언트가 보낸 식별자를 신뢰하지 않는다.
- 검증 실패와 읽을 수 없는 JSON은 `400 VALIDATION_ERROR`, 예상하지 못한 오류는 `500 INTERNAL_ERROR`로 처리한다.
- `details`와 로그에 요청 본문, 토큰, 연결 코드, URL 전문 등 민감값을 넣지 않는다.
- 도메인 오류는 이후 API에서 확정된 코드와 HTTP 상태를 명시해 공통 예외 처리기로 전달한다. 현재 계약에 없는 코드는 추가하지 않는다.
- `429 TOO_MANY_REQUESTS`를 반환할 때는 `Retry-After` 헤더와 같은 초 단위의 `retryAfterSeconds`를 설정한다. 실제 rate limiter는 후속 작업이다.

## 구현 흐름

1. `ApiResponse<T>`와 `ApiErrorResponse`를 정의한다.
2. `RequestIdFilter`가 `/api/v1/**` 요청에 서버 생성 ID를 부여한다.
3. 공통 예외와 `@RestControllerAdvice`로 검증·JSON·예상 밖 오류를 안전하게 변환한다.
4. 향후 인증 필터가 MVC 바깥에서 실패할 때 같은 오류 응답을 쓰도록 확장 지점을 문서화한다.

## 제외 범위

- Google 로그인·JWT 인증 필터, DB 모델·PostgreSQL migration
- 실제 rate limit, 도메인별 오류와 엔드포인트
- Spring Actuator·Swagger의 자체 응답 형식 변경

## 완료 조건

- [x] 공통 성공·오류 모델과 requestId 흐름이 있다.
- [x] 검증·JSON 오류와 예상 밖 오류가 계약 형식으로 변환된다.
- [x] Java 컴파일과 Checkstyle을 통과한다.
- [x] 구현 경계와 후속 작업을 Backend 문서에 반영한다.

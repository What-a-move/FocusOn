# 공통 API 기반 구현 결과

- 작성일: 2026-10-19
- 관련 Issue: [#30](https://github.com/What-a-move/FocusOn/issues/30)
- 관련 PR: [#31](https://github.com/What-a-move/FocusOn/pull/31)
- Branch: `feat/30-server-common-api`
- 기획서: [common-api-foundation-PLAN.md](common-api-foundation-PLAN.md)

## 구현

- 본문이 있는 성공 응답용 `ApiResponse<T>`를 추가했다. `204`는 Controller에서 본문 없이 반환하는 계약으로 유지한다.
- 공통 오류 응답의 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details`를 정의했다.
- `/api/v1` 요청마다 서버가 `requestId`를 생성해 request attribute와 MDC에 넣고 요청이 끝나면 MDC에서 제거한다.
- 명시적 API 예외, DTO·JSON·요청 바인딩 오류, 예상 밖의 Controller 오류를 각각 명세 상태로 변환한다.
- 429 예외에는 양수 초 단위 대기 시간을 요구하고 응답 본문과 `Retry-After` 헤더에 같은 값을 쓴다.
- 공개 오류 응답과 로그에 원본 입력·예외 메시지·stack trace를 포함하지 않는다.

## 검증

- `./gradlew build --no-daemon`: 성공 (기존 테스트와 새 공통 API HTTP 테스트 포함)
- 독립 MockMvc 테스트에서 성공 응답, Bean Validation·잘못된 JSON의 400, 429의 `Retry-After`, 예상 밖 오류의 500과 내부 메시지 비노출, 허용하지 않은 메서드의 405를 확인했다.
- 실제 인증 필터, DB, rate limiter와 연결된 통합 동작은 구현·실행 검증 범위 밖이다.
- 관련 오류 기록: [common-api-foundation-ERROR.md](common-api-foundation-ERROR.md)

## 후속 연결

- 도메인 Controller가 성공 응답을 `ApiResponse.of(data)`로 감싸고, 본문 없는 성공은 `204`로 반환해야 한다.
- Spring Security 필터에서 발생하는 인증·인가 오류는 Controller advice를 지나지 않으므로 인증 기능 구현 시 같은 오류 모델로 별도 연결해야 한다.
- 아직 Controller가 없는 경로의 404, 실제 rate limiting, 각 도메인 오류 코드는 후속 API 구현 시 계약에 맞춰 연결한다.
- 다음 기능은 PostgreSQL Flyway migration과 Java Entity/Repository를 Notion ERD에 맞춰 구현한다.

## 문서 상태

- `CONTEXT.md`, `NEXT_TASK.md` 갱신
- API 계약·결정 기록의 제품 정책 변경 없음

# 서버 요청 추적 구현 결과

- Issue #116; `feat/#116-server-tracing`.
- 기존 서버 `requestId`를 유지하고 API 요청의 유효 `X-Trace-Id`를 별도로 연결한다. 유효하지 않거나 여러 개면 새 값을 생성하며, 응답·MDC·요청 로그에 반영한다.
- 공용 로거는 고정 이벤트와 안전한 필드만 내보내며, query·헤더·본문과 미허용 경로 구성 요소를 기록하지 않는다. 4xx/5xx 결과는 실패 단계와 알려진 오류 코드·분류를 남긴다.
- 검증 실패는 안전한 필드명과 알려진 타입만 기록한다. 예외 메시지와 stack trace는 토큰이나 원문을 포함할 수 있어 이 로거에 기록하지 않는다.
- Spring Boot Logstash 출력의 시각 필드는 `ts`, 공통 module은 `backend`로 맞췄다. 상세 및 미검증 범위는 `server-tracing-QA.md`를 따른다.
- 세션·분석 API 및 AI 호출 구현은 이 PR 범위 밖이다. 최신 dev에는 세션 시작·활성 조회와 일시정지·재개가 구현돼 있다.

## PR #162 후속 검토 (2026-11-05)

- CodeRabbit 지적 1건을 확인했다. 프레임워크 404·405 응답의 http.out에 고정 오류 코드 NOT_FOUND·METHOD_NOT_ALLOWED를 기록하도록 수정하고 실제 HTTP 로그 테스트로 검증했다.
- 수정 후 로컬 전체 build·Checkstyle 통과. 수정 전 PR의 Backend CI 및 PostgreSQL nickname concurrency는 통과했으며, 새 커밋의 CI는 별도 확인한다.
- 실제 앱 연동 및 PostgreSQL 기반 전체 로깅 QA는 미실행이다. CodeRabbit의 docstring 비율은 경고이며 기능 오류와 구분한다.

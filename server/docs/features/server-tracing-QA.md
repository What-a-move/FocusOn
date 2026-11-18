# 서버 요청 추적 QA

## #182 분석 추적 보완 (2026-11-18)

- 백엔드 전체 빌드와 Checkstyle을 통과했다. 테스트 347개 중 330개 통과, 실패·오류 0개, 17개 생략이다.
- 내장 서버 임의 포트로 `X-Trace-Id = clientRunId`를 보내고 루프백 AI에서 `RELATED / 0.9`를 반환했다. AI 호출 헤더와 공개 응답 헤더에서 같은 ID를 확인했다.
- 수집된 JSON에서 같은 traceId로 `http.in → gate.rule.pass → ai.call.start → ai.call.ok → store.write.ok → http.out`이 이어졌다. HTTP 200·소요시간·별도 requestId를 확인했다.
- 음수 `sequence`를 보낸 공개 HTTP 요청에서 `http.in → validate.fail → http.out`과 400을 확인했다. 검증 로그에 `sequence`와 `BigDecimal`이 기록되며 거부된 값은 콘솔에서 제외됐다. 인증 실패 401과 없는 경로 404도 각 요청의 traceId로 조회했다.
- 루프백 AI 503에서 같은 traceId로 `ai.call.fail`의 503과 공개 API의 200을 확인했다. API 판정은 `UNCERTAIN / ANALYSIS_UNAVAILABLE`이다. 8KiB 초과 503도 수신 상태만 기록했다.
- 요청 본문·제목·쿼리·토큰·AI 응답 원문은 테스트 로그에서 제외됐다. 실제 Desktop·AI 서버 전체 연동 검증은 남아 있다.

## 초기 서버 추적 (#116)

- Issue #116, 기준 dev `8b68916`.
- `./gradlew build`: 전체 테스트 113개, 실패·오류 0개, 1개 건너뜀. 건너뛴 항목은 기존 PostgreSQL 닉네임 동시성 테스트이며 H2 테스트 환경의 조건에 따른 것이다. Checkstyle 통과.
- 20개 동시 요청에서 각 traceId가 서로 섞이지 않고, 요청 종료 후 MDC가 복원되는지 확인했다.
- 유효·누락·잘못된·중복 `X-Trace-Id`, 별도의 서버 `requestId`, 오류 시 응답 헤더와 MDC 복원을 확인했다.
- 실제 내장 서버에서 404 요청의 JSON `http.in`/`http.out`을 읽어 `ts`, `module`, `component`, `event`, traceId, requestId, duration과 응답 상태가 맞는지 확인했다. 테스트용 경로 변수·query·Authorization은 로그에 없었다.
- DTO 검증 실패에서 필드 이름·예상 타입을 기록하고 거부된 값을 기록하지 않는 테스트를 확인했다. 미구현 API·직접 `/error`·인증 실패 회귀 테스트도 실행했다.
- Spring DB 예외를 `database` 단계로 분류하고 예외 메시지의 민감 문자열을 기록하지 않는 테스트를 수행했다.
- 실제 Desktop·Extension → 서버 왕복 및 PostgreSQL 기반 실행, AI 연동과 가짜 판정기는 이번 검증 범위가 아니다.
- Notion 정본은 연결 도구 404 후 브라우저로 3.2~3.4 및 4.3을 확인했다. user 제공 정규식은 정본의 UUID 형태 설명보다 넓으므로 두 표현의 정합성은 후속 확인이 필요하다.
- 독립 리뷰와 GitHub CI는 아직 실행하지 않았다. 프레임워크 자체 로그는 Spring Boot 기본 Logstash 형식을 사용하며, 애플리케이션 공용 로거의 event 필드를 갖는다고 주장하지 않는다.

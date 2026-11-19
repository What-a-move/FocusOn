# Backend 결과 리포트 — 학습 정리 생성 파이프라인 (#288)

## 변경 요약

- V23: `learning_summaries` 대기열 컬럼·상태별 CHECK·부분 인덱스, `learning_review_evidences` 테이블.
- 종료 커밋 뒤 `SessionEnded` 이벤트 → `LearningSummaryQueue`가 QUEUED 행 생성. `SessionCommandService.end()`에는 이벤트 발행 한 줄만 추가했다(세션 명령 담당 영역이라 최소 변경).
- `LearningSummaryWorker`(5초 주기): 누락 행 복구 → 마지막 시도 lease 만료 FAILED → 실행 시각이 된 작업 처리.
- `LearningSummaryGenerator`: 조건부 UPDATE 점유, 입력 고정, 근거 2개 미만 INSUFFICIENT_DATA, AI 호출, 재시도 예약, fencing 저장.
- `LearningSummaryAiClient`: 고정 origin, 서버 전용 키, `X-Trace-Id`, 20초 전체 deadline, 16KB 응답 제한.
- `LearningSummaryOutputValidator`: Schema·개념 1~5개·1,200자·민감값 검사.
- 설정: `AI_LEARNING_SUMMARY_URL`(application.properties, docker-compose, .env.example). 키는 기존 `FOCUSON_AI_API_KEY`.
- CI: PostgreSQL 잡에 `*LearningSummaryPipelineTests` 추가.

## 테스트 결과 (2026-11-19)

| 구분 | 환경 | 결과 |
| --- | --- | --- |
| 전체 build + Checkstyle | H2, JDK 21 + 로컬 shim | 411개 중 실패 1, 생략 18(PostgreSQL 전용), Checkstyle 경고 0. 실패 1건은 shim 때문에 생기는 `AnalysisAiClientTests` 크기 제한 테스트(ERROR 문서 참고) |
| CI PostgreSQL 목록 전체 | PostgreSQL 16, Flyway + validate | 218개 실패 0 |
| 학습 정리 신규 테스트 | 위 두 환경 | 검증기 32, AI 클라이언트 18, 파이프라인 11, migration 2 모두 통과 |
| 동시성 반복 | PostgreSQL, 8 Worker 동시 점유 + lease 탈취 | 10/10회 통과(점유 1, AI 호출 1, 늦은 결과 미저장) |

## 실서버 E2E (실제 포트·PostgreSQL·스케줄러 1초·별도 프로세스 가짜 AI)

Google 교환과 온보딩 판정만 테스트 대역이다. 나머지(JWT 서명·Token Family·Flyway·스케줄러·AI HTTP)는 실제로 실행했다. 일회성 QA 코드는 커밋하지 않았다.

| 시나리오 | 결과 |
| --- | --- |
| A 근거 없음 | `POST /end` 200 → 즉시 `GET learning-summary` 200 `QUEUED` → 30초 뒤 200 `INSUFFICIENT_DATA`, AI 요청 0회 |
| B 근거 2개 | AI 요청 1회(경로·Bearer·Trace·필드 계약 일치) → 200 `READY` 본문 |
| C AI 503 | 1회차 뒤 대기 59.97초, 2회차 뒤 4분 59.98초, 3회차 뒤 `FAILED`. 세션 status·누적시간·종료시각 불변, `GET summary` 200 |
| D AI 25초 지연 | 20.086초에 중단, QUEUED + 1분 뒤 재시도 예약 |
| E 민감값 출력 | `FAILED`, overview·keyConcepts 미저장 |
| 로그 검사 | 개념·URL·키·Bearer·토큰·목표 문구 0건. `ai.call.start/ok/fail`, `store.write.ok/fail` 이벤트와 traceId만 남음 |

## 남은 제한

- 실제 Google 로그인 200은 확인하지 못했다. 이 환경에는 팀 Google Client 설정(`.env`)과 사용자 Google 계정 브라우저 세션이 없다. 로컬 Mac에서 `SWAGGER_TESTING.md` 순서로 확인해야 한다.
- 근거를 만드는 쪽(분석 응답의 `reviewEvidence`)이 AI 합의 전이라 실제 환경에서는 모든 정리가 `INSUFFICIENT_DATA`로 끝난다.
- AI의 `/internal/v1/learning-summaries`가 미구현이라 READY는 가짜 AI로만 확인했다.
- `learning_review_evidences` 90일 보관 정리(DATA_PRIVACY)는 기존 보관 cleanup worker 작업에서 다룬다.
- Java 25 컴파일·`limiting` 크기 제한 테스트는 PR CI에서 확인해야 한다.
- Notion 리포트 설계서 원문은 접근하지 못해 대조하지 못했다.

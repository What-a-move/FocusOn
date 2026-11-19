# Backend 개발 중 오류 보고서 — 학습 정리 생성 파이프라인

## 오류 1. H2 테스트에서 같은 근거가 중복 저장됨

- 기능명: learning-summary-pipeline / 발생 일시: 2026-11-19 / 심각도: 보통 / 상태: 해결 / 관련 Issue: #288
- 실행 환경: Linux, Java 21(로컬 대체), Spring Boot 4.1.1, H2(PostgreSQL 모드, `ddl-auto=create-drop`), test 프로필
- 재현: `LearningSummaryPipelineTests.fewerThanTwoDistinctEvidencesEndsInsufficientWithoutModelCall`에서 같은 `evidence_hash`의 근거를 두 번 insert한다.
- 기대 결과: 두 번째 insert가 unique 위반으로 실패한다(서로 다른 근거 1개).
- 실제 결과: 두 번째 insert가 성공했다. `expected: <false> but was: <true>`.
- 원인: H2는 Flyway 대신 엔티티로 테이블을 만든다. `(session_id, evidence_hash)` UNIQUE를 V23 migration에만 두고 `LearningReviewEvidenceEntity`에는 선언하지 않았다. PostgreSQL에서는 막히고 H2에서만 통과하는 차이였다.
- 해결: 엔티티 `@Table(uniqueConstraints = @UniqueConstraint(name = "uq_learning_review_evidence", ...))`를 migration과 같은 이름으로 선언했다.
- 재발 방지: DB 제약에 의존하는 동작은 엔티티에도 제약을 선언한다.

## 오류 2. 기존 V20 보존 테스트가 PostgreSQL에서 실패

- 발생 일시: 2026-11-19 / 심각도: 보통 / 상태: 해결
- 실행 환경: PostgreSQL 16(로컬, CI는 17), Flyway enabled, `ddl-auto=validate`
- 재현: `LearningSummaryMigrationTests.preservesLegacyReadyAndRejectsBlankNewWrites`를 V23 추가 뒤 실행한다.
- 기대 결과: V20 적용 전후 `row_to_json` 결과가 같다.
- 실제 결과: 다르다. 적용 후 JSON에 V23이 추가한 `input_closes_at` 등 컬럼이 포함됐다.
- 원인: 테스트가 두 번째 migrate에 target 없이 최신 버전(V23)까지 적용했다. V20의 데이터 보존이라는 테스트 의도와 맞지 않았다.
- 해결: 두 번째 migrate를 `target("20")`으로 고정하고, V23 검증은 별도 테스트(`pipelineMigrationBackfillsPendingRowsAndGuardsEvidence`)로 분리했다.

## 환경 제약 (오류 아님, 기록용)

- 이 작업 환경에는 JDK 21만 있고 JDK 25 다운로드 호스트가 네트워크 정책으로 막혀 있다. 기존 코드가 JDK 25 API(`HttpResponse.BodySubscribers.limiting`)를 써서 그대로는 컴파일되지 않는다.
- 로컬 검증 때만 Gradle init 스크립트로 toolchain을 21로 바꾸고 `limiting(...)` 3곳(분석·학습 정리 클라이언트, 분석 live 테스트)을 `ofByteArray()`로 임시 치환했다. 커밋 전 원복했고 `git diff`로 확인했다.
- 이 때문에 로컬 전체 build에서는 `AnalysisAiClientTests.oversizedValidDecisionIsRejectedByByteLimit` 1건이 실패한다(치환으로 크기 제한이 사라짐). 실제 코드 문제가 아니며 Java 25 CI에서 확인한다.

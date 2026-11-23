# 결과 리포트 — 화면 판정 matchStatus 저장과 응답 (#319)

## 변경 요약

- `AnalysisDtos.MatchStatus`(`MATCHED`/`FAR`/`NOT_ENOUGH`) 추가, 분석 응답 `Response`에 `matchStatus`.
- `AnalysisAiClient.normalize`: 확정 RELATED일 때만 AI의 `matchStatus`를 읽는다. 없거나 허용 밖 값이면 판정은 유지하고 null.
- `AnalysisDecision`: 확정 RELATED가 아니면 `matchStatus`를 비우는 규칙을 레코드에 둬서 어느 경로로 만들어도 지켜진다.
- `AnalysisRunEntity`·V28: `analysis_runs.match_status` VARCHAR(16) NULL + CHECK(값 목록, RELATED·COMPLETED일 때만).
- `docs/API_CONTRACT.md`에 필드와 규칙 기록. POST 응답과 GET 이력에 함께 담긴다.

## 테스트

- 단위(`AnalysisAiClientTests.matchStatusIsKeptOnlyForConfirmedRelated`): 세 값 유지, 소문자·모르는 값·숫자·null·객체는 판정 유지 + null, 필드 없음 null, UNRELATED·UNCERTAIN·신뢰도 부족으로 낮춘 RELATED는 null.
- 통합(`AnalysisApiTests`): RELATED + FAR가 POST 응답·DB·GET 이력에 같게 남음, UNRELATED에 붙은 값은 저장하지 않음.
- `AnalysisMigrationTests.noRawColumns` 허용 컬럼에 `match_status` 추가(고정 목록 값이라 원문 아님).
- H2 전체 426개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과.
- 임시 PostgreSQL 17(Flyway V28 + validate)에서 CI와 같은 범위 240개 통과(1개 생략).

## 남은 제한

- dev의 AI는 아직 `matchStatus`를 보내지 않아 실제 응답은 당분간 null이다. AI 배포 뒤 바로 채워진다.
- DB CHECK 위반을 직접 넣어 보는 마이그레이션 테스트는 없다(엔티티·레코드 규칙이 먼저 막는다).
- `GOAL_ADD_PROMPT`·할 일별 시간 집계는 범위 밖이다.

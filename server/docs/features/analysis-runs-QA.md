# 화면 분석 요청 QA (#171)

검증일: 2026-11-08. 개발 DB5432·기존8080 서버 대신 임시 PostgreSQL17(55471)과 random-port HTTP/stub을 사용했다. 실제 Google/Chrome/Desktop/AI 모델 성공으로 해석하지 않는다.

## 결과

| 검사 | 결과 |
| --- | --- |
| Backend build·Checkstyle·H2 전체 |160개, 실패0/오류0/생략6|
| PostgreSQL CI와 같은 선택 |34개 전부 통과|
| PG 분석 API |15개 전부 통과|
| PG 분석 migration |9개 전부 통과|
| PG 기존 확장 migration |9개 전부 통과|
| PG 닉네임 동시성 |1개 통과|
| 별도 컨텍스트 정적 리뷰·timeout 수정 재리뷰 |확정 P1/P2 없음|

PR #172 리뷰 반영 후 기본 build의 생략6개는 PostgreSQL 전용 조건에 따른 것이며 전체160개가 모두 실행됐다는 뜻이 아니다. 별도 PG34개는 이 조건을 실제 PostgreSQL에서 검사한다. 초기 head6c54dc8의 원격 CI2개·CodeRabbit은 성공했다. 이번 리뷰 수정 커밋의 원격 결과는 push 후 확인한다.

## 검증한 동작

- 실제 내장 HTTP 서버:200 data의 analysisRunId/analysisStatus/relation/confidence/reasonCode/observedAt/policyVersion, X-Trace-Id 왕복. AI는 이 테스트에서 mock이다.
- runner Desktop과 연결 Extension 허용, 다른 사용자/같은 계정의 다른 Desktop 거절, 미인증/기기 헤더 누락/다른 기기 위장 경계.
- guard+본문 거절, 서버 제외 도메인·동의 철회·민감 토큰 차단 후 AI 미호출, 원문 없는 결과 이력.
- UUID/지원 source/host URL 혼입/2,000자 초과/소수·0·BIGINT 초과 sequence 거절.
- 중복409, 같은 clientRunId 동시 제출200/409, 세션+기기9번째 요청429.
- AI fallback은 HTTP200 UNCERTAIN/ANALYSIS_UNAVAILABLE, metadata UNRELATED는 시간 계산용 effectiveRelation에서 UNCERTAIN.
- AI 처리 중 다른 source의 최신 EXCLUDED를 먼저 저장해도 이전 AI 완료는 자기 행만 갱신. 최신 조회는 EXCLUDED 유지.
- AI 완료 전 pause·동의 철회·Extension family 폐기를 재검증. heartbeat로 version만 증가하면 같은 RUNNING 구간의 정상 결과는 유지.
- 내부 adapter를 실제 loopback stub으로 검사: 서비스 Bearer·같은 traceId·서버 목표/정책/context·정규화 host·일회성 본문 전달. 0.75 경계/저신뢰·베타 suffix/null 예외·receipt/잘못된 schema 거절.
- 외부 응답9KB는8KB 제한에서 차단, 헤더 이후 본문 stall은 전체5초 deadline에서 fallback. 합성 테스트 약5.1초(운영 응답시간 지표가 아님).
- 실제 Flyway V12→V13: 기존 세션 accumulatedActiveMs1234 보존, clientRunId unique, source/sequence/confidence/relation/guard CHECK, session FK, 원문 컬럼 부재, source를 가로지르는 observedAt 최신 정렬. 시나리오별 임시 schema를 삭제했다.

## 실행

```bash
./server/gradlew -p server build
```

임시 PostgreSQL 환경변수 URL/driver/username/password, ddl-auto=validate, flyway=true로 다음 CI 선택을 실행했다. 사용자 .env는 출력하거나 덮어쓰지 않았다.

```bash
./server/gradlew -p server test \
  --tests '*UserConfigurationApiTests.concurrentNicknameConflictsOnPostgres' \
  --tests '*ExtensionMigrationTests' \
  --tests '*AnalysisApiTests' \
  --tests '*AnalysisMigrationTests' --rerun-tasks
```

## 미검증·후속

- 현재 dev AI의 정식 경로는 판정 대신 RECEIVED다. #154 반영 후 실제 판정과 OCR/DOM·traceId 통합을 검증해야 한다.
- 실제 Desktop·Extension/권한 철회/포키 알림/리포트 시연은 미완료다. 개입·heartbeat Policy Engine·90일 보관 삭제 작업은 후속이다.
- 입력의 observedAt 최신성은 클라이언트 관측 계약에 따른다. 실제 화면 navigation을 서버가 직접 관찰했다고 간주하지 않는다. 같은 observedAt은 서버 run ID가 나중인 순으로 정렬한다.
- 단일 인스턴스 메모리 rate limit이다. 다중 서버 공유 한도는 후속이다.
- 최초 검증은 요청 cancel 호출만 검사했으며 PR #172 보완에서 실제 loopback 연결의 EOF/reset을 추가 관찰한다. 운영 연결 pool·부하 자원 계측까지 수행한 것은 아니다. 현재 sensitive 탐지는 추가 방어이며 모든 개인정보를 탐지한다고 주장하지 않는다.

## PR #172 리뷰 보완 재검증

- Major1건: host 없는 CHROME_DOM 본문을400으로 차단한다. metadata 누락/host null/host 빈 값3종에서 저장 증가0·AI 호출0. 보호 상태만 보내는 요청은 정상 처리한다.
- 테스트 지적2건: 정상 판정 JSON+9,000공백으로 크기 제한을 검증하고, raw loopback socket에서 cleanup 전 EOF/reset으로 실제 요청 취소를 확인한다.
- 회귀 검증력 확인: byte limit과 pending.cancel을 로컬에서 임시 제거했을 때 두 테스트가 각각 실패했다. finally로 현재 구현을 복원하고 전체 build/Checkstyle160개(실패0/생략6)를 통과했다.
- 격리 리뷰: 이번 수정 diff·PLAN으로 별도 에이전트가 정적 재검토했고 P1/P2 없음. 실제 AI·앱 QA를 수행했다는 의미가 아니다.

# 화면 분석 요청 QA (#171)

검증일: 2026-11-08. 개발 DB5432·기존8080 서버 대신 임시 PostgreSQL17(초기55471, 최신 통합55472)과 random-port HTTP/stub을 사용했다. 실제 Google/Chrome/Desktop/AI 모델 성공으로 해석하지 않는다.

## 결과

| 검사 | 결과 |
| --- | --- |
| Backend build·Checkstyle·H2 전체 |200개, 실패0/오류0/생략9|
| PostgreSQL CI 선택 + 세션/heartbeat 회귀 |80개 전부 통과|
| PG 분석 API |39개 전부 통과|
| PG 분석 migration |14개 전부 통과|
| PG 기존 확장 migration |9개 전부 통과|
| PG 닉네임 동시성 |1개 통과|
| 별도 컨텍스트 정적 리뷰·timeout 수정 재리뷰 |확정 P1/P2 없음|

최신 dev 통합 build의 생략9개는 PostgreSQL 전용 조건에 따른 것이며 전체200개가 모두 실행됐다는 뜻이 아니다. 별도 PG80개는 이 조건과 세션/heartbeat를 실제 PostgreSQL에서 검사한다. 초기 head6c54dc8의 원격 CI2개·CodeRabbit은 성공했다. 이번 리뷰 수정 커밋의 원격 결과는 push 후 확인한다.

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
- 실제 Flyway V12→V14: 기존 세션 accumulatedActiveMs1234 보존, clientRunId unique, source/sequence/confidence/relation/guard CHECK, session FK, 원문 컬럼 부재, source를 가로지르는 observedAt 최신 정렬. 시나리오별 임시 schema를 삭제했다.

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

## 외부 P2 두 건 보완

- TAB_META host 없는 제목도 저장·AI 호출 전400으로 차단한다. host 검증 회귀는 CHROME_DOM/TAB_META 각각 metadata 누락·host null·빈 값과 보호 상태 요청을 검사한다. 알려진 제외 host의 TAB_META 제목은200 EXCLUDED·AI 미호출이다.
- 기기와 서버의 시각 비교를 제거했다. 시작 직후·재개 직후 각각±60초 총4종을 검사하고200 RELATED, observedAt 원본 보존, receivedAt 서버 시각을 확인했다. 기존 지연 결과/보호 상태 순서와 AI 도중 실행 구간 변경 검사도 통과한다.
- 추가 정적 검토에서 실행 도중 기기 시계가 역행하면 같은 기기의 observedAt 최신 조회가 이전 미래 시각을 유지할 수 있음을 발견했다. 이 경우를 해결한 것으로 기록하지 않는다. 전역 관측 순서/시계 구간 계약은 별도 협의가 필요하며 이번 두 지적의 고정 시차와 구분한다.

## 관측 순서 계약 확정·구현 (사용자 판단 위임)

- Notion API Request/비고/오류, AI2.2, ERD6.1/7.1/완료 기준과 analysis_runs 카탈로그를 보완했다. 선택 observationSequence와 source sequence를 구분하고 단계적 전환·구형 판정 보류를 명시했다.
- Server의 current 조회는 findCurrent만 사용한다. 번호 모드는 전체 번호, 구형 순서 불명 모드는 안전 이력의 ID, 나머지 구형은 source 과거 이력을 제외한 observedAt 순서다.
- 새 회귀: 다른 source·시계 역행·작은 번호 역순 도착·번호 없는 downgrade·번호 scope·번호 중복/형식·동시 중복·지연 AI 완료·구형 시계 역행·보호 상태 유지·번호 모드 복구. AI 내부 envelope에 새 필드를 전달하지 않는 것도 확인했다.
- 최신 전체 build/Checkstyle:175개 실패0/오류0/생략7. PostgreSQL53개 전부 통과(분석 API29+분석 migration14+확장 migration9+닉네임1).
- PG migration은 기존 V14의 미래 observed_at/판정/누적 시간1234를 보존한 채 V15를 적용했다. 전역 번호 unique·양수/JS 안전 범위·순서 불명 상태의 확정 관련성 금지 CHECK를 검증했다.
- 별도 컨텍스트 정적 리뷰에서 확정 P1/P2 없음. 이전 시계 보정 P2는 이 계약·서버 보호 경로로 보완됐다. 번호 없는 기존 클라이언트도 시계 역행 뒤 무리하게 AI 판단을 이어가지 않는다.
- 실제 Desktop/Extension은 observationSequence 전송을 아직 구현하지 않았다. 시계 보정 중에도 계속 정상 분석하려면 전용 인계에 따라 counter를 추가해야 한다. 기존 요청의 판정 보류를 정상 AI 판정 성공으로 표현하지 않는다.
- 실제 AI/앱 QA, 원격 최신 CI·CodeRabbit은 각각 별도 확인한다. merge/이슈 종료는 하지 않는다.

## 최신 dev 통합 재검증 (2026-11-09)

- origin/dev24db7de를 작업 브랜치에 통합했다. 기존 heartbeat V13을 보존하고 분석 신규 파일을 V14·V15로 조정했다. 아직 배포되지 않은 PR 마이그레이션 번호만 바꿨으며 사용자 개발 DB는 수정하지 않았다.
- 전체 build·Checkstyle:189개, 실패0/오류0/생략9. PostgreSQL:70개, 실패0/오류0/생략0(분석29+분석 migration14+확장 migration9+닉네임1+세션16+heartbeat cleanup1).
- V13→V14→V15 적용 및 V14 분석 이력 보존을 확인했다. 임시 QA schema0개 확인 후55472 컨테이너를 중지했다.
- 통합 diff의 별도 정적 재검토에서도 확정 P1/P2 없음. 실앱 전역 counter·실제 AI 판정 QA는 별도다. 이전 절의 수치는 해당 시점의 기록이며 이 절이 최신 결과다.

## CodeRabbit 재리뷰 회귀 (2026-11-09)

- 9d131e9 리뷰의 actionable5건 중4건(시각 저장 범위, DOM metadata host, OSIV snapshot, schema allowlist)을 보완했다. 낮은 legacy counter의 임의 수용은 지연 요청을 최신으로 올릴 수 있어 하지 않고 기존 전역 번호/새 세션 복구를 유지한다.
- OSIV=true 실제 HTTP에서 AI 호출 중 별도 thread/transaction이 family 폐기·동의 철회·pause/resume한3종을 검증했다. 완료 시 clear를 임시 제거하면 각각401→200, PRIVACY_BLOCKED→RELATED, UNCERTAIN→RELATED로3개 모두 실패했다. 보호 코드 복원 후 테스트를 다시 실행했다.
- PostgreSQL17 경계의 최소/최대 microsecond2종 수용, 저장 불가 및 상단 반올림 이탈4종400, 저장/AI0회를 확인했다. 출처 host 없는 본문 없는 DOM 제목은400, 알려진 제외 host는EXCLUDED·AI0회다.
- 세션 내 Desktop/Extension 같은 전역 번호와 같은 기기의 서로 다른 세션 같은 번호를 각각 검증한다. RUNNING 재개 후 예전 AI 결과 거절, AI 요청 정확히1회, 유효 origin+빈 key일 때0회로 테스트 보강4건도 반영했다.
- 최종 전체 build·Checkstyle200개(실패0/오류0/생략9), PostgreSQL80개 전부 통과(분석39+분석 migration14+확장 migration9+닉네임1+세션16+cleanup1). 최신 원격 CI는 push 후 확인한다.
- 범위 근거: https://github.com/postgres/postgres/blob/REL_17_STABLE/src/include/datatype/timestamp.h . 기본OSIV=false 설정은 유지했다. 실제 앱/AI QA는 미실시다.

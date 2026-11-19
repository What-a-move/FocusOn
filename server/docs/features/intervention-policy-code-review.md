# #182 격리 코드 리뷰

## 분석 추적 로그 보완 (2026-11-18)

구현 대화 이력이 없는 리뷰 에이전트가 로그와 회귀 테스트를 검토했다. 신규 로그에 요청 원문이나 비밀값이 직접 전달되는 경로는 발견하지 못했다.

### 1. 🟡 Medium — AI 응답 본문 실패 시 이미 받은 HTTP 상태 누락

AI가 503 헤더를 보내고 본문 상한을 넘기면 `AnalysisAiClient.analyze`에서 응답 완료 전에 예외가 발생해 상태코드가 사라졌다.

- 헤더 수신 시 상태를 별도로 보존하면 응답 제한과 취소 처리를 유지하면서 원인을 구분할 수 있다. 이 방식을 적용하고 8KiB 초과 503 응답을 회귀 테스트에 추가했다.
- 별도 실패 분류를 두는 방법도 검토했다. 로그 소비자가 새 분류를 해석해야 하며, 상태코드 보존을 위한 변경도 필요하다.

### 2. 🟢 Low — 공개 HTTP와 AI 요청 사이 추적 검증이 분리됨

`AnalysisApiTests`는 AI 클라이언트를 직접 대체하고 `AnalysisAiClientTests`는 MDC를 직접 채워, 공개 요청의 추적 ID가 호출 전체를 통과하는지 확인할 수 없었다.

- 공개 HTTP 테스트에 루프백 AI를 연결하면 실제 분석 클라이언트, 이벤트 순서, 민감값 차단을 한 요청으로 검증할 수 있다. 이 방식을 적용했다.
- 두 테스트를 유지하고 외부 AI로 수동 검증할 수도 있다. 외부 환경과 자격증명에 의존해 로컬 회귀를 반복하기 어렵다.

정적 재검토에서 두 항목의 보완을 확인했다. 후속 스모크 테스트에서 발견한 `sequence` 타입 누락도 검토했다. `sequence` 검증 실패 로그에는 스키마 타입인 `BigDecimal`이 남는다. 실제 HTTP 400 회귀에서 필드명·타입·traceId와 값 비노출을 확인한다. 테스트 실행 결과는 [서버 요청 추적 QA](server-tracing-QA.md)에 기록한다.

## PR #276 리뷰 반영 (2026-11-18)

- P1. 최신 `dev`를 병합하고 이력 조회 HTTP 회귀와 AI 추적 HTTP 회귀를 별도 테스트로 유지했다. `CONTEXT.md`와 `NEXT_TASK.md`는 두 브랜치의 기록을 합쳤다.
- P2. `store.write.ok`에 저장 트랜잭션이 확정한 판정·신뢰도·사유를 기록한다. 동의가 AI 대기 중 철회되면 AI 로그의 `RELATED`와 저장 로그·DB·응답의 `PRIVACY_BLOCKED`가 같은 traceId로 확인된다.
- P3. 원시 `long`을 검증 로그의 허용 타입에 추가했다. 연결 해제 HTTP 요청에서 필드명·타입·400을 확인하고 잘못된 경로 값은 콘솔에서 가린다.
- 독립 정적 검토에서 세 항목의 해결과 기존 이력 조회 회귀 보존을 확인했다. 실행 결과는 [서버 요청 추적 QA](server-tracing-QA.md)에 기록한다.
- CodeRabbit의 후속 네 건도 반영했다. 검증 타입 설명을 정정하고, 요청 헤더와 본문에 서로 다른 ID를 써 추적 전달을 검증한다. 판정 보류·생략 응답은 비어 있는 신뢰도를 확인하고, AI 로그의 메시지·필드·MDC·예외 첨부를 검사한다.

## 초기 개입 정책 리뷰 (2026-11-09)

- 일자: 2026-11-09
- 방식: `code-review-isolation.md`에 따라 대화 이력 없는 별도 에이전트에 diff·기획서·컨벤션만 전달.
- 범위: intervention 신규 모듈·테스트/V16, AnalysisRunStore 최소 연결, heartbeat commit 후 평가.
- 리뷰자는 파일/원격 상태 변경이나 테스트 실행을 하지 않았다. 테스트 결과는 QA와 분리한다.

## 발견 및 보완

| 우선순위 | 문제 | 보완·재검토 |
| --- | --- | --- |
| P1 | AnalysisRunStore의 AI 실행 latest에서 orderingUntrusted를 제외해 정책 전이도 누락. 기존 구간/알림이 남거나 이후 번호 복구 시 불명 기간이 누적될 수 있었음 | policyCurrent를 findCurrent 일치로 별도 계산해 untrusted도 interrupt. heartbeat fallback도 receivedAt 기준 invalidate. 실제 Store pending/보호/순서 불명 복구 회귀 추가. 독립 재검토에서 해결 확인 |

- 실제 PG QA에서 발견한 pending_slot NULL CHECK 누락도 추가 재검토했다. `IS NOT NULL` 보강으로 SQL UNKNOWN 통과를 막았다.
- JDBC Timestamp 변환·추가 회귀 2건도 재검토했고 추가 actionable finding 없음으로 보고받았다.
- production adapter/receipt 및 Notion 동기화 미완료는 명시된 통합 제한이다. 봇 리뷰나 실제 앱 QA 완료를 뜻하지 않는다.

## 0.6 기준 변경 후 독립 리뷰

별도 대화 이력이 없는 리뷰 에이전트가 HEAD 대비 AnalysisAiClient/테스트 변경과 최신 요구사항을 검토했다. numeric 일반/베타 모두 <0.6 보류, >=0.6 다른 게이트 유지, null 베타 예외/실패 보호 유지에 부합하며 추가 actionable finding 없음. 리뷰자는 테스트를 실행하지 않았다.

## 실제 AI 검사 도구 독립 리뷰

- 범위: Python 검사 도구 및 loopback 회귀, 명시 실행 Java live/readiness 검사.
- 발견: Java health assertion/JSON 예외의 원문 노출 가능성, Python BadStatusLine 예외 원문 노출 가능성, Java health 본문 수신 전체 기한 누락.
- 보완: 고정 실패 메시지·원인 미노출, HTTPException 안전 처리, 비동기 응답 전체 기한·취소·크기 제한. 각 회귀 추가 후 독립 재검토에서 추가 actionable finding 없음. 허용된 version 필드에 키를 반사하는 응답도 거절하도록 추가 보강·재검토했다.
- 리뷰자는 실제 외부 모델 연결이나 테스트를 실행하지 않았다. 실행 결과는 analysis-ai-live-qa.md와 구분한다.

## 전경 context·전송 receipt 확장 격리 리뷰

- 구현 대화 이력이 없는 별도 리뷰 에이전트가 diff/신규 파일/정본 계약을 읽었다. 코드를 변경하거나 테스트를 실행하지 않았다.
- P2: context 없는 호환 분석이 현재 전경의 episode/PENDING을 invalidate하고 heartbeat 최신 관측을 바꿀 수 있음. 정책 조기 반환과 context 소속 findCurrent overload로 분리했다. 번호/순서 불명 우선순위를 유지하며 더 큰 번호의 구형 이력이 현재 구간/후보를 바꾸지 않는 회귀를 추가했다. 재검토에서 해결 확인.
- 늦은 SENT의 수명 만료 후 감사 기록, 전송된 PENDING slot 해제/ack 유지 및 V18 CHECK를 추가 재검토했고 actionable finding 없음.
- 별도 migration 리뷰는 V17→V18 보존·FK/nonce/순번 unique·JS 범위·slot CHECK를 대조했으며 추가 지적 없음. 런타임 QA 성공을 대신하지 않는다.

## PR205 원격 precision/CI 보완 재검토

- 별도 리뷰 에이전트가 ack 최초 now MICROS 고정, lease JDBC fixture 정밀도, 원격 PG 테스트 범위 확장을 읽기 재검토했다. 최초/반대/동시 ack 및 수명 계약 변경 없음, 추가 actionable finding 없음. 파일·원격 변경과 테스트 실행은 하지 않았다.
- 원격 PG의 기존 job/DB/권한을 유지하며 새 개입/heartbeat/SessionApi 회귀를 포함한 변경도 별도 읽기 검토했다. 로컬 Flyway+validate143개 결과와 리뷰 판단은 분리한다.

## CodeRabbit9건 후속 보완 독립 리뷰 (2026-11-09)

- 구현 대화 없는 새 에이전트가7ab 이후 diff·신규 동의 listener·계약·회귀를 읽기 검토했다. context 현재성/후보 보존/동의 중단/cooldown freshness/connection cleanup 변경에서 재현 가능한 추가 버그를 확인하지 못했다.
- 테스트 실행/파일·원격 변경은 하지 않았다. 실제 실행 결과279개/격리PG152개는 구현자가 별도로 검증했다. CodeRabbit 원본9건별 처리는 intervention-policy-review-response.md를 따른다. 자동 재리뷰 결과와 혼동하지 않는다.

## 3664598 재리뷰 추가2건 독립 재검토

같은 독립 리뷰자가 HEAD3664598 이후 diff를 읽고 matching pending 없는 완료를 무시하는 보호 경계·필수 동의3종 재동의 회귀·소비자 coordinator/barrier 문서에서 새 버그/계약 위반을 확인하지 못했다. 테스트 실행·파일/원격 변경은 하지 않았다. 구현자 검증은 전체282개(실패0·생략14)/격리PG155개 전부 통과다.

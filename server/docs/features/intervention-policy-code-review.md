# #182 격리 코드 리뷰

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

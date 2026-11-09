# 개입 정책 소비자 연결 인계 — Issue #182

현재: AI 숫자 기준0.6은 PR #190으로 dev에 병합됐다. confidence 숫자 변경 요청은 더 보내지 않는다. context/receipt 서버 구현과 Notion 정본 동기화는 완료했으며 PR #205 후속 리뷰 보완 중이다. 실제 AI 연결 QA는 보류, Desktop/Extension/shared-types 연결과 포키 실표시는 미완료다. 이 인계문은 외부 담당자에게 자동 전송하지 않았다.

## 최신 승인 계약의 소비자 연결 요청 (2026-11-09)

Backend #182에서 전경 context와 전송 receipt를 연결했습니다. PR #205를 생성·push했고 코드리뷰 피드백을 보완 중입니다. 아직 dev 병합/배포 전이며 병합은 별도 승인 대상입니다. AI 실연결 검사는 보류했습니다. 서버 합성 QA와 실제 포키 표시 QA는 구분합니다.

### Desktop

1. 세션 runner가 전경 변경/미확인 즉시 PUT `/api/v1/sessions/{sessionId}/observation-context`를 보냅니다. Authorization·X-Device-Id를 붙이고 `{clientContextId,foregroundSequence,observer}`를 전송합니다. observer는 DESKTOP/EXTENSION/NONE이며 실제 앱/탭 제목·URL은 보내지 않습니다. 전경 sequence와 nonce는 영속화하고 재시도 때 유지합니다. 늦은 PUT 응답을 이미 더 큰 순번의 local context에 덮어쓰지 않습니다.
2. 현재 전경을 확인할 수 있을 때만 기존 유효 RUNNING heartbeat에 `observationContextId`를 연결합니다. lease30초가 만료되면 같은 heartbeat로 복구되지 않습니다. 전경을 다시 확인하고 더 큰 순번의 새 context가 필요합니다. 기존 타이머/명령 version은 유지합니다.
3. Desktop 분석 요청에 현재 context ID를 연결합니다. 관측 순번은 기존 인계대로 session+device 영속 전역 counter입니다. 전경 context 순번과 분석 관측 순번은 다릅니다. context 없는 구형 분석은 수용하지만 현재 정책에는 영향을 주지 않습니다.
4. interventions GET의 PENDING을 표시하기 직전에 현재 세션/RUNNING/context/보호 상태를 확인합니다. `remainingTtlMs`에서 HTTP 요청 시작 이후 monotonic 경과 시간을 차감합니다. 서버 expiresAt과 기기 Date.now를 직접 비교하지 않습니다.
5. 표시 시도 nonce를 영속화하고 실제 성공 채널을 모아 POST `/api/v1/interventions/{interventionId}/deliveries`로 `{clientDeliveryId,dispatchStatus,channels}`를 보냅니다. SENT는 실제 성공한 허용 채널(MOLE_UI/MACOS_NOTIFICATION/SOUND)의 비어 있지 않은 부분집합, NOT_SENT/UNKNOWN은 빈 배열입니다. 같은 시도/결과의 재전송은 nonce를 유지합니다.
6. receipt를 내구성 있게 재시도합니다. 모든 채널의 미전송이 확실한 NOT_SENT만 TTL 안 같은 이벤트를 재시도합니다. 재시작 뒤 전송 여부가 불명이면 자동 재전송 대신 UNKNOWN을 보냅니다. UNKNOWN/유실은 서버 추가 생성 보류입니다. 첫 SENT 수신 이후 GET에서 같은 PENDING을 다시 표시 대상으로 주지 않습니다.
7. 사용자 확인/닫기는 기존 acknowledgements로 보냅니다. 전송 receipt와 분리하며 확인했어 이후5분 UI 상태는 유지합니다. 전송 확인된 PENDING은 미전송30초 TTL로 사용자 반응을 만료하지 않습니다.

### Extension / shared-types

GET 같은 observation-context 경로를 Authorization·X-Device-Id로 조회합니다.204면 유효 위임 없음,200이면 자신의 observerDeviceId/EXTENSION 담당 여부를 확인해 분석 요청에 ID를 연결합니다. 오래된 조회 응답을 새 전경에 적용하지 않습니다. 탭 전환/민감 화면/전경 미확인 중에는 Desktop도 이전 후보를 표시하지 않도록 양쪽의 로컬 보호·확인 상태를 연결해야 합니다. 필요한 신호를 기존 흐름에서 제공할 수 없으면 담당자가 근거와 함께 알려주고 계약을 임의 우회하지 않습니다.

공유 타입 추가: context PUT/GET DTO, analysis/heartbeat의 선택 observationContextId, interventions의 observationContextId(string|null)·remainingTtlMs(number), delivery request/response. UI 표시·전송·receipt 재시도/복구 구현은 Backend가 직접 수정하지 않았습니다.

### 세션 API 담당

기존 SessionHeartbeatService의 유효 RUNNING commit에서만 context lease 갱신을 추가했습니다. 무시된 신호·오류·목표 도달은 갱신하지 않습니다. 세션 시간/연장/종료 로직을 재구현하지 않았습니다. V16 세션 연장을 통합하고 개입 V17, 연결 V18로 번호 충돌을 해소했습니다.

정본과 전체 필드/오류: intervention-policy-integration-contract.md 및 docs/API_CONTRACT.md. 실제 앱 QA에서는 FOCUSON_DEMO=1을 끕니다. UI demo의 interventions 대체 응답을 서버 성공으로 기록하지 않습니다. 외부 담당자에게 이 문서를 자동 전송하지 않았습니다.

<details>
<summary>과거 검토 이력 — 현재 미완료 작업으로 다시 요청하지 않음</summary>

# 개입 정책 소비자 연결 제안 — Issue #182

상태: 2026-11-09 사용자 승인 계약으로 Backend 연결을 구현·검증 중이다. 아래 이전 제안 기록은 최신 승인 절로 대체된다. Desktop/Extension/shared-types는 미수정이며 이 문서는 미전송 인계문이다.

## Desktop·Extension 담당자에게

서버는 #182에서 화면 OFF_TASK Policy Engine과 기존 개입 조회/ack API를 담당합니다. Frontend/shared-types 코드는 변경하지 않습니다. 다음 두 연결 계약을 함께 검토해야 실제 기기 간 화면 선택과 알림 전송 기준 cooldown을 보장할 수 있습니다.

1. **현재 관측 담당 위임**
   - 근거: 서버 `findCurrent`는 session+device별이고 각 기기의 순번은 서로 비교할 수 없습니다. Desktop `screen-observer.cts`는 Chrome 전경을 건너뛰고 같은 화면을 15초 유지한 뒤 한 번만 관측합니다.
   - 제안: Desktop이 단조 전경 context 세대와 담당 기기(Desktop/연결 Extension)를 알려주고, Extension 분석도 그 context를 참조합니다. 서버는 선택된 기기의 현재 관측만 사용합니다. 이전 context 완료는 이력만 남기고 현재 상태를 바꾸지 않습니다.
   - 보호 해제: 해당 담당 기기의 더 높은 유효 관측 또는 Desktop의 명시적 새 context 전환으로만 해제합니다. 기기 간 counter/시각 또는 접수 시각 비교로 해제하지 않습니다.
   - 같은 전경을 유지하고 있다는 신호와 미관찰 전환도 필요합니다. heartbeat 선택 필드 연계 또는 별도 제어 계약은 팀원 API 담당과 합의 후 정본에 반영해야 합니다. 아직 공개 필드/경로를 확정하거나 변경하지 않았습니다.
   - 전경을 서버가 알 수 없는 구간은 판정 보류합니다. 단순 TTL로 현재 화면을 추측하지 않습니다.

2. **알림 전송 영수증**
   - 근거: `intervention-watcher.cts`는 GET 뒤 show/notify를 호출하고 로컬 notify.fire 로그만 남깁니다. RECENTER/REST/CLOSE는 사용자 반응이며, 서버에 전송 성공을 알려주지 않습니다.
   - 제안: 선택된 채널로 전송 성공한 eventId에 대해 별도 receipt를 보냅니다. GET/생성/사용자 ack를 실제 전송으로 바꾸어 해석하지 않습니다. macOS API 호출 성공과 실제 사용자 표시/인지의 보장 범위도 분리합니다.
   - 서버 cooldown은 첫 검증 receipt 수신부터 5분으로 보수적으로 계산하는 안을 검토합니다. 실제 전송과 수신 사이 지연을 계약에 명시해야 합니다. receipt 중복은 시작 시각을 갱신하지 않습니다.
   - receipt의 내구성 있는 재시도·복구가 필요합니다. 유실 시 서버는 전송 여부를 모르므로 새 개입을 무조건 계속 생성하지 않습니다. 현재 watcher는 seen Set을 먼저 갱신하므로 전송 실패 때 재시도 가능 여부도 함께 검토해야 합니다.
   - 새 receipt API는 기존 두 API 외의 사용 사례입니다. 별도 이슈/계약 승인이 필요하며 아직 생성하지 않았습니다. local-events나 기존 ACK action을 임의로 확장하지 않았습니다.

**확인했어 이후 5분 재집중은 확정된 Desktop UI 동작입니다. 이 동작은 서버 정책 재논의·서버 상태 추가 대상이 아닙니다.**

미전송 PENDING은 30초 또는 보호/판정/실행 상태 변경 시 만료하도록 사용자 승인을 받았습니다. GET 응답 후 실제 표시 전까지의 경쟁은 서버 조회만으로 제거할 수 없어 Desktop도 현재 세션/context를 확인해야 합니다. rolling 창 내부 시간 clipping·열린 구간 1회 포함과 30초는 승인됐으나 Notion 동기화는 아직입니다.

## heartbeat·세션 API 담당자에게

기존 heartbeat 생존/시간 누적·상태 전이는 재구현하지 않습니다. 유효 RUNNING 신호 commit 뒤 정책 평가 trigger를 남기는 최소 연결안을 검토 중입니다. 중복/무시 신호·목표 도달·ABANDONED는 생성 trigger 대상이 아닙니다. 별도 평가 transaction은 최신 세션을 다시 확인합니다. outbox·신호 추가와 전경 context liveness 연결은 담당자 경계를 맞춘 뒤 적용하겠습니다.

#163/PR #164 종료와 #168 연장 범위는 가져오지 않습니다. 정책 구간이 pause/resume을 가로질러 이어지지 않게 lastResumedAt 세대를 확인합니다. 정확한 과거 pause 시각을 현재 저장값에서 복구할 수 없다면 추정하지 않으며, 후속 리포트의 정밀 시간은 세션 전이 이력 계약과 연결해야 합니다.

## AI 담당자에게

#154 정식 분석 경로의 판정 응답 연동은 별도 확인 항목입니다. 서버 정책/API와 합성 테스트는 독립 진행할 수 있습니다. ReceptionReceipt/RECEIVED나 AI 미설정·실패는 UNCERTAIN/ANALYSIS_UNAVAILABLE로 처리하며 실제 AI→포키 표시 QA 완료로 기록하지 않습니다.

## #182에서 구현한 서버 경계

- 코어/저장/기존 두 API와 분석·유효 heartbeat 최소 연결을 구현했습니다. production 전경 adapter는 아직 없음이며 합성 Binding으로 테스트합니다.
- 재분석 처리 중에는 같은 구간 ID·확정 누적을 유지하고 대기 시간/개입 생성만 동결합니다. 확정 UNCERTAIN·실패·보호·RELATED는 구간을 끊습니다.
- P1 전경 계약 → P2 Desktop/Extension/shared-types 소비자 적용 → production 관측 선택 활성화 순서입니다.
- P3 전송 receipt 계약·서버 수신 → P4 Desktop 채널 결과/receipt 복구 → production cooldown 활성화 순서입니다.
- P5 팀원 heartbeat/세션 경계, P6 #154 실제 AI, P7 전체 연동 QA를 이슈 #182에서 별도 추적합니다. 현재 생성 보류를 해제하지 않았습니다.
- 원문이 훼손된 Notion 문단은 사용자 승인으로 원문 복구했습니다. 새 정책은 추가하지 않았으며 정본 반영이 남았습니다.

## 시연 준비 재확인 (2026-11-09, dev 4e212de)

- PR #164는 OPEN/Draft, CONFLICTING/DIRTY다. 종료 담당의 rebase/충돌 해소·QA·승인/병합이 필요하다. #164 병합만으로 전체 앱 시연 QA가 완료됐다고 볼 수 없다. 이 작업에서 팀원 브랜치를 수정하지 않았다.
- AI #154의 구현 PR #190은 OPEN/MERGEABLE이다. 정식 내부 경로가 decision/confidence/reasonCode/modelVersion/promptVersion을 반환하도록 수정하고 candidate 모드 0.75 기준을 적용한다. 아직 dev에 병합되지 않았다. PR 설명도 실제 학교 모델·Backend·Desktop 전체 왕복 미검증을 명시한다. 자동 release note의 과거 0.6 설명은 최신 코드 0.75와 다르므로 코드 기준으로 판단한다.
- 현재 dev의 정식 내부 AI 경로는 여전히 ReceptionReceipt/RECEIVED다. Backend의 호출 경로는 이미 정식 경로다. 현재 checkout server/.env의 AI_ANALYSIS_URL/FOCUSON_AI_API_KEY는 비어 있음만 확인했으며 비밀 값을 출력하지 않았다. 실행 중인 다른 서버/배포 환경 설정이나 실제 AI 왕복은 확인하지 않았다.
- #182 코드는 로컬 1a53903/ccfa207에 있으며 아직 push/PR/병합하지 않았다. 기존 두 API 구현만으로 전경 context·관측 소속·전송 receipt 연결이 생기지 않는다. P1~P4가 실제 포키 생성의 선행이다.
- dev PR #191/#193으로 Desktop 시연 모드가 추가됐다. apps/desktop/electron/main/demo-mode.cts는 interventions GET을 실제 서버에 보내지 않고 단축키로 만든 메모리 이벤트를 반환한다. 종료/요약/달력/일간은 실제 요청 실패 시 합성 응답으로 대체한다. learning-summary는 합성하지 않는다.
- 실제 서버 연동 QA에서는 FOCUSON_DEMO=1을 사용하지 않는 일반 실행을 해야 한다. 시연 모드 영상과 실제 AI→포키·종료/리포트 연동 증거를 구분한다. 이 코드는 읽기만 했으며 Frontend를 수정하지 않았다.
- 담당 우선순위는 #182의 실제 연동 계약/연결 → 세션 summary·learning-summary 조회(생성 AI와 역할 분리) → calendar/daily다. #164/#168 세션 명령 및 #99 목표 AI는 팀원 범위로 유지한다.

## 2026-11-09 이전 결정 — AI 숫자 점수 기준 0.75 (이후 0.6으로 대체)

- 사용자 확정: 공통 문서 기준 0.75로 Backend·AI 연동을 진행한다. 0.6으로 낮추지 않는다.
- 숫자 confidence는 0.75 미만이면 UNCERTAIN, 정확히 0.75 이상이면 다른 권한·출처·보호·정책 게이트를 추가 검증한다. 점수만으로 개입을 생성하지 않는다.
- 숫자를 반환하는 베타 -uncalibrated 응답에도 같은 0.75를 적용한다. 기존 승인된 -uncalibrated + confidence:null 예외는 별도 계약이며 이번 승인으로 임의 제거/확장하지 않는다.
- 현재 AnalysisAiClient와 PR #190 실제 코드가 이미 0.75라 임계값 코드 변경은 필요 없다. 실제 AI/학교 Ollama/포키 왕복 QA는 여전히 별도다.

## 2026-11-09 최종 변경 — AI 숫자 점수 기준 0.6

- 사용자의 최신 지시로 앞선 0.75 결정을 대체한다. 숫자 confidence는 0.6 미만이면 UNCERTAIN, 정확히 0.6 이상이면 기존 권한·출처·보호·시간 정책을 추가 검증한다.
- 숫자 점수를 반환하는 -uncalibrated 베타 응답도 0.6이다. 기존 승인된 -uncalibrated + confidence:null 예외는 변경하지 않는다. APP_META/TAB_META UNRELATED 비누적·실패 시 안전 보류도 유지한다.
- 서버 AnalysisAiClient와 경계/중간값 회귀를 0.6으로 변경했다. AI PR #190 head252b513은 아직 0.75이므로 AI 담당의 별도 수정이 필요하다. AI/shared-types/Frontend는 직접 수정하지 않았다.
- 사용자 추가 승인으로 Notion AI 분석 계약 4.4/7장의 confidence 기준과 베타 예외 내 수치 참조 총 5곳을 0.6으로 수정하고 재접속 후 저장을 확인했다. 공통 docs/API_CONTRACT.md와 Backend DEVELOPMENT_RULES의 현재 숫자 기준도 0.6으로 동기화했다. null 예외/이탈 시간/cooldown은 유지했다.
- 실제 AI/학교 Ollama/포키 전체 왕복·production adapter/receipt 연결 완료를 뜻하지 않는다.

### 과거 AI 수정 요청 — 현재는 모두 반영됐으므로 재요청하지 않음

- AI PR #190의 AI/src/api/routes.py candidate 점수 gate `score < 0.75` → `score < 0.6`. AI/src/api/reception.py의 numeric beta 검증 `confidence < .75` → `confidence < .6`. 실제 코드·PR 설명·AI 문서·경계 테스트를 함께 맞춘다.
- 공통 API 계약과 QA의 confidence 숫자 기준: '0.75 미만은 UNCERTAIN' → '0.6 미만은 UNCERTAIN'. 정확히 0.6은 점수 게이트를 통과하지만 다른 보호/개입 조건은 그대로다.
- Notion의 같은 numeric confidence 기준 0.75를 0.6으로 동기화한다. confidence:null 베타 예외, 이탈 시간 임계값과 cooldown은 이 변경 대상이 아니다.
- 단순 모의 판정으로 포키가 보이는 것과 실제 모델 점수의 정확도 검증을 구분한다.

### 0.6 변경 검증·정본 동기화 결과

- 전체 build/Checkstyle 성공: 234개, 실패0·오류0·생략10. numeric 일반/베타 양쪽의 0.599 보류, 0.6·0.6001·0.65·0.749 통과 및 null 예외를 검증했다.
- 별도 컨텍스트 리뷰: 추가 actionable finding 없음. 이번 변경은 숫자 정규화만이며 SQL 변경이 없어 PostgreSQL QA는 재실행하지 않았다(기존 #182 PG87개 결과와 구분).
- Notion AI 분석 계약 4.4/7 numeric 기준 및 null 예외의 기준 참조를 동기화했다. API POST 페이지에는 별도 0.75 기준 문구가 없어 수정하지 않았다. Notion의 관측 순서·rolling/30초 만료 등 다른 미동기화 항목은 이번 작업으로 해결됐다고 보지 않는다.
- AI PR #190 head252b513의 0.75는 AI 담당 수정 대기다. 실제 AI/학교 Ollama/포키 QA·PR 생성·병합·이슈 종료는 미수행이다.


</details>

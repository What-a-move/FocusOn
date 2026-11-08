# 화면 이탈 Policy Engine과 개입 API 기획 초안

## 기본 정보

- 상태: 사용자 지적 2건 반영 후 내부 구현 착수 승인(2026-11-09). 미정 공개 계약·정책은 별도 승인 대기.
- 작성일: 2026-11-09 (Asia/Seoul)
- 관련 Issue: https://github.com/What-a-move/FocusOn/issues/182
- 담당: 사용자 담당 ⑥ 분석·개입, Server만 수정한다.
- 기준: fetch한 origin/dev `5462afd` (PR #188까지; Server 변경 없음).
- 선행: PR #172 MERGED, 검증 head `e77438e`. Issue #171은 단계 0~11 완료·종료 승인 대기로 OPEN이다. 새 기능과 구분한다.
- 중복 확인: GitHub 전체 이슈 최대 300개 및 전체 PR 최대 200개 조회 결과, 별도 Server Policy Engine·개입 구현 Issue/PR을 찾지 못했다. 생성 직전 다시 확인한다.
- 제안 Issue 제목: `[Server] 화면 이탈 Policy Engine 및 개입 조회·확인 API 구현`
- Labels: `area:server`, `type:feature`, `priority:mvp-core`.
- 작업 Branch: 사용자 승인 뒤 최신 dev에서 현재 checkout에 `feat/182-server-intervention-policy` 생성 완료.

## 이번 구현의 실제 경계

- 2026-11-09 사용자 승인: 지적 2건을 보완한 뒤 내부 구현 착수. rolling 창 내부 시간 clipping·열린 구간 1회 포함, 미전송 30초 만료 권고안도 추가 승인됐다.
- 상태: `feat/182-server-intervention-policy`, 기준 dev `5462afd`. 서버 코어/영속화/기존 두 API/분석·heartbeat 연결을 구현하고 검증 중이다. 전체 완료/PR/실제 앱 QA 완료가 아니다.
- 기본 `ForegroundPolicyContext`는 context 없음/관측 소속 불명으로 응답한다. 실제 전경 계약·전송 receipt가 없으므로 production 후보 생성은 보류한다. 공개 요청 필드를 추가하거나 기존 클라이언트가 신호를 보내는 것처럼 가정하지 않는다.
- 테스트만 명시적인 합성 Binding·관측 소속을 주입한다. 이 내부 포트는 공개 API가 아니며 production의 기기별 sequence/observedAt으로 바인딩을 추측하지 않는다.
- 아래 설계 방향 중 초기 테이블 분리/outbox/별도 ack 테이블은 실제 구현과 다르다. 아래 '구현 모델' 절이 현재 구현 기준이다. 미정 외부 계약은 P1~P7 추적표를 따른다.
- Notion은 편집 중 문단 순서가 훼손됐으나 사용자 원문 복구 승인 후 복구 확인했다. 사용자가 원문 복구만 승인해 새 정책은 Notion에 반영하지 않았다. 정본 동기화는 미완료이며 PR/연동 완료 조건에서 누락시키지 않는다.

### 구현 모델

1. `OffTaskTimeline`: 서버 시간 Span을 같은 episode ID로 묶은 불변 snapshot. 같은 실행/context의 분석 대기는 ID·확정 누적을 보존하고 알림/누적만 멈춘다. 최신 UNRELATED 완료 시 재개하며 대기 시간은 소급 가산하지 않는다. 확정 UNCERTAIN/실패/보호/RELATED/실행·context 변경은 종료한다. 확정 UNCERTAIN을 잠시 보류했다 이어 붙이지 않는다.
2. `intervention_policy_states`: session PK/FK, 서버 전용 typed timeline JSONB, 원인 revision. 원문은 포함하지 않는다. context 전환은 현재 episode만 종료하고 이전 10분 반복 이력을 보존한다. 실행 세대 변경은 이전 연속 상태를 재사용하지 않는다.
3. `intervention_events`: session FK, type/status, cause revision, nullable pendingSlot(1), channels JSONB, policyVersion, runningSince, createdAt/expiresAt, actedAt, deliveryConfirmedAt. cause UNIQUE와 pending slot UNIQUE로 DB에서도 중복을 막는다. 사용자 반응은 해당 행의 최초 action/actedAt으로 보존해 별도 중복 ack 테이블을 추가하지 않았다.
4. `InterventionPolicyService`: 분석 prepare/complete와 같은 짧은 transaction에서 pending/확정 전이를 저장한다. `findCurrent`·관측 소속·동의·활성 family·runner/연결 Extension을 확인한다. AI 호출은 기존처럼 transaction 밖이다.
5. heartbeat: 유효 RUNNING 신호만 내부 식별자 이벤트를 발행한다. AFTER_COMMIT listener → 별도 REQUIRES_NEW evaluator가 fresh 세션을 다시 잠근다. bulk managed snapshot을 재사용하지 않는다. 실패는 공용 로거 WARN이며 이미 commit된 생존 기록은 유지한다. outbox는 이번에 구현하지 않았다. 실패한 평가는 다음 유효 heartbeat/분석에서 재평가하며 종료 직전의 평가 성공을 보장하지 않는다.
6. 목록은 data 배열·기존 enum·문자열 ID다. GET은 DB 상태/전송 시각을 변경하지 않는다. TTL 만료는 읽기 시 EXPIRED view로 보이고, 다음 mutation에서 영속화한다. 실행 세대·현재 context·pending 보호가 유효하지 않은 PENDING은 표시하지 않는다.
7. ack는 사용자→세션 잠금 아래 첫 결정을 유지한다. 같은/반대 action도 최초 status/actedAt을 200으로 반환한다. EXPIRED 늦은 ack는 EXPIRED/actedAt null이며 허위 사용자 시각을 만들지 않는다. 새 Idempotency-Key/If-Match-Version을 요구하지 않는다. 자원 비존재/타인 개입은 기존 공통 NOT_FOUND(404), 타인 세션 목록은 SESSION_NOT_FOUND(404)다. 정본의 상세 404 code/nullable actedAt 보완은 최종 계약 대조 항목이다.
8. 전송 미확인 이벤트는 ack/TTL 뒤에도 새 생성을 막는다. deliveryConfirmedAt을 임의로 채우는 공개/내부 수신 API는 만들지 않았다. 전송 receipt와 cooldown 시작점은 P3 합의/구현 선행이며 확인했어 UI 상태는 서버에서 저장하지 않는다.
9. V16은 additive 신규 테이블만 추가한다. V1~V15는 수정하지 않는다. 리포트·이력·카메라·BREAK_SUGGESTION 생성·세션 명령은 추가하지 않았다.

## 목적과 1차 범위

저장된 최신 화면 판정을 시간 기반으로 평가해 OFF_TASK 개입을 생성하고, 기존 Desktop이 조회·표시·확인할 수 있게 한다. AI가 직접 알림이나 세션 상태를 바꾸지 않는다.

1. 화면 OFF_TASK의 연속/반복 조건·개입 영속화·중복 방지.
2. 기존 heartbeat의 유효한 RUNNING 처리와 분석 예약/완료에 정책 평가·구간 갱신 연결. heartbeat 생존·누적 시간·상태 전이 로직은 재구현하지 않는다.
3. GET interventions와 POST acknowledgements, 공개 enum·래퍼·헤더를 기존 소비자에 맞춘다.
4. 최신 관측/보호 상태·정책 버전·세션 전이·동시성·민감정보 비저장을 검증한다.

### 범위 밖

- 사용자·목표·세션 상태 명령 개발. #163 종료 PR #164는 Draft이며 #168 연장도 팀원 범위다.
- BREAK_SUGGESTION 생성, 카메라 입력/개입 생성. 공개 타입 이름은 유지하되 이번 OFF_TASK 구현 완료와 혼동하지 않는다.
- 분석 이력 GET, local-events, 리포트, 학습 정리 생성/조회·보관 cleanup. 각각 기존 구현·Issue·담당을 확인한 뒤 별도 단위로 진행한다.
- Frontend·shared-types·AI 수정. 변경 필요 사항은 Server 인계 문서로 전달한다.
- 실제 AI 판정 통합 완료 주장. #154는 OPEN이며 현재 dev의 정식 내부 경로는 여전히 ReceptionReceipt/RECEIVED다.

## 확인한 정본과 소비자

2026-11-09 실제 Notion 본문 열람:

- [analysis-runs](https://app.notion.com/p/58cf7aade6614fc9a00e248affb052f9)
- [interventions](https://app.notion.com/p/24690cf40b1149419938913acd67d2fd)
- [acknowledgements](https://app.notion.com/p/fb8626ab4897450d95a6bfeddba674e6)
- [화면 분석 5.2·5.3](https://app.notion.com/p/3de0b6e5e64c80c1a9e3d69db89f2ab8)
- [AI 분석 계약 7·8](https://app.notion.com/p/3de0b6e5e64c80fbb0bccb985e38002c)

소비자 근거: `apps/desktop/electron/main/intervention-watcher.cts`를 읽기만 했다. RUNNING에서 10초마다 PENDING 하나를 표시하며 OFF_TASK/BREAK_SUGGESTION만 해석한다. RECENTER/REST는 ACKNOWLEDGED, CLOSE는 DISMISSED로 보내고 ACK 버튼은 추가 서버 요청을 보내지 않는다. 표시 시각·확인했어를 누른 시각은 현재 API로 전송하지 않는다.

## API 정의

### GET /api/v1/sessions/{sessionId}/interventions

- Authorization 필수, Desktop 호출. 세션 소유권을 검증한다.
- 200 성공은 공통 data 안의 배열이다. 빈 목록은 빈 배열이다.
- 항목: interventionId(string), type(OFF_TASK/BREAK_SUGGESTION), status(PENDING/ACKNOWLEDGED/DISMISSED/EXPIRED), channels[], createdAt, policyVersion.
- 새 REQUEST 필드·공개 타입을 임의로 추가하지 않는다. `DISTRACTION_ALERT`나 종료 확인 이벤트를 공개 type으로 내리지 않는다.
- 세션 없음/타인 세션은 404 SESSION_NOT_FOUND, 인증 오류는 기존 정책, 일반 60회/분·429/Retry-After를 재사용한다.

### POST /api/v1/interventions/{interventionId}/acknowledgements

- Authorization·X-Device-Id 필수, Desktop 호출. 소유권·기기 권한을 검증한다.
- Request: `{ "action": "ACKNOWLEDGED" }` 또는 DISMISSED.
- 200 data에 status와 actedAt을 반환한다. 중복/상반된 action은 아래 멱등 설계를 적용한다. 미소유 대상의 정식 404 code는 기존 오류 카탈로그와 대조 후 공개 계약 검토에 포함한다.
- Idempotency-Key를 새 필수 헤더로 만들지 않는다. DB 제약·transaction으로 중복 반영을 막는다.
- ack는 AI relation·세션 status·타이머를 변경하지 않는다. 잠시 쉬기의 pause는 기존 Desktop·세션 명령 경로가 담당한다.

## 유지할 정책

| v1 | 연속 조건 | rolling 10분 반복 조건 |
| --- | --- | --- |
| GENTLE | UNRELATED 180초 초과 | 60초 초과 구간 3회 |
| NORMAL | UNRELATED 60초 초과 | 30초 초과 구간 2회 |
| ACTIVE | UNRELATED 30초 초과 | 20초 초과 구간 2회 |

- 조건은 OR이며 초과는 엄격한 `>`다. 15초 미만 전환은 이탈 구간으로 인정하지 않는다.
- APP_META/TAB_META의 UNRELATED는 `effectiveRelation()`의 UNCERTAIN 취급을 유지한다.
- UNCERTAIN·EXCLUDED·PRIVACY_BLOCKED·미관찰·pause 구간은 연속 이탈을 끊는다. 과거 30초 보류 규칙은 사용하지 않는다.
- 세션 고정 interventionPolicyVersion을 사용한다. 설정·배포·재개 때 v1을 임의 교체하지 않는다.
- confidence 검증은 #172의 안전 정규화와 명시된 베타 `-uncalibrated` 예외를 재사용한다. 실패/RECEIVED를 성공 판정으로 조작하지 않는다.
- 현재 관측은 기기별 `AnalysisRunRepository.findCurrent`로 조회한다. source별 sequence와 session+device 전역 observationSequence를 혼합하지 않는다.
- observedAt은 기기 원본, receivedAt·세션 수명은 서버 시각이다. 두 시계를 직접 비교하거나 서로 다른 기기 번호를 비교하지 않는다.

## 구현 전 확정할 경계

아래에서 사용자 동작·공개 계약에 영향을 주는 항목만 승인받는다. 구간 저장·잠금·중복 ack 등 내부 구현은 아래 설계안으로 구체화하며 사용자에게 구현 선택을 위임하지 않는다.

1. **기기 간 현재 화면 선택:** findCurrent는 기기별이다. Desktop과 연결 Extension의 서로 다른 clock·counter로 세션 전체 실제 관측 순서를 증명할 수 없다. 오래된 반대편 기기의 결과가 최신 보호 상태를 풀지 않도록 활성 대상 선택/보호 우선/판정 보류 기준을 정본에 구체화해야 한다. receivedAt이 크다는 이유만으로 실제 최신 화면이라고 단정하지 않는다.
2. **정책 경과 시간과 실행 구간(내부 설계):** 서버 기준 정책 구간·실행 세대·AI 대기 차단은 아래 내부 구현안으로 구체화했다. 저장 방식·잠금 선택을 사용자에게 결정하도록 요구하지 않는다. 기기 observedAt과 서버 수명 시각의 차로 시간을 계산하지 않는다.
3. **rolling 10분의 구간 포함:** 시작/끝/겹침 기준, 열린 구간의 반복 횟수 포함 여부 및 같은 구간 중복 세기를 정한다. 구간 저장 없이 heartbeat 호출 횟수를 이탈 횟수로 세지 않는다.
4. **실제 전송과 cooldown:** 화면 설계는 실제 전송부터 5분을 요구하지만 현재 GET은 PENDING 조회이며 실제 표시 영수증이 없다. 생성·최초 조회를 실제 표시로 간주하지 않는다. **확인했어 이후 5분은 이미 Desktop UI 상태로 확정되어 서버 정책 재검토 대상이 아니다.** 미정인 것은 서버의 실제 알림 전송 확인 방법뿐이다. 현재 두 API로 보장할 범위와 전송 확인에 필요한 소비자 변경을 결정한다.
5. **개입 수명·ack:** 반복 ack는 아래 첫 결정 유지·최초 actedAt 보존으로 설계했다. 사용자 동작에 영향을 주는 PENDING 만료·EXPIRED에 늦은 ack의 의미만 최종 계약 승인에 포함한다.

정본 보완이 필요하면 이유·영향·Frontend 변경을 먼저 제시하고 승인 후 Notion·문서를 같이 맞춘다. 2026-11-09 사용자가 Issue 등록·상세 기획 검토를 승인했다. 이는 미정 사용자 동작·공개 계약이나 구현 착수에 대한 일괄 승인이 아니다. #154 미완료는 Policy Engine·공개 API 구현 및 합성 테스트의 선행 차단 조건이 아니며 실제 AI→포키 표시 통합 QA와 분리한다.

## 데이터·transaction 설계 방향

- intervention_events·intervention_acknowledgements 및 필요 최소 정책 상태/구간 이력. 텍스트·URL·제목·토큰·AI 전문·알림 문구는 저장하지 않는다.
- 사용자 → 세션 → 정책 상태/개입 잠금 순서를 검토한다. 동시 heartbeat/분석 완료/ack/종료에도 같은 후보를 한 번만 생성하도록 DB 유일 제약을 함께 둔다.
- AI 네트워크 호출은 DB transaction 밖에 유지한다. #172 완료 transaction의 EntityManager clear 및 fresh 권한·동의·실행 구간 재검증을 보존한다.
- 분석 예약·보호 관측 도착에서 기존 후보를 차단하고, AI 완료는 findCurrent 재확인 뒤 해당 관측이 여전히 유효할 때만 정책을 갱신한다.
- heartbeat 연결 위치는 기존 유효 신호 검증·누적 확정 뒤다. 무시된 재전송·오류·ABANDONED/목표 도달 응답에서 개입을 생성하지 않는다. bulk update와 persistence context도 함께 검토한다.
- 정책 호출의 실패 격리·commit 경계를 확정해 heartbeat 생존 기록을 부당하게 유실하거나 실패한 transaction의 개입을 반환하지 않는다.
- 최신 migration은 V13 heartbeat / V14 analysis_runs / V15 관측 순서다. V16은 현재 후보 번호일 뿐, 실제 작성 직전 최신 dev·팀원 PR과 다시 대조한다. V1~V15는 수정하지 않는다.
- #163 Draft PR164의 session 파일과 충돌 가능한 최소 연결만 검토하고 종료 기능은 가져오지 않는다.

## 검증 계획

- 임계값 직전·동일·초과, 반복 구간·10분 경계, 메타데이터/보호/UNCERTAIN/AI 실패 차단.
- 새 관측 없는 heartbeat 평가, 현재 번호·legacy 판정 보류·늦은 AI 완료·두 기기·pause/resume 경쟁.
- session policy/options snapshot, 권한·동의·unlink 후 차단, terminal 상태 개입 금지.
- 실제 PostgreSQL에서 동시 생성·ack·heartbeat·보호 관측 경쟁, migration과 기존 데이터 보존.
- 합성 입력 서버 QA와 실제 AI·Desktop 포키 표시 QA를 별도 기록한다. #154가 미완료라도 순수 정책·공개 API는 격리 검증할 수 있다.
- backend build·Checkstyle 및 필요한 회귀. 개발 DB5432/서버8080은 변경·중지하지 않고 격리 DB/포트를 쓴다.
- 구현 뒤 규칙에 따른 별도 컨텍스트 리뷰를 수행한다. PR 생성·병합·Issue 종료는 각각 별도 승인이다.

## 상세 설계 검토 (2026-11-09, 내부 구현 승인 후)

### API 설계 원칙 대조

- 원본 계약: 기존 두 경로·HTTP method·data 배열·공개 enum을 유지한다. 전송 영수증을 ACKNOWLEDGED나 GET의 부수효과로 숨기지 않는다.
- 사용 사례: GET은 조회만 수행한다. 정책 평가/생성은 분석·유효 heartbeat 쪽에 연결하며 GET을 평가 타이머로 쓰지 않는다.
- 성공/오류: 문자열 식별자·UTC Instant·공통 envelope를 쓴다. ack는 status/actedAt만 반환한다.
- 인증/동시성: 사용자·runner Desktop 소유권과 fresh family를 검증한다. ack는 세션 상태 명령이 아니므로 If-Match-Version/Idempotency-Key를 추가하지 않는다.
- 확장/개인정보: 목록은 정본에 없는 cursor를 임의 추가하지 않는다. 당장은 해당 세션 이력을 createdAt/id 오름차순으로 반환하는 안이며, 대량 응답 제한이 필요하면 계약 승인을 먼저 받는다. 앱 식별자/제목/URL 원문을 정책 상태에 추가하지 않는다.
- 규칙 문서는 5개 원칙을 열거한다. 워크플로우의 '6원칙' 문구와 달리 여섯 번째 원칙을 지어내지 않는다.

### 초기 내부 구현 방향 (이력, 현재 구현은 위 구현 모델 참조)

**모듈:** intervention 패키지에 순수 PolicyEngine(Clock 주입), PolicyCoordinator, 조회/ack Service, Controller, Entity/Repository/DTO를 둔다. 엔진 입력은 정규화된 판정·고정 정책/강도·서버 시각·실행 세대·구간·cooldown snapshot이다. AI client를 엔진에서 호출하지 않는다.

**저장 모델:** 아래는 신규 migration 후보이며 기존 논리 ERD와 실제 키/삭제 정책을 구현 전 맞춘다.

| 테이블 후보 | 최소 필드/제약 |
| --- | --- |
| intervention_policy_states | session_id PK/FK, 실행 세대(lastResumedAt snapshot), 선택된 context/device/run 참조, 현재 interval, 단조 evaluationRevision, 처리한 관측 참조 |
| intervention_off_task_intervals | id, session_id, 실행 세대, started_at/ended_at 서버 시각, 종료 원인, 서로 다른 구간 식별자 |
| intervention_events | id, session_id, type, status, channels snapshot, policy_version, created_at, expires_at, cause revision/구간 참조, deliveryConfirmedAt(nullable) |
| intervention_acknowledgements | intervention_id UNIQUE/FK, action, acted_at(서버), actor device 참조 |
| intervention_cooldowns | (session_id, type) PK, last confirmed delivery event/time, 다음 생성 허용 시각 |

- 이벤트 원인은 unique(session_id, type, evaluationRevision)로 중복 생성하지 않는다. 추가로 같은 세션+타입의 미완료 PENDING은 하나만 유지하는 PostgreSQL partial unique index를 둔다. 이후 새 관측에서 같은 연속 구간을 재평가할 수 있으므로 intervalId만 영구 UNIQUE로 묶지 않는다.
- 분석 원문·제목·전체 URL·host·AI 전문·token·알림 대사는 새 테이블/로그에 저장하지 않는다. 입력 DTO/Entity 전체를 로그로 덤프하지 않는다.
- DB migration은 새로 추가한다. 기존 V13~V15는 건드리지 않으며 V16 충돌을 작성 직전 재확인한다.

**시간/구간:** 정책 누적은 서버 시간만 사용한다. 유효한 UNRELATED 완료가 확정된 commit부터 열린 구간을 시작한다. 새 분석 요청의 처리 중(PENDING)은 확정 UNCERTAIN과 다르다. 같은 실행 세대·선택 context의 기존 이탈 구간 ID와 확정 누적은 유지하고, 대기 중 시간 누적/알림 생성만 동결한다. 최신 요청이 UNRELATED로 완료되면 같은 구간에서 누적을 재개한다. 대기 시간을 소급 가산하지 않는다. 결과가 UNCERTAIN/실패/보호/RELATED이거나 실행 세대·선택 context가 바뀌면 구간을 닫는다. AI 대기 상태는 DB에 별도로 식별하고, #172의 공개 예약 응답 UNCERTAIN과 혼동하지 않는다. pending을 무기한 유효 판정으로 사용하거나 예전 UNCERTAIN 30초 연결 규칙을 적용하지 않는다. 확정 판정과 foreground 유효성이 유지되는 동안 heartbeat가 경과 시간만 평가하며 heartbeat 횟수를 반복 횟수로 세지 않는다. 이벤트는 당시 policyVersion/강도/channels를 고정한다. 서버 재시작 뒤 상태는 DB에서 복구한다. 시계 역행에서는 음수 기간을 0으로 누르고 이미 확정된 시간을 줄이거나 만들어내지 않는다.

**분석 연결:** prepare의 새 current 예약에서는 같은 context 구간의 누적/생성을 동결하고, 확정 보호 결과에서는 구간을 종료한다.  complete에서 fresh 권한/동의/실행 세대 및 findCurrent 일치가 확인된 결과만 전달한다. 늦은 완료·구형 counter는 이미 닫힌 구간이나 만료된 후보를 되살리지 않는다. receivedAt은 접수 시각이며 관측 순서 증명이나 AI 완료 시각의 대용물이 아니다.

**heartbeat 연결:** 유효 RUNNING 신호가 commit된 뒤 서버 내부 trigger를 남기고 별도 짧은 transaction으로 평가한다. 중복·무시된 신호·오류·목표 도달·ABANDONED 경로에서는 생성 trigger를 내지 않는다. 내구성 있는 trigger/outbox를 heartbeat transaction에 함께 기록하면 평가 실패 시 다음 worker 재처리가 가능하다. heartbeat의 생존/누적 계산·응답 스키마는 바꾸지 않는다. bulk update 뒤 reload로 최신 상태를 확인하고 managed stale session을 사용하지 않는다. outbox 연결은 팀원 담당 코드와의 최소 접점으로 리뷰한다. 기존 heartbeat 응답 성공과 정책 평가 성공을 같은 것으로 기록하지 않는다.

**transaction/잠금:** 사용자 → 세션 → policy state/cooldown → event 순서로 일관되게 잠근다. 다른 transaction을 사용자 잠금 보유 상태에서 REQUIRES_NEW로 호출하지 않는다. AI 네트워크 요청 중에는 잠금을 보유하지 않는다. 분석 정책 전이의 내구성을 확보하되 예상 가능한 '판정 보류/미지원 버전'은 예외 대신 안전 결과로 처리한다. 진짜 DB 장애를 catch하고 같은 rollback-only transaction을 성공 처리하지 않는다. worker 재처리는 revision 및 unique 제약으로 멱등이다.

**세션 전이:** 팀원 pause/resume/end를 복제하지 않는다. 평가·조회 시 최신 status/lastResumedAt을 확인해 이전 세대 구간·이벤트를 선택하지 않는다. 새 실행 세대는 이전 구간과 절대 이어 붙이지 않는다. 이전 구간의 정확한 pause 종료 시각이 현재 DB에 없으면 그것을 추정해 기록하지 않고 마지막으로 확인한 서버 시각까지만 집계한다. 향후 리포트용 정밀 전이 시각은 팀원의 세션 전이 이력/연결 계약이 필요하다.

**ack 멱등:** event별 첫 유효 action만 상태에 반영한다. 동일 action 재전송은 최초 status/actedAt으로 200을 반환하며 시각을 갱신하지 않는다. 반대 action도 이미 확정된 결과를 200으로 반환하고 이력을 덮지 않는 '첫 결정 유지'를 제안한다. 새로운 409/오류 enum을 만들지 않는 선택이며 최종 API 검토에 행동을 명시한다. EXPIRED에 늦은 ack는 EXPIRED를 유지하고, actedAt null 허용 여부는 정본에 추가 확인한다. 종료 뒤에도 소유자의 이미 생성된 이벤트 ack는 세션 상태를 바꾸지 않고 허용하는 안이다. 권한은 매 요청 fresh 검증한다.

### 통합 선행 관계와 담당

#182 서버 구현 완료만으로 실제 앱 알림이 동작하지 않는다. 다음은 #182의 연결 작업 추적표이며 독립 이슈를 승인 없이 만들지 않는다. 담당은 역할 기준이며 실제 개인을 임의 지정하지 않는다.

| 연결 작업 | 담당 | 선행 관계/완료 근거 |
| --- | --- | --- |
| P1 전경 context·담당 기기·유효성 계약 확정 | Backend(본인) + Desktop/Extension 담당 | 공개 계약/정본 합의 선행. #182의 production 판정 gate 해제에 필수 |
| P2 Desktop context 발급/전환/계속 유효 신호, Extension 참조·지속 counter | Desktop/Extension + shared-types 담당 | P1 후 소비자 구현. 기존 #182 및 observation-order 인계와 연결, 관련 FE 이슈 확인 후 담당자 등록 |
| P3 전송 성공/실패·재시도 receipt 계약과 서버 수신 경로 | Backend(본인) + Desktop 담당 | 정본·별도 Issue 생성 승인 필요. #182 cooldown gate 해제 선행 |
| P4 실제 채널 호출 결과 보고·receipt 복구 | Desktop 담당 | P3 후 구현. GET·seen Set·사용자 ack를 전송 성공으로 간주하지 않음 |
| P5 heartbeat 최소 평가 trigger/세션 전이 경계 | Backend(본인) + 세션 API 담당 | #167/PR #178 기존 heartbeat 재사용, #163/PR #164·#168과 경계 맞춤 |
| P6 실제 AI 판정 응답 | AI 담당, #154 | Policy/API 합성 테스트의 선행 아님. 실제 AI→포키 QA의 선행 |
| P7 목표 직접 설정→집중→실제 AI→포키→종료→리포트 QA | 각 담당 공동, 본인은 Backend만 | P1~P6 및 종료/리포트 API 연결 후. #182 서버 합성 QA와 별도 완료 판정 |

P1/P3 계약이 없는 현재 코드는 production 후보 생성 보류가 기본이며 합성 입력으로 내부 정책과 영속화·API를 검증한다. 새 API/Frontend 변경을 완료한 것으로 표시하지 않는다. P1~P4를 단순 '나중 개선'으로 취급하지 않고 실제 알림 연동의 필수 차단 항목으로 관리한다.

### 사용자 동작/소비 계약 승인 대상과 권고안

**A. 기기 선택 — Desktop의 명시적 전경 context 위임 권고.** Desktop이 전경 전환에 단조 context 세대를 발급하고, 현재 입력 담당(Desktop 또는 연결 Extension)과 함께 서버에 알리는 계약이 필요하다. Extension 분석은 그 context를 참조한다. 서버는 선택된 기기의 findCurrent만 사용하며 다른 context/기기의 늦은 결과는 상태를 바꾸지 않는다. 보호 상태는 같은 담당 기기의 더 높은 유효 관측 또는 Desktop의 명시적인 새 context 전환에서만 해제한다. 기기 observedAt/receivedAt/서로 다른 counter 비교나 임의 TTL 경과로 해제하지 않는다.

- 현재 관측 요청에는 이런 위임/공통 context가 없다. Desktop은 Chrome 전경일 때 관측을 보내지 않고, 같은 화면 15초 유지 후 한 번만 보낸다. 따라서 짧은 관측 TTL만 넣으면 GENTLE 180초 조건이 영원히 성립하지 않을 수 있으며 heartbeat 생존만으로 화면 최신성도 증명할 수 없다.
- 전경 context의 계속 유효함/미관찰 전환도 명시 신호가 필요하다. 팀원 heartbeat에 선택 필드를 제안하거나 별도 관측 제어 계약을 마련해야 하며, 이번 서버 작업이 임의로 팀원 API를 바꾸지 않는다. 공개 필드명/전송 경로는 담당자 합의·정본 변경 전 확정하지 않는다.
- 소비자 연결 전에는 안전하게 판정 보류한다. 엔진/두 API와 합성 context 입력 테스트는 진행할 수 있지만 이 상태를 실제 앱 개입 완료로 보지 않는다. 서버만으로 실제 기기 간 순서를 복구할 수 있다는 약속은 하지 않는다.

**B. 실제 전송 — 별도 delivery 영수증 권고.** Desktop이 선택된 채널로 전송을 시도해 성공한 뒤 eventId로 전송 확인을 보고하는 계약을 분리한다. 사용자 ACKNOWLEDGED와 다른 사실이며 '사용자가 봤다'는 보장은 아니다. macOS 표시 보장은 OS API로 알 수 없는 점도 계약에 명시한다. 이 receipt는 현재 두 API에 없는 사용 사례다. 별도 후속 이슈/API로 제안하고, 기존 local-events에 섞거나 ACK action을 확장하지 않는다. 새 이슈는 추가 승인 전 생성하지 않는다.

- 서버는 검증된 첫 receipt 수신 시각을 cooldown의 보수적인 기준으로 쓰는 안을 권고한다. 기기 시각을 서버와 빼지 않으며 실제 전송보다 늦게 시작해 쿨다운이 짧아지지 않도록 한다. 이 '확인 수신 기준'을 정본에 승인받고 명시해야 한다. 지연/유실에 따른 과도한 보류 가능성도 인계한다.
- receipt 전에는 해당 세션+타입에 PENDING 하나만 유지해 무한 생성을 막는다. receipt 중복은 cooldown을 연장하지 않는다. GET/생성/사용자 ack를 전송 확인으로 대체하지 않는다.
- **확인했어 이후 5분 재집중은 Desktop UI만 담당하며 서버 저장/정책에 추가하지 않는다.**
- receipt 후 5분이 지났어도 기존 후보를 자동 발송하지 않는다. cooldown 경계 이후의 새 적격 관측을 기준으로 재평가하고, 이미 폐기한 후보를 heartbeat만으로 되살리지 않는다.

**C. rolling 경계 (2026-11-09 사용자 승인, Notion 반영 대기):** 평가 시각 t에서 창은 (t-10분, t]다. 해당 창과 겹치는 서로 다른 UNRELATED 구간별로 창 안의 시간만 잘라 합산하고, 강도별 반복 최소 시간의 엄격한 >를 충족한 구간을 한 번 센다. 열린 구간도 현재까지의 창 내부 시간이 기준을 넘으면 1회 포함한다. 정확히 창 왼쪽 경계에서 끝난 구간은 제외한다. 연속 조건은 현재 열린 구간 전체 길이로 별도 평가한다. 예: NORMAL에서 30.000초 구간은 0회, 30.001초 구간은 1회다. 이 경계 선택은 사용자에게 보이는 발생 시점을 바꾸며 이번 사용자 응답에서 승인됐다.

**D. PENDING 만료 (30초 사용자 승인, Notion 반영 대기):** 생성 후 30초가 지나거나 보호·UNCERTAIN·RELATED·선택 context 변경·RUNNING 이탈을 확인하면 아직 미전송인 PENDING을 EXPIRED로 바꾼다. 10초 poll의 지연/오프라인 뒤에 오래된 알림을 보여 주는 것을 막기 위한 제안이다. 30초는 이번 사용자 응답에서 승인됐다. 서버 GET이 유효 PENDING만 내려도 응답 직후 보호 전환과 클라이언트 표시 사이 경쟁은 남으므로 Desktop의 현재 context 재검증이 함께 필요하다. receipt 후 이미 전송된 알림의 수명과 재집중 UI는 별도이며 이 TTL로 닫지 않는다. EXPIRED 늦은 ack와 null actedAt 응답도 승인 때 함께 정한다.

- receipt가 유실되어 TTL 만료된 뒤 실제 전송 여부를 모르는 상태에서는 추가 생성하지 않는 '전송 미확인 보류'를 권고한다. 기한 후 무조건 재생성하면 실제 전송 기준 5분을 위반할 수 있다. 재조회/재전송 영수증 복구 계약까지 인계한다.

### 오류·QA 설계

- 기존 AUTH_RELOGIN_REQUIRED/SESSION_NOT_FOUND/VALIDATION_ERROR/TOO_MANY_REQUESTS를 의미에 맞게 재사용한다. intervention 미존재/비소유는 목록·식별자 노출을 피하도록 같은 404로 처리하되 정식 code(현재 enum에 없음)는 Notion 오류 카탈로그 대조 후 승인한다. NOT_FOUND의 현 문구는 '경로 없음'이므로 자원 없음에 무조건 재사용하지 않는다.
- 알 수 없는 policyVersion·깨진 options snapshot·context 불명·미전송 확인은 후보 없음으로 안전 처리하고 원문 없는 내부 reason만 기록한다.
- 합성 테스트에는 A/B에서 정한 순서·전송 상태를 명시적으로 주입한다. 실제 Desktop이 보내지 않는 필드를 이미 보내는 것처럼 QA를 작성하지 않는다.
- 단위: v1 각 경계·창 clipping·현재/과거 구간·메타/보호/AI 실패·policyVersion 고정·server clock 역행.
- PostgreSQL: 같은 trigger 동시 처리/두 instance 생성 경쟁/중복·상반 ack/보호와 완료 경쟁/전송 확인과 만료 경쟁/실패 rollback 및 outbox 재시도.
- 회귀: #172 freshness·권한/동의·observationSequence, 기존 heartbeat 응답/누적/ABANDONED/목표 도달.
- 수동 서버 QA는 격리 DB/포트에서 수행한다. 실제 AI→Desktop 포키 표시·전송 receipt·전경 context 연동 QA는 별도 미완료 항목으로 남긴다.

## Issue 본문

아래 내용으로 [Issue #182](https://github.com/What-a-move/FocusOn/issues/182)를 생성했다. 진행 체크리스트는 GitHub 본문을 기준으로 관리한다.

# 기능 또는 작업 요청

## 작업 유형

- [x] 기능 개발

## 담당 영역

- [x] Server

## Frontend 대상 앱

해당 없음. Desktop·Extension 코드는 소비 계약 확인만 하며 직접 수정하지 않는다.

## Labels

`area:server`, `type:feature`, `priority:mvp-core`

## 작업 제목

[Server] 화면 이탈 Policy Engine 및 개입 조회·확인 API 구현

## 작업 목적

PR #172에 저장된 최신 관측을 기존 heartbeat와 연결해 시간 기반 화면 OFF_TASK 개입을 생성하고 Desktop이 조회·확인할 수 있게 한다.

## 작업 내용

- 세션 고정 정책 버전으로 연속/rolling 10분 반복 이탈 조건 평가
- 관측 순서·보호·동의·실행 구간 검증, 개입 저장·중복 방지·cooldown·동시성 처리
- 기존 heartbeat의 유효 RUNNING 신호와 분석 예약/완료에 최소 연결
- GET /api/v1/sessions/{sessionId}/interventions
- POST /api/v1/interventions/{interventionId}/acknowledgements
- 기기 간 판정 선택·실제 알림 전송 확인 및 사용자 동작에 영향을 주는 미정 정책 검토

## 사용자 흐름 또는 재현 방법

1. 실행 중인 세션에서 합성 화면 분석 결과와 유효 heartbeat를 입력한다.
2. 정책 조건 충족 시 OFF_TASK가 한 번 생성되고 기존 Desktop 계약으로 조회된다.
3. 확인/닫기 기록은 멱등 반영하며 AI 판정·세션 상태·타이머를 바꾸지 않는다.

## 완료 조건

- [ ] 미정 사용자 동작·계약에 대한 승인과 필요한 정본 동기화
- [ ] Policy Engine·개입 영속화·조회/ack API 구현
- [ ] 개인정보 비저장·관측 순서·보호·임계값·동시성·중복 회귀 검증
- [ ] 격리 PostgreSQL QA, backend build·Checkstyle, 격리 리뷰
- [ ] 팀원 인계 및 합성 테스트/실제 AI·Desktop 연동 QA 결과 구분
- [ ] PR 생성·병합·Issue 종료 각각 별도 승인

## 예상 변경 파일

```text
server/src/main/java/com/capstone/backend/intervention/**
server/src/main/java/com/capstone/backend/analysis/** (최소 연결)
server/src/main/java/com/capstone/backend/session/service/SessionHeartbeatService.java (최소 연결)
server/src/main/resources/db/migration/V<최신 확인 후 확정>__*.sql
server/src/test/**
server/docs/**
```

## 예정 Branch

`feat/#<이슈번호>-intervention-policy` — 최종 기획·구현 승인 후 현재 checkout에서 최신 dev 기반으로 분기한다.

## 관련 문서

- 기획서: `server/docs/features/intervention-policy-PLAN.md`
- API 정본: [개입 조회](https://app.notion.com/p/24690cf40b1149419938913acd67d2fd), [ack](https://app.notion.com/p/fb8626ab4897450d95a6bfeddba674e6)
- 정책 정본: [화면 분석](https://app.notion.com/p/3de0b6e5e64c80c1a9e3d69db89f2ab8)
- 선행 #171 / PR #172, AI 연동 후속 #154, 팀원 종료 #163 / PR #164 및 연장 #168
- 관측 순서 인계: `server/docs/features/analysis-observation-order-handoff.md`

## 주의사항 및 결정 필요 사항

- 사용자 승인(2026-11-09): 이슈 생성·상세 기획 검토. 구현·브랜치·PR·병합·종료 승인은 별도다.
- 확인했어 이후 5분은 확정된 Desktop UI 동작이다. 서버 cooldown 재설계 대상으로 삼지 않는다.
- 서버 cooldown의 실제 전송 확인은 GET 조회·생성 시각으로 대체하지 않는다.
- 기기별 번호/시각을 비교해 Desktop·Extension 전체 최신 화면을 단정하지 않는다.
- 구간 저장·잠금·중복 ack는 내부 설계로 정리하고, rolling 경계·만료 등 사용자 동작에 영향을 주는 미정 항목만 승인받는다.
- #154 미완료여도 Policy/API와 합성 테스트 진행 가능. 실제 AI→포키 통합 QA 완료를 뜻하지 않는다.
- BREAK_SUGGESTION 생성·카메라·이력/local-events·리포트·학습 정리는 후속 범위다.
- 사용자·목표·세션 상태 명령은 팀원 담당. Frontend/shared-types/AI는 직접 수정하지 않는다.
- 개발 DB5432/서버8080·기존 IDE 파일·Claude outputs·stash를 보존한다.
- #171은 구현·병합·단계 완료 후 종료 승인 대기로 OPEN이다. 미완료 구현과 구분한다.

## 진행 체크리스트

- [x] 0. 최신 dev·기존 Issue/PR·코드·관련 정본 확인
- [x] 1. 사용자 승인 후 Issue 생성·실제 Label 확인
- [x] 2. 기획 초안 작성
- [x] 3. API·시간·관측·전송·담당 경계 검토
- [ ] 4. 검토 수정·필요 계약 승인/정본 동기화
- [ ] 5. 최종 기획 범위 확인
- [x] 6. 사용자 구현 진행 승인 (지적 2건 보완 후 내부 구현, 미정 계약 제외)
- [x] 7. 최신 dev 기반 Issue Branch 생성
- [ ] 8. 구현
- [ ] 9. 격리 리뷰·보완
- [ ] 10. 테스트·QA
- [ ] 11. 별도 승인 후 PR·CI·리뷰와 문서 반영

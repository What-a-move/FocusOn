# #182 실제 개입 연결 계약 검토안

상태: **2026-11-09 사용자 승인 계약**. 사용자가 #182 범위 확장, 새 endpoint/선택 필드, lease30초, 최초 SENT 수신 기준5분 cooldown, 전송 불명 보류와 Notion 동기화를 승인했다. PR 생성·병합·Issue 종료는 별도 승인이다. 구현/실제 앱 QA 완료를 뜻하지 않는다. Backend 구현 담당은 본인, Desktop/Extension/shared-types는 해당 담당자, heartbeat 변경은 세션 API 담당과 경계를 맞춘다.

## 해결할 실제 차이

- Desktop/Extension의 observationSequence는 서로 비교할 수 없다. 새 판정 접수 시각을 현재 화면의 증명으로 쓰지 않는다.
- 같은 화면은 한 번만 분석하므로 관측 TTL만 짧게 두면 60/180초 연속 정책이 성립하지 않을 수 있다. 전경의 계속 유효함을 분석 요청과 분리한다.
- GET 조회·ACKNOWLEDGED는 전송 성공이 아니다. 실제 OS/포키 호출 결과를 별도 receipt로 수집한다. 사용자가 봤다는 증명은 아니다.
- 기존 #182의 ForegroundPolicyContext는 빈 adapter다. 아래 계약과 소비자 연결이 없으면 production 생성 보류를 유지한다.

## P1: Desktop이 전경 담당을 명시적으로 위임

사용자 승인 구현 범위: #182 안에서 전경 context PUT/GET 2개와 기존 분석/heartbeat 선택 필드를 연결한다. 새 이슈는 생성하지 않는다.

### PUT /api/v1/sessions/{sessionId}/observation-context

- 제안 권한: Authorization + X-Device-Id, 현재 runner Desktop만 변경.
- 전경 앱/창이 바뀌거나 현재 대상을 확인할 수 없으면 15초 분석 대기 전에 즉시 보낸다. 앱 이름/번들 ID/창 제목/URL은 보내지 않는다.

```json
{
  "clientContextId": "uuid-v4",
  "foregroundSequence": 51,
  "observer": "EXTENSION"
}
```

- observer: DESKTOP / EXTENSION / NONE. EXTENSION의 실제 deviceId는 현재 활성 연결 기기에서 서버가 결정하며 클라이언트 임의 기기를 신뢰하지 않는다.
- foregroundSequence는 session+runner 전체의 JS 안전 양의 정수, 생성 시 증가·영속화. 작은 번호는 현재 context를 덮지 않고, counter 유실은 임의 초기화하지 않는다.
- 같은 clientContextId+동일 요청은 원래 결과를 반환한다. 같은 nonce/번호로 다른 요청은409 IDEMPOTENCY_CONFLICT다. 제어 요청은 원문이 없어 재시도할 수 있지만 번호/nonce를 바꾸어 새 전환으로 위장하지 않는다.
- 응답 data: observationContextId(BIGINT 문자열), observer, observerDeviceId(문자열/null), foregroundSequence, createdAt(서버 UTC), leaseRemainingMs.
- NONE은 즉시 미관찰로 전환해 현재 구간/미전송 후보를 차단한다. 다른 기기의 old context 결과가 보호를 풀지 않는다.
- 명시적 새 context 전환은 기존 연속 구간을 끝내되 과거 rolling 구간은 보존한다. 세션 실행 세대(lastResumedAt)가 바뀌면 이전 context는 무효다.

### GET /api/v1/sessions/{sessionId}/observation-context

- 제안 권한: Authorization + X-Device-Id, runner Desktop 또는 그 활성 연결 Extension의 같은 세션만 조회. 타인/다른 Desktop은404 SESSION_NOT_FOUND.
- 유효 context가 없거나 lease가 끝났으면204. 있으면 위 최소 필드만 반환한다. 조회로 lease를 연장하지 않는다.
- Extension은 자신의 deviceId가 담당일 때만 그 context ID를 관측 요청에 연결한다. context 없음/담당 아님은 원문 수집·정책 제출 근거가 아니다.

### 기존 분석 요청의 추가 선택 필드

`POST .../analysis-runs`의 observationContextId(문자열)를 선택 필드로 제안한다. observationSequence와 별개다. 기존 요청은 #172 호환을 유지하되 context 미연결 이력으로만 처리하고 새 production 정책 근거로 승격하지 않는다. 원문이나 내부 AI schema에 context를 복제하지 않는다.

서버는 context의 session/runner/담당 device/실행 세대를 확인하고 run과 context 참조를 저장한다. 현재 기기의 findCurrent 일치에 더해 해당 run이 현재 context에 속해야 정책을 갱신한다. 이전 context의 늦은 완료·guard 해제·낮은 기기 번호는 현재 상태를 바꾸지 않는다.

### 기존 heartbeat와의 최소 접점 (세션 담당 검토 필수)

- 제안 선택 필드: request.observationContextId. Desktop이 실제 전경 확인을 계속 성공하고 local context가 서버 context와 일치할 때만 보낸다. context 전환 요청이 진행 중이거나 전경을 모르면 이전 ID로 keepalive하지 않는다.
- 기존 유효 RUNNING heartbeat commit에서만 해당 context lease를 연장한다. 무시된 재전송·오류·목표 도달·종료·미확인 context는 연장하지 않는다. 타이머·누적 시간·명령 version 계약은 변경하지 않는다.
- **승인 lease는30초(3주기)**다. 개입 TTL30초와 별도 정책이다. context lease 만료는 미관찰로 구간을 끊고 생성/표시를 차단한다. 끝난 lease를 지연 heartbeat만으로 되살리지 않는다.
- 연결 복구 때 Desktop이 전경을 다시 확인해 더 큰 foregroundSequence의 새 context를 제출한다. 같은 화면을 길게 봐도 context keepalive가 있으므로 화면 재분석으로 구간을 초기화하지 않는다.
- 내부 Binding에는 validUntil 등 서버 기준 유효 끝 시각을 추가해야 한다. lease 만료 후 heartbeat 시각까지를 이탈로 합산하지 않고 마지막 유효 시각까지만 닫는다. 현재 Optional.empty adapter를 단순 교체하는 것만으로 이 경계를 생략하지 않는다.

## P3: 실제 전송 receipt

사용자 승인 구현 범위: #182 안에서 아래 receipt POST 1개와 이벤트/cooldown을 연결한다. 실제 표시 QA는 Desktop의 P1 소비/표시 경쟁 보호 연결 후다.

### POST /api/v1/interventions/{interventionId}/deliveries

권한 제안: Authorization + X-Device-Id, 해당 세션 runner Desktop. 기존 acknowledgements는 사용자 반응으로 유지한다.

```json
{
  "clientDeliveryId": "uuid-v4",
  "dispatchStatus": "SENT",
  "channels": ["MOLE_UI"]
}
```

- SENT: 채널 중 하나 이상에서 포키 표시/OS 알림 제출이 성공한 것으로 client가 확인. 표시/전송 시도를 사용자 인지와 혼동하지 않는다. channels는 실제 성공 채널만, 세션 snapshot 허용 목록의 부분집합.
- NOT_SENT: 모든 선택 채널에서 전송하지 않았음을 확실히 아는 경우. UNKNOWN: crash/OS 결과 불명 등으로 실제 전송 여부를 증명할 수 없음. 두 상태의 channels는 빈 배열이다.
- 각 실제 전송 시도에 nonce를 한 번 배정해 영속화한다. 같은 nonce+동일 결과 재전송은 최초 응답/시각 유지, 다른 결과는409 IDEMPOTENCY_CONFLICT. 새 nonce로 같은 실제 시도를 중복 보고해도 최초 SENT만 cooldown 시작점으로 쓴다.
- data 제안: dispatchStatus, acceptedAt(서버 UTC), cooldownUntil(서버 UTC/null). 원문 대사·앱 이름·URL·키는 저장하지 않는다.
- 사용자 승인에 따라 최초 유효 SENT receipt 수신부터5분을 계산한다. 실제 전송과 수신 지연이 있을 수 있으며, 기기 시각을 서버 시각과 빼지 않는다. GET/생성/사용자 ack로 전송 시각을 채우지 않는다.
- receipt가 늦어 이미 EXPIRED/ACKNOWLEDGED가 된 이벤트라도 실제 전송 사실은 감사/중복 차단용으로 기록할 수 있다. 기존 사용자 status를 PENDING으로 되돌리지 않고 최초 ack 시각도 바꾸지 않는다.
- UNKNOWN/receipt 유실은 추가 생성을 보류한다. TTL만으로 미전송을 확정해 새 개입을 만들지 않는다. NOT_SENT 재시도와 UNKNOWN 복구/사용자 안내는 아래 승인 항목이다.

### 기존 GET interventions·Desktop 수정

- 추가 응답 필드 제안: observationContextId, remainingTtlMs. expiresAt과 기기 Date.now를 직접 비교하지 않는다.
- Desktop은 응답이 현재 세션·context에 속하고 여전히 RUNNING/비보호인지 표시 직전에 확인한다. HTTP 요청 시작 이후 monotonic 경과 시간을 remainingTtlMs에서 보수적으로 차감해 오래된 응답을 표시하지 않는다.
- 이미 SENT가 확인된 PENDING은 poll에서 다시 표시 대상으로 노출하지 않는 반환 의미를 제안한다. 기존 완료 status 이력과 DB 원본은 보존한다. 이 조회 의미도 사용자 승인 및 정본 반영 대상에 포함됐다.
- Desktop은 시도/결과/미전송 receipt를 영속화하고 같은 receipt를 재전송한다. 현재 in-memory seen Set만으로 재시작 뒤 중복을 막았다고 주장하지 않는다. crash 뒤 전송 여부 불명은 자동 재전송 대신 UNKNOWN으로 보고한다.
- DB 이벤트 생성·receipt의 멱등성은 보장하되 OS side effect와 DB transaction의 원자성을 주장하지 않는다. 네트워크 분리/프로세스 crash에서 실제 화면 표시 exactly-once는 별도 보장할 수 없다.
- 미전송 TTL30초와 보호/상태 전이 시 만료는 기존 승인 범위다. 실제 전송된 말풍선 수명이나 확인했어 이후5분 UI 상태를 서버 TTL로 변경하지 않는다.

## 사용자 승인 및 소비자 연결 범위

1. 위 새 endpoint/필드·권한·응답 의미와 P1/P3의 별도 이슈 등록 범위. #182에서 서버 API 구현 승인. 새 이슈는 생성하지 않으며 Frontend 직접 수정 없음.
2. 전경 lease30초와 만료 후 새 context 복구. 세션 heartbeat 담당자와 선택 필드/유효 신호 경계 확인.
3. 최초 receipt 수신 기준 cooldown, SENT의 OS 제출 성공 의미, UNKNOWN 보류/복구 UX. NOT_SENT만 확실한 경우에만 TTL 안 같은 이벤트로 재시도하는 안.
4. 기존 GET의 추가 필드와 이미 전송된 PENDING 재노출 방지 의미. Desktop 담당이 표시 직전 context/보호·monotonic TTL을 확인.

## 합의 뒤 검증

- context 낮은/중복 번호, context 전환 전후 늦은 AI·guard, Desktop/Extension 전환, lease 경계/지연 heartbeat/복구, pause/resume 및 unlink·동의 철회.
- 구간 pending은 보존/동결, 확정 UNCERTAIN은 종료. 오래된 context나 lease 밖 시간을 소급 합산하지 않음.
- 전송 전후 crash·동일/다른 nonce receipt·중복 채널·늦은 receipt·ack와 경쟁·TTL·cooldown 뒤 새 관측, 조회 응답 후 보호 전환.
- #190 실제 AI 0.6 판정과 일반 Desktop 실행을 사용한 포키 표시. 합성 입력/시연 모드와 구분.

## 구현 전 Notion 반영

- 전경 PUT: https://app.notion.com/p/4130b6e5e64c801da7bbd8a8159db5d1
- 전경 GET: https://app.notion.com/p/4130b6e5e64c80b88a53ff3bf6d38644
- 전송 receipt POST: https://app.notion.com/p/4130b6e5e64c80cbb9dac57f9723d9bd
- 기존 개입 GET의 Response·권한·비고를 새 필드/표시/cooldown 기준으로 수정. 화면 분석 정본 하단에 승인된 연결 정책 추가(기존 문단 유지).
- 새 API 상태 IMPLEMENTING, MVP, 인증·헤더·소비자·도메인 속성 및 본문 타입/예시/오류 기록. 실제 구현 검증은 후속이다.

## 구현 세부 경계

- PENDING은 사용자 반응 상태다. 최초 SENT 뒤에는 미전송 slot만 해제하며 30초 미전송 TTL로 사용자 PENDING을 만료시키지 않는다. 전송된 PENDING은 GET 표시 목록에서 제외하지만 ack는 받을 수 있다.
- lease 만료/실행·context 변경 뒤 도착한 SENT는 먼저 후보 수명을 만료한 뒤 전송 사실/cooldown만 기록한다. 기존 EXPIRED/사용자 반응 상태는 유지한다.
- context 없는 분석 이력은 정책 전이를 변경하지 않는다. findCurrent(session, device, context) overload로 같은 번호/순서 불명 우선순위를 적용해 정책 선택을 분리한다. 기존 기기 전체의 findCurrent와 counter 계약은 유지한다.
- 새 nonce와 최초 수신 시각은 PostgreSQL 마이크로초 정밀도로 고정해 첫 응답과 재전송 응답의 시각을 일치시킨다.

### 늦은 receipt와 표시 사이의 클라이언트 직렬화

- Desktop의 단일 전송 coordinator가 후보 GET의 수용/폐기, 표시 시도 journal, 비동기 채널 결과, receipt outbox·재시도를 하나의 직렬 흐름으로 관리한다. 다른 worker/process가 별도로 receipt를 보내거나 후보를 표시하지 않으며 단일 instance/공유 broker로 소유권을 보장해야 한다.
- 채널 callback 미완료·전송 여부 UNKNOWN·미확인 SENT receipt/outbox가 하나라도 있으면 다른 후보를 표시하지 않는다. NOT_SENT는 모든 채널의 미전송이 최종 확정됐을 때만 기록한다. 늦은 성공 callback 가능성이 남아 있으면 NOT_SENT로 확정하지 않는다.
- receipt 상태가 바뀌면 이전에 시작한 GET 응답/캐시 후보를 모두 무효화한다. SENT receipt 응답을 확정한 뒤에만 새 세대의 GET을 시작한다. 응답을 받은 뒤 표시까지 같은 coordinator의 barrier를 유지하므로 receipt 재시도/콜백이 중간에 끼어들면 응답을 버린다. 단순 GET 재조회만으로 경쟁이 사라졌다고 보지 않는다.
- 예: B 조회가 진행 중일 때 A의 SENT 결과/재전송이 생기면 B 응답은 폐기하고 표시를 막는다. A receipt가 확정된 뒤 새 GET을 하면 서버 cooldown이 B를 숨긴다. 프로세스 재시작 때도 journal/outbox를 먼저 복구하며 불명 상태를 임의 해제하지 않는다.
- 서버는 알려진 cooldown에서 후보를 숨기지만 이미 반환한 HTTP 응답/OS 표시를 취소할 수 없다. 위 소비자 coordinator가 구현·검증되기 전에는 실제 표시 경쟁이 해결됐다고 주장하지 않는다. OS side effect와 DB의 exactly-once 보장은 여전히 없다. 별도 서버 endpoint/클라이언트 시각 비교/확인했어 UI 정책은 추가하지 않는다.


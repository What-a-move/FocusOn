# 베타 테스트용 API 형식 (Desktop·Extension이 실제로 부르는 형식)

> 작성: 2026-11-02 · 작성자: jae1(Frontend)
> 목적: 베타 흐름(목표 → 집중 → 이탈 알림 → 종료 → 집중 리포트)에 필요한데 **서버에 아직 없는 API**를, 클라이언트 코드가 지금 보내고 읽는 형식 그대로 정리한다.
> 기준: `apps/desktop/electron/main/*`, `apps/desktop/electron/shared/*-contract.cts`, `apps/extension/extension/background/*`, `packages/shared-types`.
> 정본: Notion `FocusOn API 명세서`. 2026-11-02에 아래 API 전부를 Notion과 대조했고 **Desktop·Extension은 명세와 같다.** 명세에 빈칸이던 부분은 맨 아래 `확정 사항`대로 정해 Notion에도 적었다. **서버는 이 형식에 맞춰 주세요.**

## 공통

- 성공: `{ "data": ... }` 래퍼. 아래 "응답"은 모두 `data` 안의 값이다.
- 실패: `{ code, message, retryable, retryAfterSeconds, requestId, details }`. 클라이언트는 `code`와 `retryable`만 본다.
- 모든 요청: `Authorization: Bearer`, `X-Trace-Id`(UUID). `analysis-runs`는 `X-Trace-Id` = `clientRunId`.
- ID는 문자열(BIGINT 10진 문자열). 경로에 넣으므로 영문·숫자·`_`·`-` 64자 이하.
- 응답에 **아래에 없는 필드가 더 있어도 괜찮다.** 클라이언트는 필요한 필드만 읽는다. 반대로 "필수"로 적은 필드가 없거나 타입이 다르면 계약 위반(`INVALID_RESPONSE`)으로 처리한다.

## 우선순위 한눈에

| 순위 | API | 부르는 쪽 | 없으면 |
| --- | --- | --- | --- |
| 1 | `POST /sessions/{id}/end` | Desktop | 집중을 끝낼 수 없음 → 리포트 시연 불가 |
| 1 | `POST /sessions/{id}/pause` · `resume` (#132) | Desktop | 멈춤·재개·앱 재시작 복구 실패 |
| 1 | `POST /sessions/{id}/heartbeats` | Desktop | 서버가 세션을 살아 있다고 못 봄 |
| 1 | `GET /sessions/{id}` | Desktop | 버전 충돌 때 최신값으로 못 맞춤 |
| 1 | `POST /sessions/{id}/local-events` | Desktop·Extension | 권한 철회 자동 멈춤이 서버에 반영 안 됨 |
| 2 | `POST /sessions/{id}/analysis-runs` | Desktop·Extension | 화면 분석 전체 불가 |
| 2 | `GET /sessions/{id}/interventions` | Desktop | 포키 알림 불가 |
| 2 | `POST /interventions/{id}/acknowledgements` | Desktop | 알림 반응 기록 불가 |
| 3 | `GET /sessions/{id}/summary` | Desktop | 집중 리포트 불가 |
| 3 | `GET /reports/calendar?month=` · `GET /reports/daily?date=` | Desktop | 홈 달력·지난 목표·집중 기록 불가 |
| 베타 제외 가능 | `extend`, `learning-summary`, `goals/clarify` | Desktop | 연장 버튼·학습 노트·목표 AI 확인만 막힘 (목표는 직접 정하기로 저장됨) |

---

## 1순위 — 집중 진행

### 상태 명령 공통 (pause · resume · extend · end)

- 헤더: `Idempotency-Key`(명령마다 새 UUID, **실패 후 같은 의도로 다시 누르면 같은 키·같은 본문**), `If-Match-Version: {현재 version}`
- 성공 HTTP: **200**
- 응답 (공통):

```json
{ "version": 8, "status": "PAUSED", "accumulatedActiveMs": 734000, "targetDurationMs": 1800000 }
```

| 필드 | 필수 | 비고 |
| --- | --- | --- |
| `version` | ✅ | 정수 ≥ 0. 다음 명령의 `If-Match-Version`에 쓴다 |
| `status` | 선택 | 세션 상태 7개 중 하나. 없으면 Desktop이 명령에 맞는 상태로 둔다 |
| `accumulatedActiveMs` | 선택 | 있으면 서버 확정값으로 덮어쓴다 |
| `targetDurationMs` | 선택 | extend에서 쓰면 좋음 |

- Desktop이 따로 처리하는 오류 코드: `SESSION_VERSION_CONFLICT`(→ `GET /sessions/{id}`로 최신값 조회), `SESSION_STATE_INVALID`, `SESSION_NOT_FOUND`
- 필수 헤더는 Notion대로 `Authorization`·`Idempotency-Key`·`If-Match-Version`이다. **`X-Device-Id`는 보내지 않는다**(확정 1).

### `POST /sessions/{id}/pause`

```json
{ "pauseReason": "USER", "accumulatedActiveMs": 734000 }
```

- `pauseReason`: `USER` | `APP_RESTART`
  - `APP_RESTART`: 앱을 다시 켰을 때 서버 세션이 `RUNNING`이면 Desktop이 로컬에서 먼저 `AUTO_PAUSED`로 두고 이 요청을 보낸다.
- `accumulatedActiveMs`: Desktop monotonic clock 기준 정수 ms. 서버가 검증·확정한다.
- 허용 상태(Desktop 기준): `USER`는 `RUNNING`에서, `APP_RESTART`는 서버 `RUNNING`(로컬 `AUTO_PAUSED`)에서.

### `POST /sessions/{id}/resume`

```json
{ "readiness": { "screenAnalysisReady": true } }
```

- 이름은 Notion 확정(2026-10-25)대로 `readiness`다. 시작의 `startReadiness`와 이름이 다르지만 그대로 둔다(확정 2).
- 허용 상태: `PAUSED`, `AUTO_PAUSED`

### `POST /sessions/{id}/extend` (베타 제외 가능)

```json
{ "additionalDurationMs": 600000 }
```

- 5~300분, 1분 단위. 허용 상태: `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`
- `AWAITING_END_CONFIRMATION`에서 연장하면 Desktop은 바로 `RUNNING`으로 다시 센다.

### `POST /sessions/{id}/end`

- 본문 없음.
- 허용 상태: `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`
- 응답의 `accumulatedActiveMs`가 있으면 그 값으로 확정한다. 성공 후 Desktop은 `/session-result/?session={id}`로 이동해 `summary`를 부른다.

### `POST /sessions/{id}/heartbeats`

- 10초마다. 헤더: `X-Device-Id` (멱등 키·버전 없음)
- 보내는 상태: 활성 상태(`RUNNING`·`PAUSED`·`AUTO_PAUSED`·`AWAITING_END_CONFIRMATION`·`ENDING`)이고 명령 처리 중이 아닐 때

```json
{ "clientObservedAt": "2026-11-02T08:00:10.000Z", "accumulatedActiveMs": 744000 }
```

- 응답 (200, 둘 다 필수):

```json
{ "status": "RUNNING", "accumulatedActiveMs": 744000 }
```

- `409 SESSION_ABANDONED`면 Desktop이 세션을 `ABANDONED`로 끝내고 되살리지 않는다.

### `GET /sessions/{id}`

- `GET /sessions/active`와 **같은 형식**(결정 023, 이미 구현된 active 응답과 동일):

```json
{ "sessionId": "41", "status": "PAUSED", "version": 8, "goalTextSnapshot": "운영체제 3장 정리", "targetDurationMs": 1800000, "accumulatedActiveMs": 734000 }
```

### `POST /sessions/{id}/local-events`

- 헤더: `X-Device-Id`(Desktop은 Desktop deviceId, Extension은 Extension deviceId)
- 성공 HTTP: **200**. 응답 내용은 읽지 않는다.

```json
{ "localEventCode": "LOCAL_SCREEN_PERMISSION_REVOKED", "occurredAt": "2026-11-02T08:03:00.000Z" }
```

| 코드 | 보내는 쪽 | 상황 | 서버에 기대하는 것 |
| --- | --- | --- | --- |
| `LOCAL_SCREEN_PERMISSION_REVOKED` | Desktop | 집중 중 화면 기록 권한이 꺼짐. Desktop은 이미 로컬에서 `AUTO_PAUSED` | 세션을 `AUTO_PAUSED`로 맞춤 |
| `LOCAL_EXTENSION_HOST_PERMISSION_MISSING` | Extension | 사이트 접근 권한 없음 | 정책대로 기록 |

- `retryable` 실패면 다음 heartbeat 주기에 다시 보낸다. 그 밖의 실패는 버린다.

---

## 2순위 — 화면 분석 → 포키 알림

```text
Desktop(앱 이름·창 제목, 필요하면 OCR 글자) ┐
Extension(탭 제목·호스트, DOM 글자)         ┴→ POST analysis-runs → 서버: 제외 검사 → AI 판정 → 저장
                                               → Policy Engine: UNRELATED 구간이 강도별 기준 초과 → intervention 생성
Desktop: 10초마다 GET interventions → PENDING 하나를 포키로 표시 → POST acknowledgements
```

### `POST /sessions/{id}/analysis-runs`

- 헤더: `X-Device-Id`, `X-Trace-Id` = `clientRunId`
- 언제: 화면(맨 앞 앱·창 제목·탭)이 바뀔 때. **자동 재전송·대기열 없음**(실패하면 다음 변경부터 새로 보냄). `429`면 Desktop은 1분 쉰다.
- 요청 (`packages/shared-types`의 `AnalysisRunRequest`):

```json
{
  "clientRunId": "5f0c...-uuid",
  "contentSource": "APP_META",
  "observedAt": "2026-11-02T08:01:12.000Z",
  "sequence": 17,
  "guardStatus": null,
  "metadata": { "appName": "Xcode", "windowTitle": "Process.swift" }
}
```

| 필드 | 값 |
| --- | --- |
| `contentSource` | Desktop: `APP_META`, `DESKTOP_APP_OCR` · Extension: `TAB_META`, `CHROME_DOM` (`CHROME_VIEWPORT_OCR`은 P1) |
| `sequence` | **같은 세션 + 같은 contentSource 안에서** 1부터 증가. 앱을 다시 켜도 이어서 센다. 더 작은 값은 늦게 온 옛 결과로 보면 된다 |
| `guardStatus` | `EXCLUDED` \| `PRIVACY_BLOCKED` \| `null`. 값이 있으면 `metadata`·`contentPayload`를 **보내지 않는다** |
| `metadata` | Desktop: `appName`, `windowTitle`(null 가능) · Chrome: `appName`, `pageTitle`, `urlHost` |
| `contentPayload` | `{ "text": "..." }` — `DESKTOP_APP_OCR`·`CHROME_DOM`일 때만. **저장·로그·큐 금지**(일회성) |

- 응답 (200, 모두 필수):

```json
{ "analysisRunId": "903", "relation": "UNRELATED", "confidence": 0.86, "reasonCode": "SEMANTIC_GOAL_MISMATCH", "observedAt": "2026-11-02T08:01:12.000Z", "policyVersion": "v1" }
```

| 필드 | 값 |
| --- | --- |
| `relation` | `RELATED` \| `UNRELATED` \| `UNCERTAIN` \| `EXCLUDED` \| `PRIVACY_BLOCKED` |
| `confidence` | 0~1 또는 `null`(판정 없음). `< 0.75`는 서버가 `UNCERTAIN`으로 정규화 |
| `reasonCode` | `SEMANTIC_GOAL_MATCH` · `SEMANTIC_GOAL_MISMATCH` · `INSUFFICIENT_CONTEXT` · `AMBIGUOUS_CONTEXT` · `ANALYSIS_UNAVAILABLE` 또는 `null`. 모르는 값이 와도 받는다 |

- AI 장애·시간 초과는 **HTTP 오류가 아니라** `relation: "UNCERTAIN"`, `reasonCode: "ANALYSIS_UNAVAILABLE"`로 주면 된다(`UNCERTAIN`은 이탈로 세지 않음).

### `GET /sessions/{id}/interventions`

- 언제: 세션이 `RUNNING`일 때 10초마다.
- 응답: 배열 또는 `{ "interventions": [...] }` 둘 다 읽는다.

```json
[{ "interventionId": "77", "type": "OFF_TASK", "status": "PENDING", "channels": ["MOLE_UI", "MACOS_NOTIFICATION"], "createdAt": "...", "policyVersion": "v1" }]
```

| 필드 | 필수 | 비고 |
| --- | --- | --- |
| `interventionId` | ✅ | 문자열 |
| `type` | ✅ | `OFF_TASK`(이탈 알림) \| `BREAK_SUGGESTION`(휴식 제안). 그 밖의 값은 Desktop이 표시하지 않는다(확정 4) |
| `status` | ✅ | `PENDING`인 것만 표시. 한 번 표시한 ID는 다시 띄우지 않음 |
| `channels` | 선택 | `MOLE_UI`·`MACOS_NOTIFICATION`·`SOUND`. 비어 있으면 세션 시작 때 고른 채널을 쓴다 |

- 문구는 Desktop이 정한다. 서버는 화면 내용·근거를 넣지 않아도 된다.

### `POST /interventions/{id}/acknowledgements`

- 헤더: `X-Device-Id`. 성공 HTTP: **200**(응답 내용은 읽지 않음). 실패해도 다시 보내지 않는다.

```json
{ "action": "ACKNOWLEDGED" }
```

- `ACKNOWLEDGED`: `조금 더 보기`·`잠시 쉬기`(잠시 쉬기는 이어서 pause 요청) · `DISMISSED`: 닫기

---

## 3순위 — 집중 리포트·기록

### `GET /sessions/{id}/summary`

- 성공 200. `ENDED`·`ABANDONED` 세션.

| 필드 | 필수 | 화면 |
| --- | --- | --- |
| `sessionId` | ✅ | |
| `goalSnapshot` | ✅ | 제목. Notion 명세대로 이 응답만 `goalSnapshot`이다(다른 API는 `goalTextSnapshot`, 확정 3) |
| `targetDurationMs`, `accumulatedActiveMs` | ✅ | 목표·실제 집중 시간 |
| `status` | 선택 | `ENDED`/`ABANDONED` |
| `startedAt`, `endedAt` | 선택 | 시간대 표시 |
| `totalPausedMs`, `pauseCount`, `autoPauseCount`, `autoPauseReasons[]` | 선택 | 멈춤 |
| `relatedDurationMs`, `unrelatedDurationMs`, `uncertainDurationMs` | 선택 | 집중 흐름 막대 |
| `extensionCount`, `extendedDurationMs` | 선택 | 연장 |
| `interventionCount` | 선택 | 알림 횟수 |

- 선택 필드가 없으면 화면에 `기록 없음`으로 보인다. **베타에서는 `related/unrelated/uncertainDurationMs`와 `interventionCount`가 있어야** "화면 분석 → 리포트"가 보인다.

### `GET /reports/calendar?month=YYYY-MM`

```json
{ "month": "2026-11", "studyDayCount": 2, "days": [{ "date": "2026-11-02", "totalActiveMs": 3000000, "sessionCount": 1, "hasStudyActivity": true }] }
```

- `days[].date`는 모두 요청한 달이어야 한다. `hasStudyActivity` = `totalActiveMs >= 60000`.

### `GET /reports/daily?date=YYYY-MM-DD`

```json
{ "date": "2026-11-02", "totalActiveMs": 4800000, "sessions": [{ "sessionId": "41", "goalTextSnapshot": "운영체제 3장 정리", "startedAt": "...", "endedAt": "...", "accumulatedActiveMs": 3000000 }] }
```

- `API_CONTRACT.md`의 일간 항목 형식과 같다.

---

## AI 쪽에 필요한 것 (클라이언트 관점)

- 클라이언트는 AI를 직접 부르지 않는다. 서버가 `analysis-runs`를 받아 AI에 묻고, 위 응답의 `relation`·`confidence`·`reasonCode`로 돌려주면 된다.
- 판정은 `RELATED` / `UNRELATED` / `UNCERTAIN` 세 가지만 있으면 된다(AI 내부 `SUPPORTING`은 서버가 `RELATED`로 바꿈).
- AI 입력으로 쓸 수 있는 값: 목표 문구(서버 저장값), `metadata`(앱 이름·창 제목 / 탭 제목·호스트), `contentPayload.text`(있을 때만, 일회성).
- 서버 → AI는 `POST /internal/v1/analysis-runs` 하나만 부른다. 이 경로가 판정을 바로 돌려준다(아래 확정 사항 6).
- Desktop OCR은 정리한 글자(`contentPayload.text`)만 보낸다. `ocr.confidence`·`captureStatus`는 베타에서 보내지 않는다.

## 확정 사항 (2026-11-02, PM 결정 · Notion 반영)

1. **상태 명령 헤더:** pause·resume·extend·end는 `Authorization`·`Idempotency-Key`·`If-Match-Version`만 쓴다. `X-Device-Id`는 쓰지 않는다(Notion 명세 그대로). 기기 확인이 필요한 요청은 heartbeat·analysis-runs·local-events·acknowledgements·reports다.
2. **resume 본문:** `{ "readiness": { "screenAnalysisReady": boolean } }` (Notion 2026-10-25 확정 그대로).
3. **summary 제목 필드:** `goalSnapshot` (Notion 명세 그대로). 집계 필드 이름은 위 summary 표의 이름으로 확정한다.
4. **알림 목록:** 각 항목에 `interventionId`(문자열, acknowledgements 경로에 씀)를 넣는다. `type`은 베타에서 `OFF_TASK`·`BREAK_SUGGESTION` 두 가지다. 응답은 배열을 기본으로 한다(`{ interventions: [] }`도 Desktop이 읽음). 종료 확인은 heartbeat의 `AWAITING_END_CONFIRMATION`으로 처리하므로 알림 목록에 넣지 않는다.
5. **AI 실패·임시 판정:** AI 장애·시간 초과는 HTTP 오류가 아니라 `relation: UNCERTAIN`, `confidence: null`, `reasonCode: ANALYSIS_UNAVAILABLE`로 응답한다. 베타 일정까지 AI가 준비되지 않으면 서버가 **임시 규칙 판정**으로 대신할 수 있다. 조건은 다음과 같다.
   - 응답 형식은 같다.
   - `reasonCode`는 기존 목록 안에서 쓴다.
   - `policyVersion`은 세션 고정값(`v1`)을 쓴다.
   - AI가 준비되면 서버 안에서 판정기만 바꾼다. 클라이언트 변경은 없다.
6. **베타 판정 모드 (2026-11-03, Notion `AI 분석 계약` 4.3·4.4·9):** 보정된 confidence가 없어 AI가 늘 `UNCERTAIN`을 내면 이탈 알림이 생기지 않는다. 베타 동안은 다음처럼 한다.
   - AI는 `FOCUSON_ANALYSIS_DECISION_MODE=candidate`일 때 미보정 후보 판정을 `decision`으로 내고, `promptVersion`에 `-uncalibrated`를 붙이며 `confidence`는 `null`이다.
   - 서버는 `-uncalibrated` 결과에만 0.75 정규화와 Policy Engine의 `confidence ≥ 0.75` 조건을 적용하지 않고 판정을 그대로 쓴다.
   - 그대로인 규칙: 앱 이름·탭 제목만 근거인 `UNRELATED`는 이탈에 넣지 않는다. AI 실패·시간 초과(목표 3초)는 `UNCERTAIN` + `ANALYSIS_UNAVAILABLE`이다.
   - 보정이 끝나면 모드를 끄고 예외를 지운다. 클라이언트 변경은 없다.

# Frontend 전달: 전역 관측 번호 (PR #172)

기존 source별 sequence만으로는 기기 시계 보정 뒤 TAB_META/DOM/앱/OCR 전체 순서를 비교할 수 없어 Notion API·AI2.2·ERD에 observationSequence를 추가했습니다. 서버 변경 PR172와 함께 아래 전송을 맞춰 주세요. 이 문서는 인계이며 Frontend 코드를 수정한 결과가 아닙니다.

```json
{
  "clientRunId": "uuid-v4",
  "contentSource": "CHROME_DOM",
  "observedAt": "2026-11-08T10:00:00Z",
  "sequence": 2,
  "observationSequence": 17,
  "guardStatus": null,
  "metadata": { "urlHost": "example.com", "pageTitle": "synthetic" },
  "contentPayload": { "text": "ephemeral input" }
}
```

- **기존 sequence는 source별로 유지**, 새 observationSequence는 **같은 session+device의 모든 source를 합쳐** 1부터 증가합니다. Desktop Main은 APP_META/DESKTOP_APP_OCR, Extension worker는 TAB_META/CHROME_DOM을 각각 하나의 counter로 관리합니다. 두 기기의 counter를 합치는 것은 아닙니다.
- 실제 관측을 생성할 때 번호를 원자적으로 배정·영속화하세요. 서로 다른 비동기 수집 경로가 동시에 번호를 배정해 중복되지 않게 합니다. 오래된 수집 결과에 전송 시점의 새 번호를 붙이면 안 됩니다. DOM/OCR 후속 수집도 현재 화면임을 확인한 새 관측에 새 번호를 줍니다.
- 같은 세션에서는 pause/resume·앱/worker 재시작에도 계속 증가합니다. 최대9007199254740991(JS 안전 정수). 시계나 Date.now()로 만들지 않습니다. counter가 유실되면 같은 세션에 임의1을 보내지 않고 명시적으로 새 세션으로 복구합니다.
- 재전송은 같은 clientRunId·observationSequence이며, 기존처럼 POST 자동 재시도·원문 큐는 만들지 않습니다. 같은 전역 번호에 새 UUID를 보내면409 IDEMPOTENCY_CONFLICT입니다.
- 공유 타입 AnalysisRunRequest에 선택 observationSequence:number를 추가하고 각 앱의 송신부·counter 저장·실패/재시작 테스트를 함께 반영해 주세요. AI로 직접 보내거나 서비스 키를 앱에 넣지 않습니다. 응답 필드는 바뀌지 않습니다.
- 번호를 보내기 시작한 session+device는 번호 없는 요청으로 되돌릴 수 없습니다. 번호 없는 요청을 받아도 현재 판정을 갱신하거나 AI를 호출하지 않습니다.
- 기존 설치본도 당장 요청할 수 있지만 시계 역행/지연으로 순서가 불확실해지면 해당 session+device는 AI 판정을 보류합니다. 일반 관측은 UNCERTAIN/ANALYSIS_UNAVAILABLE, 기존 EXCLUDED/PRIVACY_BLOCKED는 유지합니다. 번호가 있는 새 관측 또는 새 세션으로 복구합니다.
- guard 없는 TAB_META/CHROME_DOM 본문은 유효 urlHost가 필수이며 누락은400입니다. guard 요청에는 제목/본문 없이 guardStatus만 보냅니다.
- observedAt은 원본으로 유지하세요. Backend는 번호로 최신을 판단하고 server receivedAt·세션 수명 시각과 기기 시각을 혼용하지 않습니다. pause/resume·화면 변경 시 이전 수집 작업/전송 의도를 폐기하는 기존 클라이언트 방어도 유지합니다.

실제 Desktop·Extension 적용/QA는 아직 이 Backend 작업에서 수행하지 않았습니다. 서버는 구형 호환·번호 모드·혼합 요청을 합성 HTTP와 PostgreSQL로 검증합니다. Notion 정본: https://app.notion.com/p/58cf7aade6614fc9a00e248affb052f9

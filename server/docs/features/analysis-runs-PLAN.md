# 화면 분석 요청·결과 저장 기획 (#171)

- 작성/보완: 2026-11-08. 사용자 담당6번, MVP 핵심.
- Branch: feat/171-analysis-runs / PR #172, 기준 dev91cd524.
- 사용자 승인: 다음 작업 진행 및 후속 관측 순서 결정을 문서에 근거해 판단·반영하도록 위임했다.
- 정본: [POST analysis-runs](https://app.notion.com/p/58cf7aade6614fc9a00e248affb052f9), [AI 계약2.2](https://app.notion.com/p/3de0b6e5e64c80fbb0bccb985e38002c), [ERD6.1/7.1](https://app.notion.com/p/3de0b6e5e64c80a4bf5ef7ff3ac01f17), [테이블 카탈로그](https://app.notion.com/p/777b158c982f4769b2f2f2e292afc48e).
- 위 정본에 2026-11-08 관측 순서 보완을 브라우저로 먼저 반영·확인했다. 공개 응답과 AI 내부 schema는 유지한다.

## 범위

POST 한 개의 입력 검증·권한·Privacy Guard·AI adapter·결과 저장. 개입/heartbeat Policy Engine/리포트/보관 삭제 worker는 후속이다. 팀원 세션 상태 명령·Frontend·공유 TS·AI 코드는 수정하지 않는다.

## API

`POST /api/v1/sessions/{sessionId}/analysis-runs`, Bearer + X-Device-Id 필수.

```json
{
  "clientRunId": "4f1a2d24-b3fd-4b62-874a-8f8650aa26c9",
  "contentSource": "CHROME_DOM",
  "observedAt": "2026-11-08T10:00:00Z",
  "sequence": 2,
  "observationSequence": 17,
  "guardStatus": null,
  "metadata": { "appName": "Google Chrome", "pageTitle": "synthetic", "urlHost": "example.com" },
  "contentPayload": { "text": "ephemeral input" }
}
```

- sequence: 기존 source별 양의 정수 유지. observationSequence: 선택 양의 정수, 최대9007199254740991(JS 안전 정수). 같은 session+device의 모든 source에서 관측 생성 순서대로 증가하고 재시작 후에도 유지한다. 실제 관측 생성 때 배정하며 이미 오래된 수집 결과를 늦게 전송하면서 새 번호로 만들지 않는다.
- clientRunId는 UUID v4. guard는 EXCLUDED/PRIVACY_BLOCKED만 허용하고 metadata/contentPayload를 함께 보내면400이다.
- MVP source: APP_META, TAB_META, CHROME_DOM, DESKTOP_APP_OCR. metadata source의 본문은 받지 않는다. guard 없는 TAB_META 및 CHROME_DOM 본문에는 유효 host가 필수다. text 최대2,000자.
- 200 data: analysisRunId/analysisStatus/relation/confidence/reasonCode/observedAt/policyVersion. policyVersion은 세션 snapshot이다.
- 400 VALIDATION_ERROR, 기존401, 소유권 포함404 SESSION_NOT_FOUND, 상태409 SESSION_STATE_INVALID, 중복409 IDEMPOTENCY_CONFLICT, 429+Retry-After.
- 중복 범위: session+clientRunId 및 session+device+observationSequence. 같은 번호로 새 UUID를 만들거나 source별 번호를 전역 번호로 재사용하면409다.
- 한도는 소유 세션+기기8회/분이며 인증 일반60회/분과 함께 적용한다. 중복 요청도 분석 한도에 포함한다.

## 관측 순서와 시각

1. **번호 모드:** observationSequence가 있는 이력 중 가장 큰 번호가 현재 상태다. source·기기 벽시계·AI 완료 순서와 무관하다. 작은 번호는 이력만 남고 AI/현재 판정에 사용하지 않는다. 같은 기기에 번호 모드가 한번 들어오면 번호 없는 요청은 현재 후보에서 제외한다. nullable 필드는 기존 설치본을 받기 위한 호환성이고 downgrade 허용을 뜻하지 않는다.
2. **기존 모드:** 전역 번호가 없으면 source별 가장 큰 sequence 이하 요청은 명백한 과거로 분리한다. 나머지는 observedAt으로 비교한다. 더 새 source 순서인데 시각이 현재 관측보다 뒤로 가면 시계 역행/지연을 구별할 수 없어 해당 session+device를 순서 불명으로 취급한다.
3. **기존 모드의 순서 불명:** AI를 호출하지 않고 UNCERTAIN/ANALYSIS_UNAVAILABLE로 기록한다. 이전 보호 상태는 일반 관측이 완화할 수 없다. PRIVACY_BLOCKED가 EXCLUDED보다 우선하며 새 보호 신호를 적용한다. 이 안전 모드의 현재 조회는 과거 미래 시각이 아닌 안전 이력의 접수 ID 순이다. 명백히 오래된 source sequence는 이 상태도 바꾸지 않는다. 전역 번호가 있는 관측 또는 새 세션에서 복구한다.

observedAt은 원본으로 보존하고 receivedAt은 서버 수신 시각이다. 서로 다른 clock을 비교해 요청을 거절하지 않는다. 번호를 벽시계로 추정·보정하지 않으며 source별 counter를 전역 counter로 잘못 합치지 않는다. 서버 수명·학습 시간에는 서버 시각을 사용한다.

현재 관측 조회는 `AnalysisRunRepository.findCurrent`만 사용한다. raw observedAt 내림차순으로 현재를 다시 고르면 시계 보정 문제를 재도입한다. 원문 없이 관측별 이력을 보존하며 늦은 AI 완료는 자기 행만 갱신한다.

## 저장·동시성

- V14 analysis_runs: 세션/기기, clientRunId/source/observedAt/receivedAt/sequence, 안전 결과·model/prompt/policy 버전만 저장한다. 원문·제목·host·AI 전문·traceId는 저장하지 않는다.
- V15: observation_sequence BIGINT NULL, current_eligible BOOLEAN true, ordering_untrusted BOOLEAN false. 번호 unique와 양수/JS 안전 범위 CHECK. 순서 불명 이력은 현재 후보·번호 없음·비확정 관련성만 허용한다.
- V15 index: 기기별 전역 번호·순서 불명 최신·source별 sequence. 기존 V14 이력/시간/판정은 변경하지 않는다.
- 사용자 잠금 아래 준비·완료 transaction을 분리한다. 같은 계정의 동시 요청/연결 변경을 직렬화하되 AI 동안 잠금을 유지하지 않는다. 번호 중복은 잠금 아래 확인하고 DB unique로도 보장한다.
- runner Desktop 또는 그 Desktop의 활성 연결 Extension만 허용한다. 완료 때 family/기기/동의/실행 구간을 다시 확인한다. 서버 lastResumedAt snapshot끼리 비교하며 heartbeat로 version만 증가한 것은 구간 변경이 아니다.
- session+device의 전역 번호이지 여러 기기나 여러 세션을 합친 하나의 번호가 아니다. 앱 재시작/잠깐 pause/resume에도 같은 세션이면 계속 증가한다. counter 유실 시 같은 세션에서 임의로1로 초기화하지 않고 새 세션을 사용한다.

## AI·로깅

- AI_ANALYSIS_URL/FOCUSON_AI_API_KEY: 서버 전용. 정식 POST /internal/v1/analysis-runs, 같은 traceId, connect2초·전체 응답5초·8KB 상한·미완료 future cancel, 애플리케이션 자동 재시도 없음.
- request/context 내부에는 새 observationSequence를 전달하지 않는다. 서버가 current 순서를 검증하고 기존 isActiveTarget을 제공한다.
- AI 미설정/장애/receipt/schema 오류 및 검증 불가 순서는200 UNCERTAIN/null/ANALYSIS_UNAVAILABLE다.
- confidence0.75 및 2026-11-03 Notion의 -uncalibrated/null 예외. metadata UNRELATED는 effectiveRelation에서 UNCERTAIN으로 처리해 이탈 근거로 쓰지 않는다.
- SafeApiLog의 고정 gate/ai/store 이벤트만 사용한다. 원문·외부 예외 메시지를 남기지 않는다.

## 검증·인계

- 기존 입력·권한·보호·중복·한도 회귀와 새 전역 번호의 시계 역행/역순 도착/다른 source/늦은 AI 완료/동시 중복을 검증한다.
- 기존 시계 역행의 AI 미호출·보호 유지·번호 모드 복구, downgrade 차단·scope 분리·전역 번호 형식·AI 내부 schema 보존을 검증한다.
- PostgreSQL V14→V15 이력 보존과 새 CHECK/unique를 검증하고 전체 build/Checkstyle·격리 리뷰·QA를 기록한다.
- 실계정·실제 AI/앱 통합은 별도다. 현 AI 정식 경로가 아직 receipt만 반환하면 안전 fallback이며 #154 반영 후 실제 판정을 QA한다.
- 구간 식별자가 없는 요청이 재개 후 처음 도착하는 경우의 실제 캡처 구간을 서버가 완전히 증명한다고 주장하지 않는다. 클라이언트는 pause/resume·화면 이탈 시 오래된 수집/전송 의도를 폐기한다. 번호는 인증·실제 활동 검증 수단이 아니다.
- PR #172에 반영하며 merge/이슈 종료는 별도 승인이다. 세션 종료 후90일 분석 이력 삭제 worker는 이번 범위 밖이다.

## PR172 재리뷰 보완 (2026-11-09)

- DOM 추출 본문이 없어도 metadata가 있으면 host 검증과 제외 검사를 거친다. 입력 시각은 서버 시각과 비교하지 않되 PostgreSQL17의 유한 timestamp 범위와 JDBC의 microsecond 반올림 가능 범위를 검증한다. 범위 밖은 예약/AI 전에400이다.
- 준비·완료 transaction은 REQUIRES_NEW이며 시작 시 EntityManager snapshot을 비워 OSIV 설정과 무관하게 소유권·family·동의·세션 실행 구간을 새로 조회한다. 원문은 transaction 밖 AI 호출에만 사용한다.
- 낮은 구형 source 번호를 새 관측으로 임의 수용하지 않는다. Counter 유실과 지연 요청은 서버 요청만으로 구별할 수 없어 Frontend의 지속 전역 번호 또는 새 세션 복구를 유지한다.

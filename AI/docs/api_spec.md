# AI API 계약 초안

## 문서 상태

- 상태: 팀 검토용 초안, 미구현
- 버전: `0.3.0-draft`
- 기준일: 2026-09-22
- 검토 필요: Server 담당자, Frontend(`apps`) 담당자, Shared 타입 담당자
- 관련 문서: [개발 규칙](DEVELOPMENT_RULES.md), [목표·세션 모델](goal_session_spec.md), [콘텐츠 수집 계약](content_acquisition_spec.md), [상태 모델](state_model.md), [피드백 명세](feedback_personalization_spec.md), [노트 명세](session_note_spec.md), [데이터 수명](data_lifecycle.md)
- 확정 조건: 호출 경로, 인증, Timeout, 요청 필드, 상태값, 캐시 정책을 관련 담당자가 확인한 뒤 AI `DECISION_RECORD.md`에 확정 결정을 남긴다.

이 문서는 FocusOn Server와 AI 서비스 사이의 목표 구조화, 페이지 관련성 분석과 학습 노트 생성 계약을 제안한다. 외부 클라이언트 계약의 원본은 Notion `FocusOn API 명세서`이며, 이 문서는 공개 경로·응답·오류를 새로 정의하지 않는다. 내부 경로, 서비스 인증, 필드와 상태 변환은 관련 담당자 합의 후 `DECISION_RECORD.md`에 확정한다.

## 1. 책임 경계

```text
Extension / macOS 네이티브 모듈
  → 제외·권한 검사
  ├─ Chrome: DOM 또는 Extension 내부 로컬 OCR
  └─ macOS Desktop: 허용된 활성 앱 화면의 Apple Vision OCR
  → 로컬 개인정보·품질 검사
  → Spring Server
      → 인증·사용자·목표·세션·회차 소유권 검증
      → 최신 goalVersion과 제외·기록 설정 확인
      → AI 서비스 호출
          → 목표 구조화·관련성·흐름·노트 생성
      → 결과 검증·저장·시간 집계
  → Client가 표시·알림·사용자 선택 실행
```

- Client는 AI 서비스를 직접 호출하지 않는다.
- AI는 로그인, 사용자 권한, 최종 시간 집계, 브라우저 차단과 영구 저장을 직접 담당하지 않는다.
- AI는 원본 화면·카메라 영상·전체 DOM·OCR 원문을 입력받지 않는다.
- ColPali 이미지 경로는 현재 기본 API에 포함하지 않는다. 별도 PoC에서 실행 위치와 개인정보 정책을 먼저 검증한다.

## 2. 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 형식 | `application/json`, lower camel case |
| 시각 | ISO 8601 UTC |
| 식별자 | UUID 문자열을 기본으로 하며 실제 형식은 Server 계약에서 확정 |
| 호출자 | Spring Server |
| 인증 | 서비스 간 인증 방식 검토 필요; 비밀 값은 환경변수·비밀 관리 시스템 사용 |
| Timeout | 기능별 예산 검토 필요; Timeout 시 현재 판정 적용 금지 |
| 응답 | 내부 응답을 Server가 Notion 공개 계약의 성공·오류 형식으로 변환 |
| 버전 | API·분석기·모델·Prompt·추출기·정책 버전 분리 |

### 2.1 요청 식별자

페이지 분석 요청은 다음 값을 사용한다.

- `requestId`: API 중복 요청 추적
- `eventId`: 관찰 이벤트 멱등성 처리
- `sessionId`: 목표별 논리 학습 세션
- `runId`: 실제 공부 회차
- `goalId`와 `goalVersion`: 목표와 해석 버전
- `navigationId`: 현재 탭 탐색 버전

응답 적용 전에 Server와 Client가 현재 값과 다시 비교한다. 불일치하면 `STALE_EVENT`로 폐기한다.

## 3. 제안 Endpoint

| 기능 | 공개 Server 경로 | 내부 AI 경로 | 상태 |
| --- | --- | --- |
| 목표 명확화 | `POST /api/v1/goals/clarify` | `POST /internal/v1/goals/clarify` | 공개 경로는 Notion 확정, 내부 경로 제안 |
| 페이지 관련성 분석 | `POST /api/v1/sessions/{sessionId}/analysis-runs` | `POST /internal/v1/analysis-runs` | Notion 기준 |
| 사용자 관련성 판정 수정·무효화 | MVP 공개 경로 없음 | Server 소유권 검증 후 후속 검토 | P1 이후 제안; Notion API 계약 변경 전 구현 금지 |
| 학습 정리 생성 | 세션 종료 후 비동기 작업 | `POST /internal/v1/learning-summaries` | 내부 경로 제안; MVP 응답은 `overview`, `keyConcepts`, `nextStartPrompt`만 사용 |

기존 `/api/v1/analyze/relevance`는 레거시 경로 후보로만 기록하며 최신 `/api/v1/sessions/{sessionId}/analysis-runs`와 동시에 최종 경로로 구현하지 않는다. AI 서비스는 Server가 호출하며 Client가 직접 호출하지 않는다.

## 4. 목표 구조화

### 4.1 요청 예시

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "goalText": "Spring Security JWT 인증 구현",
  "conversationTurn": 0
}
```

목표 명확화 요청은 `goalText`와 `conversationTurn`을 사용한다. `goalText`는 2~40자이며, `conversationTurn`은 0~2다. 사용자 확인 전 후보와 확인 후 확정 목표를 같은 객체로 덮어쓰지 않는다.

### 4.2 확정 전 보조 응답

```json
{
  "data": {
    "decision": "CLEAR",
    "normalizedGoalText": "Spring Security JWT 인증 구현",
    "question": null,
    "suggestions": [],
    "goalCandidates": [],
    "conversationTurn": 0
  }
}
```

허용 `decision`은 `CLEAR`, `AMBIGUOUS`, `MULTI_GOAL`, `UNKNOWN_TERM`이다. `AMBIGUOUS`의 `suggestions`는 2~3개, `MULTI_GOAL`은 `goalCandidates`와 질문 1개를 반환한다. 질문은 대화 전체에서 최대 2회다. 빈 입력은 AI 판정이 아니라 `GOAL_TEXT_INVALID`다. AI 실패·Timeout 뒤에는 사용자가 현재 목표 문장을 수동 확정할 수 있다. AI가 방문한 콘텐츠만으로 사용자 목표를 자동 확장하지 않는다.

MVP의 목표 문장은 2~40자, 명확화 질문은 전체 대화에서 최대 2회다. 여러 목표가 입력되면 최종적으로 하나만 선택하도록 돕는다. 목표 문장 확인·수정은 화면 관련성 결과 정정·삭제나 개인화 피드백과 별개다. Issue #12의 구현 Branch와 이 계약의 차이는 통합 전 확인한다.

## 5. 페이지 관련성 분석

### 5.1 요청 예시

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "eventId": "550e8400-e29b-41d4-a716-446655440003",
  "sessionId": "550e8400-e29b-41d4-a716-446655440001",
  "runId": "550e8400-e29b-41d4-a716-446655440004",
  "goalId": "550e8400-e29b-41d4-a716-446655440000",
  "goalVersion": 1,
  "navigationId": "navigation-003",
  "source": "extension",
  "sessionStatus": "RUNNING",
  "analysisStatus": "READY",
  "pauseReason": null,
  "settings": {
    "durationMode": "PRESET",
    "interventionLevel": "NORMAL",
    "recordOnly": false,
    "personalizedBreakEnabled": false,
    "settingsVersion": 1
  },
  "goalProfile": {
    "mainTopic": "Spring Security JWT 인증 구현",
    "purpose": "IMPLEMENTATION",
    "coreTopics": ["JWT", "인증 필터"],
    "supportingTopics": ["인증 오류 해결"]
  },
  "content": {
    "pageType": "QA",
    "domain": "example.com",
    "title": "JWT 인증 오류 해결",
    "text": "인증 필터 설정을 확인하는 공개 예제",
    "ocr": null,
    "extractionMethod": "DOM",
    "extractionStatus": "SUCCESS",
    "extractorVersion": "proposal-1"
  },
  "signals": {
    "activeDurationSeconds": 35,
    "browserFocused": true
  },
  "observedAt": "2026-09-12T10:00:00Z"
}
```

### 5.2 요청 규칙

- `goalVersion`은 Server가 저장한 최신 버전과 일치해야 한다.
- `content.text`와 `content.ocr`는 [콘텐츠 수집 계약](content_acquisition_spec.md)의 제안형 AI 전달 형식이다. OCR 경로의 `ocr`는 인식 상태·품질 메타데이터이며 원본 이미지나 차단된 OCR 원문을 넣지 않는다. 공개 `contentPayload`와 내부 제안형을 혼동하지 않는다.
- 최종 `qualityScore`와 정제 `contentHash`는 AI 전처리에서 계산한다. Client의 OCR 신뢰도·1차 품질값을 최종값으로 복사하지 않는다. 두 값의 Server 전달·저장 위치와 Backend 검토 예정 9개 컬럼은 미확정이다.
- 근거 문단 ID가 필요한 분석 응답은 AI 전처리에서 허용된 `text`를 분할해 부여한다. 입력 `passages` 유지 여부는 Server와 합의한다.
- 전체 URL, URL 쿼리, 사용자 ID, 이메일, Authorization Header를 포함하지 않는다.
- `EXCLUDED`이면 AI 요청을 만들지 않는다.
- `FAILED` 또는 `UNSUPPORTED`는 콘텐츠 판정 대신 처리 상태 기록을 우선한다.
- `activeDurationSeconds`는 흐름 정책 입력이며 AI가 학습 시간으로 확정하지 않는다.
- 체류·카메라 신호는 관련성 분석의 최종 라벨이나 `confidence`를 덮어쓰지 않는다. 이탈 정책 입력과 관련성 모델 입력의 분리 방식은 Server와 합의한다.

### 5.3 성공 응답 예시

```json
{
  "data": {
    "requestId": "550e8400-e29b-41d4-a716-446655440002",
    "eventId": "550e8400-e29b-41d4-a716-446655440003",
    "sessionId": "550e8400-e29b-41d4-a716-446655440001",
    "runId": "550e8400-e29b-41d4-a716-446655440004",
    "goalId": "550e8400-e29b-41d4-a716-446655440000",
    "goalVersion": 1,
    "navigationId": "navigation-003",
    "extractionStatus": "SUCCESS",
    "analysisStatus": "COMPLETED",
    "relevanceLabel": "RELATED",
    "productRelevanceLabel": "RELATED",
    "relationKind": "DIRECT",
    "driftState": "LEARNING",
    "recommendedAction": "NO_ACTION",
    "confidence": 0.84,
    "confidenceType": "HEURISTIC",
    "reasonCode": "GOAL_RELATED",
    "reason": "현재 자료는 목표의 인증 필터 설정과 직접 관련됩니다.",
    "evidenceIds": ["passage-001"],
    "decisionStage": "FINAL",
    "versions": {
      "analyzer": "relevance-v1",
      "model": "unselected",
      "prompt": "relevance-prompt-v1",
      "policy": "policy-v1",
      "extractor": "proposal-1"
    },
    "observedAt": "2026-09-12T10:00:00Z"
  }
}
```

`confidence`는 설명용 판단 지표다. 임베딩 유사도, OCR 인식 점수 또는 LLM 자기평가를 그대로 정답 확률로 반환하지 않는다. 의미와 보정 방법은 평가 후 `confidenceType`과 함께 확정한다.

MVP에서 `RELATED`·`UNRELATED` 후보의 confidence가 `0.75` 미만이면 `UNCERTAIN`으로 반환한다. 목표 이탈·복귀 정책은 최종 `relevanceLabel`·`confidence`를 그대로 소비하며 OCR `qualityScore`, 중복 여부, 임베딩 유사도로 다시 계산하지 않는다. `RELATED` 복귀는 이탈 구간과 대기 알림 후보를 초기화하고 `UNCERTAIN`은 시간을 누적하지 않는다.

### 5.4 상태 필드

허용 값과 조합 조건은 `state_model.md`를 단일 기준으로 사용한다.

- `extractionStatus`: 콘텐츠 확보 결과
- `analysisStatus`: AI 실행 결과
- `relevanceLabel`: 목표와 콘텐츠의 관계
- `driftState`: 최근 흐름·체류를 고려한 이탈 위험
- `recommendedAction`: 사용자에게 제안 가능한 행동

### 5.5 제품 표시 라벨 매핑

공개 API의 `relation`은 Notion 기준 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 사용한다. `EXCLUDED`와 `PRIVACY_BLOCKED`는 오류가 아닌 비분석 상태로 전달한다.

AI 내부에서 사용하는 `SUPPORTING`, `OFF_TASK`, `UNAVAILABLE` 같은 세부 상태는 분석 근거와 정책 판단을 위한 내부 값이다. Server는 공개 응답을 만들 때 관련성과 처리 상태를 각각 변환하며 내부 상태를 Client에 그대로 노출하지 않는다.

| AI 내부 값 | 제품 표시 값 | 설명 |
| --- | --- | --- |
| `RELATED` | `RELATED` | 직접 학습 |
| `SUPPORTING` | `RELATED` | 보조 학습이며 `relationKind=SUPPORTING`으로 구분 |
| `OFF_TASK` | `UNRELATED` | 충분한 근거가 있는 목표 무관 활동 |
| `UNCERTAIN` | `UNCERTAIN` | 의미적 판단 보류 |
| `UNAVAILABLE` | `UNCERTAIN` | 처리 실패이며 상세 원인은 별도 상태로 전달 |
| `extractionStatus=EXCLUDED` | `EXCLUDED` | AI 호출 없음 |
| 개인정보 2차 차단 | `PRIVACY_BLOCKED` | 캡처·전송·AI 호출 없음 |

### 5.6 세션·제외 입력 계약

AI는 세션 상태를 변경하지 않지만 Server가 검증한 현재 상태를 입력으로 받아 분석 가능 여부를 결정한다.

| 필드 | 허용 값·의미 |
| --- | --- |
| `sessionStatus` | Notion의 `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING`, `ENDED`, `ABANDONED` |
| `analysisStatus` | `EXCLUDED`, `PRIVACY_BLOCKED` 등 분석 상태. 세션 상태와 분리 |
| `pauseReason` | `USER`, `REQUIRED_PERMISSION`, `EXTENSION_UNLINKED`, `APP_RESTART`, `SYSTEM` 등 Server enum |
| `recordOnly` | 기록만 허용하고 알림·콘텐츠 노트를 만들지 않는지 여부 |
| `personalizedBreakEnabled` | 개인화 휴식 제안 동의·사용 여부 |
| `settingsVersion` | 세션 설정 변경 감지를 위한 버전 |

`EXCLUDED`에서는 화면 콘텐츠 수집·OCR·AI를 중지하고 타이머와 동의된 로컬 카메라 분석을 유지한다. `PRIVACY_BLOCKED`에서는 화면·카메라 분석을 중지하고 타이머는 유지한다. 타이머를 멈추는 것은 `PAUSED`·`AUTO_PAUSED` 같은 세션 전이로만 처리한다. 비분석 상태에서 콘텐츠 payload를 AI에 보내지 않는다.

### 5.7 피드백 Endpoint

`POST /api/v1/feedback`은 MVP 공개 API에 없다. 사용자 관련성 판정 수정·저장·AI 개인화 활용은 보류된 후속 구상이다. 구현 전에 Notion 제품 정책과 API 계약을 별도로 변경해야 한다.

후속 기능을 설계할 때도 한 페이지의 수정이 Domain 전체 허용으로 확대되지 않게 해야 한다. 현재 MVP에는 이 수정 기능과 공개 경로가 없다.

## 6. 처리된 비분석 결과

예상 가능한 추출 실패나 미지원은 요청이 Server까지 도달한 경우 정상 처리 결과의 분석 상태로 표현할 수 있다.

```json
{
  "data": {
    "eventId": "550e8400-e29b-41d4-a716-446655440003",
    "extractionStatus": "UNSUPPORTED",
    "analysisStatus": "SKIPPED",
    "relevanceLabel": "UNAVAILABLE",
    "driftState": "UNKNOWN",
    "recommendedAction": "NO_ACTION",
    "confidence": null,
    "reasonCode": "OCR_UNAVAILABLE"
  }
}
```

잘못된 요청, 인증 실패, 모델 호출 오류와 내부 오류는 Notion 공통 오류 형식으로 변환한다.

### 6.1 비분석 `reasonCode`

다음 코드는 요청 자체는 처리했지만 관련성 판단을 수행하지 않았음을 설명한다. 이 값은 낮은 관련성이나 사용자 이탈을 뜻하지 않는다.

| 코드 | 단계 | 기대 상태 |
| --- | --- | --- |
| `EXTRACTION_EMPTY` | DOM·텍스트가 비어 있음 | `FAILED`, `SKIPPED`, `UNAVAILABLE` |
| `EXTRACTION_TOO_SHORT` | 유형별 핵심 근거 부족 | `PARTIAL` 또는 `FAILED` |
| `DOM_ACCESS_DENIED` | DOM 접근 권한 없음 | 허용된 OCR 검토 또는 `UNAVAILABLE` |
| `IFRAME_ACCESS_DENIED` | Cross-origin 영역 접근 불가 | 상위 문서만 `PARTIAL` |
| `OCR_UNAVAILABLE` | OS·설치·권한상 OCR 미지원 | `UNSUPPORTED`, `SKIPPED` |
| `OCR_FAILED` | 허용된 로컬 OCR 실행 실패 | `FAILED`, `SKIPPED` |
| `UNSUPPORTED_CONTENT_TYPE` | 안전한 추출 경로가 없음 | `UNSUPPORTED`, `SKIPPED` |
| `ANALYSIS_NOT_REQUIRED` | 현재 캐시·정책으로 모델 호출 불필요 | 검증된 기존 결과 또는 `SKIPPED` |
| `SESSION_NOT_ACTIVE` | `sessionStatus`가 `RUNNING`이 아님 | 콘텐츠 분석 없음 |
| `PRIVACY_EXCLUDED` | 제외·민감 대상 | AI 요청을 만들지 않는 것이 기본 |

## 7. 실패 응답

```json
{
  "code": "ANALYSIS_SERVICE_UNAVAILABLE",
  "message": "AI 분석을 완료하지 못했습니다.",
  "retryable": true,
  "retryAfterSeconds": 3,
  "requestId": "req_01...",
  "details": {}
}
```

| 오류 코드 | 의미 | 처리 |
| --- | --- | --- |
| `INVALID_REQUEST` | Schema·필수 필드 오류 | 요청 수정, 모델 미호출 |
| `UNAUTHORIZED` | 서비스 인증 실패 | 원문 없는 오류 반환 |
| `UNAUTHORIZED_SCOPE` | 사용자·목표·세션 소유 범위 불일치 | 데이터 미반환, 재시도 금지 |
| `REQUEST_CONFLICT` | 같은 멱등 키에 다른 요청 내용 | 기존 결과를 덮어쓰지 않음 |
| `MODEL_TIMEOUT` | 모델 시간 예산 초과 | 결과 미사용, 무알림 |
| `MODEL_UNAVAILABLE` | 모델 의존성 사용 불가 | 안전한 이전 단계가 없으면 판단 보류 |
| `MODEL_OUTPUT_INVALID` | 출력 Schema 검증 실패 | 결과 미사용 |
| `AGENT_BUDGET_EXCEEDED` | Agent 시간·도구·모델 호출 예산 초과 | 판단 보류, 자동 반복 금지 |
| `DEPENDENCY_UNAVAILABLE` | 캐시·임베딩·조회 의존성 장애 | 안전한 단계가 없으면 판단 보류 |
| `STALE_EVENT` | 목표·회차·탐색 버전 불일치 | 현재 상태 변경 금지 |
| `NOTE_INPUT_STALE` | 노트 집계·목표·근거 버전이 변경됨 | 최신 Snapshot으로 새 작업 |
| `NOTE_OUTPUT_INVALID` | 노트 Schema·근거 검증 실패 | 노트 미저장, 시간 기록 유지 |
| `INTERNAL_ERROR` | 예상하지 못한 내부 오류 | 안전한 오류 코드와 요청 ID만 기록 |

위 코드는 AI 내부 진단용 후보이며 외부 Client에 그대로 노출하지 않는다. Server는 Notion `공통 Error Code`의 `ANALYSIS_SERVICE_UNAVAILABLE`, `REQUEST_TIMEOUT`, `VALIDATION_ERROR` 등으로 변환한다. 개인정보 차단은 오류 대신 정상 `analysisStatus = PRIVACY_BLOCKED`로 처리한다.

오류 메시지에 입력 원문, 모델 원본 출력, 비밀 값과 내부 Stack Trace를 포함하지 않는다.

## 8. 캐시와 멱등성

- 같은 `eventId` 재전송은 한 번만 처리한다.
- `requestId` 재사용 시 요청 Fingerprint가 다르면 충돌 오류로 처리한다.
- Fingerprint와 캐시 키에 입력 원문이나 인증 정보를 넣지 않는다.
- 콘텐츠 관련성 캐시와 시간 기반 `driftState`를 분리한다.
- 목표·모델·Prompt·추출기·정책 버전이 바뀌면 관련 캐시를 무효화한다. 화면 판정 피드백 버전은 후속 기능에서만 적용한다.
- 고정 `24시간` TTL은 미확정이다. 데이터별 보존 목적과 삭제 전파를 검토한 뒤 결정한다.

상세 기준은 `data_lifecycle.md`를 따른다.

## 9. 학습 노트 생성

노트는 Server가 회차 집계를 확정한 뒤 낮은 우선순위의 비동기 작업으로 요청한다. 회차 종료와 시간 저장은 노트 생성 성공에 의존하지 않는다.

작업 상태, 입력 Snapshot, 근거 검증, 멱등 키, 재생성과 삭제 계약은 `session_note_spec.md`를 단일 상세 기준으로 사용한다.

입력에는 다음만 포함한다.

- 확정된 목표와 목표 버전
- Server가 계산한 직접·보조·무관·미확인·중지 시간
- 저장이 허용된 활동 요약과 `sourceRef`
- 사용자 메모·미해결 표시 (화면 판정 수정은 후속 기능, MVP 제외)

출력에는 각 학습 주장과 연결된 근거 참조를 포함한다. 자료를 열었다는 사실만으로 이해하거나 해결했다고 쓰지 않는다.

작업 상태는 `PENDING → GENERATING → COMPLETED | FAILED`를 제안한다. `runId + aggregationVersion + noteVersion`으로 중복 생성을 방지한다.

### 9.1 작업 요청 제안

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "runId": "550e8400-e29b-41d4-a716-446655440004",
  "goalId": "550e8400-e29b-41d4-a716-446655440000",
  "goalVersion": 2,
  "aggregationVersion": 3,
  "evidenceSnapshotVersion": 1,
  "noteSchemaVersion": "session-note-v1"
}
```

Server가 실제 집계와 허용된 근거 Snapshot을 구성한다. Client가 임의로 노트 생성 입력을 AI에 직접 전달하지 않는다.

## 10. 검토 체크리스트

- [ ] 관련성 Endpoint 경로를 하나로 확정했다.
- [ ] 서비스 간 인증 방식을 확정했다.
- [ ] 다중 상태와 기존 `FocusState` 호환 방식을 확정했다.
- [ ] `goalId / sessionId / runId` 관계를 Server와 합의했다.
- [ ] `confidence` 의미와 `null` 허용을 합의했다.
- [ ] 요청 크기, 문단 수, Timeout, 재시도 상한을 평가했다.
- [ ] 캐시·체크포인트·로그 TTL과 삭제 전파를 합의했다.
- [ ] 공유 타입과 Server·Client 소비자 테스트를 계획했다.
- [ ] ColPali 이미지를 기본 API 입력에서 제외했음을 확인했다.

## 11. 검증 시나리오

- 정상 `RELATED`와 `SUPPORTING` 결과가 서로 구분된다.
- 짧은 `OFF_TASK`도 관련성 근거와 confidence가 충분하면 제품 `UNRELATED`로 표시되지만, 15초 미만 전환은 이탈 시간·알림 조건에 포함하지 않고 차단하지 않는다.
- `FAILED`, `UNSUPPORTED`, 모델 오류에서 `NO_ACTION`을 반환하거나 실패 envelope를 사용한다.
- 목표 수정·탭 전환·회차 종료 후 오래된 결과가 폐기된다.
- 같은 이벤트 재전송으로 결과와 시간이 중복되지 않는다.
- 목표 문장 수정 직후 오래된 `goalVersion` 결과와 대기 알림이 무효화된다.
- MVP에서 화면 판정 수정·저장 Endpoint를 노출하지 않는다.
- 요청·응답·로그에 원본 화면, 전체 URL, 비밀 값이 남지 않는다.
- 모델 출력이 허용 Schema를 벗어나면 사용하지 않는다.

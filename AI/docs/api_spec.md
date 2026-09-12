# AI API 계약 초안

## 문서 상태

- 상태: 팀 검토용 초안, 미구현
- 버전: `0.2.0-draft`
- 기준일: 2026-09-12
- 검토 필요: Server·Extension·Shared·Client 담당자
- 관련 문서: [개발 규칙](DEVELOPMENT_RULES.md), [목표·세션 모델](goal_session_spec.md), [콘텐츠 수집 계약](content_acquisition_spec.md), [상태 모델](state_model.md), [피드백 명세](feedback_personalization_spec.md), [노트 명세](session_note_spec.md), [데이터 수명](data_lifecycle.md)

이 문서는 FocusOn Server와 AI 서비스 사이의 목표 구조화, 페이지 관련성 분석과 학습 노트 생성 계약을 제안한다. 기존 루트 `docs/API_CONTRACT.md`나 `packages/shared-types`를 자동으로 대체하지 않는다. 경로, 인증, 필드, 상태 호환 방식은 관련 담당자 합의 후 `DECISION_RECORD.md`에 확정한다.

## 1. 책임 경계

```text
Extension / macOS 네이티브 모듈
  → 제외·권한 검사
  → DOM 또는 허용된 로컬 Apple Vision OCR
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
| 응답 | 루트 공통 성공·실패 envelope 유지 |
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

| 기능 | Method·경로 | 상태 |
| --- | --- | --- |
| 목표 구조화 | `POST /api/v1/goals/profile` | 제안 |
| 페이지 관련성 분석 | `POST /api/v1/analyze/relevance` | 제안 |
| 기존 관련성 경로 | `POST /api/v1/analysis/relevance` | 기존 초안, 경로 합의 필요 |
| 학습 노트 생성 | `POST /api/v1/session-notes/generate` | 후속 제안 |

두 관련성 경로를 동시에 구현하지 않는다. Server 담당자와 하나를 확정하거나 버전 마이그레이션 계획을 만든다.

## 4. 목표 구조화

### 4.1 요청 예시

```json
{
  "requestId": "request-uuid",
  "goalId": "goal-uuid",
  "goalVersion": 1,
  "originalText": "Spring Security JWT 인증 구현"
}
```

### 4.2 성공 응답 예시

```json
{
  "success": true,
  "data": {
    "goalId": "goal-uuid",
    "goalVersion": 1,
    "originalText": "Spring Security JWT 인증 구현",
    "mainTopic": "Spring Security 기반 JWT 인증",
    "purpose": "IMPLEMENTATION",
    "coreTopics": ["JWT", "SecurityFilterChain", "토큰 검증"],
    "supportingTopics": ["HTTP 인증 헤더", "인증 오류 해결", "CORS"],
    "expectedActivities": ["공식 문서", "예제 코드", "오류 검색", "강의 시청"],
    "clarificationNeeded": false
  },
  "message": "목표 해석이 생성되었습니다."
}
```

사용자 확인·수정 후 Server가 `confirmedByUser`를 관리한다. AI가 방문한 콘텐츠만으로 사용자 목표를 자동 확장하지 않는다.

## 5. 페이지 관련성 분석

### 5.1 요청 예시

```json
{
  "requestId": "request-uuid",
  "eventId": "event-uuid",
  "sessionId": "session-uuid",
  "runId": "run-uuid",
  "goalId": "goal-uuid",
  "goalVersion": 1,
  "navigationId": "navigation-003",
  "source": "EXTENSION",
  "goalProfile": {
    "mainTopic": "Spring Security JWT 인증 구현",
    "purpose": "IMPLEMENTATION",
    "coreTopics": ["JWT", "인증 필터"],
    "supportingTopics": ["인증 오류 해결"]
  },
  "content": {
    "contentHash": "sanitized-content-hash",
    "pageType": "QA",
    "domain": "example.com",
    "title": "JWT 인증 오류 해결",
    "passages": [
      {
        "id": "passage-001",
        "text": "인증 필터 설정을 확인하는 공개 예제"
      }
    ],
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
- `contentHash`는 개인정보 검사를 통과한 정제 콘텐츠로 계산한다.
- `passages[].id`는 응답 근거를 현재 요청의 문단과 연결한다.
- 전체 URL, URL 쿼리, 사용자 ID, 이메일, Authorization Header를 포함하지 않는다.
- `EXCLUDED`이면 AI 요청을 만들지 않는다.
- `FAILED` 또는 `UNSUPPORTED`는 콘텐츠 판정 대신 처리 상태 기록을 우선한다.
- `activeDurationSeconds`는 흐름 정책 입력이며 AI가 학습 시간으로 확정하지 않는다.

### 5.3 성공 응답 예시

```json
{
  "success": true,
  "data": {
    "requestId": "request-uuid",
    "eventId": "event-uuid",
    "sessionId": "session-uuid",
    "runId": "run-uuid",
    "goalId": "goal-uuid",
    "goalVersion": 1,
    "navigationId": "navigation-003",
    "extractionStatus": "SUCCESS",
    "analysisStatus": "COMPLETED",
    "relevanceLabel": "SUPPORTING",
    "driftState": "LEARNING",
    "recommendedAction": "NO_ACTION",
    "confidence": 0.84,
    "confidenceType": "HEURISTIC",
    "reasonCode": "GOAL_SUPPORTING",
    "reason": "현재 자료는 목표를 진행하는 데 필요한 보조 학습 자료입니다.",
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
  },
  "message": "분석이 완료되었습니다."
}
```

`confidence`는 설명용 판단 지표다. 임베딩 유사도, OCR 인식 점수 또는 LLM 자기평가를 그대로 정답 확률로 반환하지 않는다. 의미와 보정 방법은 평가 후 `confidenceType`과 함께 확정한다.

### 5.4 상태 필드

허용 값과 조합 조건은 `state_model.md`를 단일 기준으로 사용한다.

- `extractionStatus`: 콘텐츠 확보 결과
- `analysisStatus`: AI 실행 결과
- `relevanceLabel`: 목표와 콘텐츠의 관계
- `driftState`: 최근 흐름·체류를 고려한 이탈 위험
- `recommendedAction`: 사용자에게 제안 가능한 행동

### 5.5 기존 FocusState 호환

현재 `packages/shared-types`의 `FOCUSED`, `DISTRACTED`, `UNCERTAIN`을 유지해야 한다면 응답에 `legacyState`를 임시 추가하는 안을 검토한다.

```json
{
  "legacyState": "FOCUSED"
}
```

`legacyState`는 새 상태 조합에서 애플리케이션 코드가 파생한다. 모델이 직접 생성하지 않는다. 매핑과 제거 일정은 `state_model.md` 및 공유 타입 담당자 합의가 필요하다.

## 6. 처리된 비분석 결과

예상 가능한 추출 실패나 미지원은 요청이 Server까지 도달한 경우 성공 envelope의 처리 상태로 표현할 수 있다.

```json
{
  "success": true,
  "data": {
    "eventId": "event-uuid",
    "extractionStatus": "UNSUPPORTED",
    "analysisStatus": "SKIPPED",
    "relevanceLabel": "UNAVAILABLE",
    "driftState": "UNKNOWN",
    "recommendedAction": "NO_ACTION",
    "confidence": null,
    "reasonCode": "OCR_UNAVAILABLE"
  },
  "message": "지원되지 않는 추출 경로라 이번 활동은 판단하지 않았습니다."
}
```

잘못된 요청, 인증 실패, 모델 호출 오류와 내부 오류는 실패 envelope를 사용한다.

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
| `SESSION_NOT_RUNNING` | 회차가 실행 중이 아님 | 콘텐츠 분석 없음 |
| `PRIVACY_EXCLUDED` | 제외·민감 대상 | AI 요청을 만들지 않는 것이 기본 |

## 7. 실패 응답

```json
{
  "success": false,
  "error": {
    "code": "MODEL_TIMEOUT",
    "message": "AI 분석이 지연되어 이번 페이지는 판단하지 않았습니다."
  }
}
```

| 오류 코드 | 의미 | 처리 |
| --- | --- | --- |
| `INVALID_REQUEST` | Schema·필수 필드 오류 | 요청 수정, 모델 미호출 |
| `UNAUTHORIZED` | 서비스 인증 실패 | 원문 없는 오류 반환 |
| `UNAUTHORIZED_SCOPE` | 사용자·목표·세션 소유 범위 불일치 | 데이터 미반환, 재시도 금지 |
| `REQUEST_CONFLICT` | 같은 멱등 키에 다른 요청 내용 | 기존 결과를 덮어쓰지 않음 |
| `PRIVACY_BLOCKED` | 민감정보 2차 검사 실패 | 모델 미호출, 원문 미저장 |
| `MODEL_TIMEOUT` | 모델 시간 예산 초과 | 결과 미사용, 무알림 |
| `MODEL_UNAVAILABLE` | 모델 의존성 사용 불가 | 안전한 이전 단계가 없으면 판단 보류 |
| `MODEL_OUTPUT_INVALID` | 출력 Schema 검증 실패 | 결과 미사용 |
| `AGENT_BUDGET_EXCEEDED` | Agent 시간·도구·모델 호출 예산 초과 | 판단 보류, 자동 반복 금지 |
| `DEPENDENCY_UNAVAILABLE` | 캐시·임베딩·조회 의존성 장애 | 안전한 단계가 없으면 판단 보류 |
| `STALE_EVENT` | 목표·회차·탐색 버전 불일치 | 현재 상태 변경 금지 |
| `NOTE_INPUT_STALE` | 노트 집계·목표·근거 버전이 변경됨 | 최신 Snapshot으로 새 작업 |
| `NOTE_OUTPUT_INVALID` | 노트 Schema·근거 검증 실패 | 노트 미저장, 시간 기록 유지 |
| `INTERNAL_ERROR` | 예상하지 못한 내부 오류 | 안전한 오류 코드와 요청 ID만 기록 |

오류 메시지에 입력 원문, 모델 원본 출력, 비밀 값과 내부 Stack Trace를 포함하지 않는다.

## 8. 캐시와 멱등성

- 같은 `eventId` 재전송은 한 번만 처리한다.
- `requestId` 재사용 시 요청 Fingerprint가 다르면 충돌 오류로 처리한다.
- Fingerprint와 캐시 키에 입력 원문이나 인증 정보를 넣지 않는다.
- 콘텐츠 관련성 캐시와 시간 기반 `driftState`를 분리한다.
- 목표·피드백·모델·Prompt·추출기·정책 버전이 바뀌면 관련 캐시를 무효화한다.
- 고정 `24시간` TTL은 미확정이다. 데이터별 보존 목적과 삭제 전파를 검토한 뒤 결정한다.

상세 기준은 `data_lifecycle.md`를 따른다.

## 9. 학습 노트 생성

노트는 Server가 회차 집계를 확정한 뒤 낮은 우선순위의 비동기 작업으로 요청한다. 회차 종료와 시간 저장은 노트 생성 성공에 의존하지 않는다.

작업 상태, 입력 Snapshot, 근거 검증, 멱등 키, 재생성과 삭제 계약은 `session_note_spec.md`를 단일 상세 기준으로 사용한다.

입력에는 다음만 포함한다.

- 확정된 목표와 목표 버전
- Server가 계산한 직접·보조·무관·미확인·중지 시간
- 저장이 허용된 활동 요약과 `sourceRef`
- 사용자 수정·메모·미해결 표시

출력에는 각 학습 주장과 연결된 근거 참조를 포함한다. 자료를 열었다는 사실만으로 이해하거나 해결했다고 쓰지 않는다.

작업 상태는 `PENDING → GENERATING → COMPLETED | FAILED`를 제안한다. `runId + aggregationVersion + noteVersion`으로 중복 생성을 방지한다.

### 9.1 작업 요청 제안

```json
{
  "requestId": "request-uuid",
  "runId": "run-uuid",
  "goalId": "goal-uuid",
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
- 짧은 `OFF_TASK`가 즉시 `DISTRACTED`나 차단으로 변환되지 않는다.
- `FAILED`, `UNSUPPORTED`, 모델 오류에서 `NO_ACTION`을 반환하거나 실패 envelope를 사용한다.
- 목표 수정·탭 전환·회차 종료 후 오래된 결과가 폐기된다.
- 같은 이벤트 재전송으로 결과와 시간이 중복되지 않는다.
- 사용자 수정 직후 오래된 캐시와 대기 알림이 무효화된다.
- 요청·응답·로그에 원본 화면, 전체 URL, 비밀 값이 남지 않는다.
- 모델 출력이 허용 Schema를 벗어나면 사용하지 않는다.

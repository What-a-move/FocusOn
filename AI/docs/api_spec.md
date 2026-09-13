# AI API 명세

## 문서 상태

- 상태: 초안
- 작성 기준일: 2026-09-05
- 검토 필요: Server 담당자, Frontend(`apps`) 담당자
- 확정 조건: 호출 경로, 인증, Timeout, 요청 필드, 상태값, 캐시 정책을 관련 담당자가 확인한 뒤 AI `DECISION_RECORD.md`에 확정 결정을 남긴다.

이 문서의 값과 정책은 검토가 끝나기 전까지 구현 확정 사항으로 간주하지 않는다.

분석 상태값은 `packages/shared-types`의 `FocusState`(`FOCUSED`, `DISTRACTED`, `UNCERTAIN`)와 동일하게 사용한다. 상태값을 변경할 때는 공통 타입과 이 문서를 함께 수정한다.

### 검토 체크리스트

- [ ] Server가 AI API의 유일한 호출자인지 확인
- [ ] Bearer 서비스 토큰 인증 방식 확인
- [ ] 동기 처리와 10초 Timeout 확인
- [ ] `contentText` 최대 4,000자 제한 확인
- [ ] `FOCUSED`, `DISTRACTED`, `UNCERTAIN` 상태값 확인
- [ ] AI의 24시간 결과 캐시 정책 확인
- [ ] 실패 응답의 `requestId`와 `details` 형식 확인

## 1. 문서 목적

이 문서는 FocusOn Server가 AI 서비스에 현재 활동의 학습 목표 관련성 분석을 요청할 때 사용하는 API 계약을 정의한다.

1차 범위는 사용자의 학습 목표와 Desktop 또는 Chrome Extension에서 수집한 최소 활동 정보를 비교해 현재 활동을 `FOCUSED`, `DISTRACTED`, `UNCERTAIN` 중 하나로 판단하는 것이다.

여기서 상태값은 사용자의 실제 주의력이나 행동을 직접 측정한 결과가 아니다. 화면·페이지 정보가 학습 목표와 관련 있는지를 바탕으로 추정한 **활동 관련성 상태**다.

다음 기능은 1차 범위에 포함하지 않는다.

- 목표 자체의 품질 분석
- 여러 활동을 이용한 장기 학습 흐름 분석
- 연속 이탈 여부 판단
- 학습 종료 후 요약·리포트 생성
- MediaPipe 시선·자세 상태 분석
- 원본 화면 또는 카메라 영상 분석

구체적인 점수 임계값과 모델 판단 규칙은 `analysis_rules.md`에서 관리한다.

## 2. 호출 구조와 책임

```text
Desktop / Chrome Extension
  → 최소 활동 데이터 수집
  → Spring Server
      ├─ 사용자·세션 검증
      ├─ 제외 앱·도메인 확인
      ├─ 민감 정보 제거
      └─ AI 서비스 호출
          → 관련성 분석 결과 반환
  → Spring Server가 결과 영구 저장
```

- AI API는 Spring Server만 호출한다.
- Desktop과 Chrome Extension은 AI API를 직접 호출하지 않는다.
- 제외 앱·도메인에서는 Server가 AI 요청을 생성하지 않는다.
- Server는 AI에 전달하기 전에 URL 경로·쿼리·토큰과 민감 정보를 제거한다.
- AI 서비스는 분석 결과만 반환하며 사용자·세션·리포트의 영구 저장을 담당하지 않는다.

## 3. 공통 규칙

### 3.1 통신 방식

- 기본 경로: `/api/v1`
- Content-Type: `application/json`
- 처리 방식: 동기식 요청·응답
- 분석 제한 시간: AI 서비스가 인증과 요청 검증을 마친 시점부터 응답 생성을 완료할 때까지 최대 10초
- 날짜와 시간: UTC 기준 ISO 8601 문자열
- 식별자: 문자열 UUID
- 필드 이름: lower camel case

### 3.2 인증

모든 분석 요청에는 Server와 AI 사이에서 사용하는 서비스 토큰이 필요하다.

```http
Authorization: Bearer <service-token>
```

- 서비스 토큰은 환경 변수로 관리한다.
- 토큰 원문은 코드, 문서, 요청 로그, 오류 로그에 기록하지 않는다.
- 토큰이 없거나 일치하지 않으면 `401 UNAUTHORIZED`를 반환한다.

### 3.3 공통 응답 형식

성공 응답은 다음 형식을 사용한다.

```json
{
  "success": true,
  "data": {},
  "message": "요청이 완료되었습니다."
}
```

실패 응답은 다음 형식을 사용한다.

```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "요청 값을 확인해주세요.",
    "requestId": "01991a9a-9968-7f21-8b75-17edc5e534a3",
    "details": [
      {
        "field": "activity.domain",
        "reason": "EXTENSION 요청에는 domain이 필요합니다."
      }
    ]
  }
}
```

호출자는 사용자 안내 문구가 아니라 `error.code`를 기준으로 오류를 처리한다.

- `error.requestId`는 요청에서 유효한 UUID를 확인할 수 있을 때 반환하고, 확인할 수 없으면 `null`을 반환한다.
- `error.details`는 입력 검증 실패 시에만 선택적으로 반환한다.
- `error.details`에는 필드 이름과 안전한 실패 사유만 포함하고 실제 입력값은 포함하지 않는다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `success` | Boolean | 예 | 실패 응답에서는 항상 `false` |
| `error.code` | String | 예 | 호출자가 분기할 안정적인 오류 코드 |
| `error.message` | String | 예 | 민감 정보를 포함하지 않는 안전한 오류 설명 |
| `error.requestId` | UUID 문자열 또는 `null` | 예 | 확인 가능한 요청 식별자, 확인할 수 없으면 `null` |
| `error.details` | Array | 아니요 | 입력 검증 실패 필드와 사유 목록 |

## 4. 현재 활동 관련성 분석

### 4.1 Endpoint

```http
POST /api/v1/analysis/relevance
```

학습 목표와 현재 앱 또는 페이지 정보를 비교해 활동 관련성 상태를 반환한다.

### 4.2 Request

```json
{
  "requestId": "01991a9a-9968-7f21-8b75-17edc5e534a3",
  "sessionId": "01991a98-42db-7982-b442-13f26e7c468a",
  "goal": {
    "goalId": "01991a98-c5cb-7ba7-9c54-60b046049f45",
    "text": "Spring Security 인증 구조 공부"
  },
  "activity": {
    "source": "EXTENSION",
    "appName": "Google Chrome",
    "domain": "docs.spring.io",
    "title": "Spring Security Reference",
    "textSource": "PAGE_TEXT",
    "contentText": "Server가 민감 정보를 제거한 최소 분석 텍스트"
  },
  "observedAt": "2026-09-05T03:00:00Z"
}
```

### 4.3 Request 필드

| 필드 | 형식 | 필수 | 제약 및 설명 |
| --- | --- | --- | --- |
| `requestId` | UUID 문자열 | 예 | 요청 추적과 중복 방지에 사용하는 고유 식별자 |
| `sessionId` | UUID 문자열 | 예 | 현재 학습 세션 식별자 |
| `goal` | Object | 예 | 현재 학습 목표 |
| `goal.goalId` | UUID 문자열 | 예 | 학습 목표 식별자 |
| `goal.text` | String | 예 | 공백 제거 후 1~500자 |
| `activity` | Object | 예 | 현재 앱 또는 페이지의 최소 활동 정보 |
| `activity.source` | Enum | 예 | `DESKTOP` 또는 `EXTENSION` |
| `activity.appName` | String | 아니요 | 앱 표시 이름, 최대 200자 |
| `activity.bundleId` | String | 아니요 | `DESKTOP`에서만 사용하는 macOS Bundle Identifier, 최대 255자 |
| `activity.domain` | String | 조건부 | `EXTENSION`에서 필수인 호스트명, 경로·쿼리·토큰 제외, 최대 253자 |
| `activity.title` | String | 아니요 | 창 또는 페이지 제목, 최대 500자 |
| `activity.textSource` | Enum | 조건부 | `contentText`가 있으면 필수, `OCR` 또는 `PAGE_TEXT` |
| `activity.contentText` | String | 아니요 | 민감 정보를 제거한 최소 텍스트, 최대 4,000자 |
| `observedAt` | ISO 8601 문자열 | 예 | 활동이 관찰된 UTC 시각 |

### 4.4 Request 검증 규칙

- `requestId`, `sessionId`, `goal.goalId`는 유효한 UUID여야 한다.
- `goal.text`는 공백만으로 구성할 수 없다.
- `observedAt`은 UTC를 나타내는 `Z`로 끝나는 유효한 ISO 8601 시각이어야 한다.
- `activity.source`는 정의된 값만 허용한다.
- `DESKTOP` 요청은 `appName`이 필수이며 `bundleId`, `title`, `contentText`를 선택적으로 전달한다. `domain`은 전달하지 않는다.
- `EXTENSION` 요청은 `domain`이 필수이며 `appName`, `title`, `contentText`를 선택적으로 전달한다. `bundleId`는 전달하지 않는다.
- `contentText`를 전달하면 텍스트 출처에 맞는 `textSource`를 함께 전달한다.
- `DESKTOP` OCR 결과는 `OCR`, Extension이 DOM에서 추출한 본문은 `PAGE_TEXT`를 사용한다.
- `contentText`가 없으면 `textSource`도 전달하지 않는다.
- 선택 필드에 값이 없으면 빈 문자열이나 `null`을 전달하지 않고 해당 필드를 생략한다.
- `domain`에는 프로토콜, 경로, 쿼리 문자열, Fragment를 포함하지 않는다.
- `contentText`가 4,000자를 넘으면 분석 전에 임의로 자르지 않고 `CONTENT_TOO_LARGE`를 반환한다.
- 사용자 ID, 이메일, 원본 URL, 화면 이미지, 카메라 영상, MediaPipe 상태값은 요청에 포함하지 않는다.
- AI 서비스가 Bearer 토큰, JWT, API Key 형식 또는 결제 카드 후보처럼 규칙으로 식별 가능한 민감 정보를 발견하면 모델에 전달하지 않고 `SENSITIVE_CONTENT_DETECTED`를 반환한다.

### 4.5 내부 분석 단계

API 응답 형식은 분석 단계와 관계없이 동일하다. AI 서비스는 다음 순서로 비용이 낮고 재현 가능한 판단을 먼저 수행한다.

```text
요청 검증
  → OCR·페이지 텍스트 정제와 품질 판단
  → 규칙 기반 판단
  → 임베딩 유사도 판단
  → 애매한 경우 LLM 정밀 판단
  → analysis_rules.md 기준으로 최종 상태 결정
```

- 입력이 부족하면 불필요한 임베딩·LLM 호출 없이 `UNCERTAIN`을 반환한다.
- 규칙이나 임베딩으로 충분히 판단하면 LLM을 호출하지 않는다.
- 단계별 내부 점수와 원문은 API 응답이나 로그에 노출하지 않는다.
- 임베딩 모델, LLM 공급자, 단계별 진입 임계값은 평가 후 확정한다.

## 5. 성공 응답

### 5.1 Response 예시

```json
{
  "success": true,
  "data": {
    "requestId": "01991a9a-9968-7f21-8b75-17edc5e534a3",
    "sessionId": "01991a98-42db-7982-b442-13f26e7c468a",
    "state": "FOCUSED",
    "relevanceScore": 0.91,
    "confidence": 0.87,
    "reasonCode": "GOAL_RELATED",
    "reason": "현재 활동이 설정한 학습 목표와 관련되어 있습니다.",
    "analyzerVersion": "relevance-v1",
    "analyzedAt": "2026-09-05T03:00:01Z",
    "cached": false
  },
  "message": "관련성 분석이 완료되었습니다."
}
```

### 5.2 Response 필드

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| `success` | Boolean | 성공 응답에서는 항상 `true` |
| `data.requestId` | UUID 문자열 | 요청에서 전달받은 요청 식별자 |
| `data.sessionId` | UUID 문자열 | 요청에서 전달받은 학습 세션 식별자 |
| `data.state` | Enum | 목표 관련성을 바탕으로 추정한 `FOCUSED`, `DISTRACTED`, `UNCERTAIN` 중 하나 |
| `data.relevanceScore` | Number | 목표와 활동의 관련성 정도, 0 이상 1 이하 |
| `data.confidence` | Number | 현재 판단의 확실성, 0 이상 1 이하 |
| `data.reasonCode` | Enum | 판단 근거를 구분하는 안정적인 코드 |
| `data.reason` | String | 검증된 응답 Template으로 생성하는 짧고 중립적인 한국어 설명 |
| `data.analyzerVersion` | String | `relevance-v{major}` 형식의 분석 계약·규칙 버전 |
| `data.analyzedAt` | ISO 8601 문자열 | 실제 분석이 완료된 UTC 시각 |
| `data.cached` | Boolean | 24시간 캐시의 기존 결과인지 여부 |
| `message` | String | 성공 안내 문구 |

### 5.3 상태값

이 상태값은 현재 활동의 목표 관련성을 표현한다. 사용자의 실제 시선, 자세 또는 주의 상태를 증명하거나 단정하지 않는다.

| 상태 | 의미 |
| --- | --- |
| `FOCUSED` | 현재 활동이 설정한 학습 목표와 관련 있다고 판단됨 |
| `DISTRACTED` | 현재 활동이 설정한 학습 목표와 관련성이 낮다고 판단됨 |
| `UNCERTAIN` | 정보가 부족하거나 모호해 목표 관련성을 확정할 수 없음 |

`PAUSED`와 `EXCLUDED`는 AI 분석 결과가 아니다. 해당 상태는 Desktop, Extension 또는 Server가 결정하며 AI 요청을 보내지 않는다.

### 5.4 점수 구분

- `relevanceScore`는 현재 활동이 학습 목표와 얼마나 관련 있는지를 나타낸다.
- `confidence`는 AI가 반환한 상태 판단을 얼마나 확신하는지를 나타낸다.
- 두 값은 서로 다른 의미이므로 하나의 값으로 합치지 않는다.
- 상태별 점수와 신뢰도 임계값은 `analysis_rules.md`에서 정의한다.

### 5.5 판단 근거 코드

| `reasonCode` | 사용할 수 있는 상태 | 의미 |
| --- | --- | --- |
| `GOAL_RELATED` | `FOCUSED` | 목표의 핵심 주제 또는 필요한 학습 활동과 관련됨 |
| `GOAL_UNRELATED` | `DISTRACTED` | 목표와 명확하게 무관한 활동으로 판단됨 |
| `INSUFFICIENT_CONTEXT` | `UNCERTAIN` | 판단에 필요한 활동 정보가 부족함 |
| `AMBIGUOUS_CONTEXT` | `UNCERTAIN` | 정보는 있으나 여러 의미로 해석할 수 있음 |

- `reason`은 비난이나 단정적인 표현을 사용하지 않는다.
- 모델 내부 오류, 외부 AI 서비스 장애, 시간 초과는 `UNCERTAIN`으로 숨기지 않고 실패 응답으로 반환한다.

## 6. 실패 응답

### 6.1 오류 코드

| HTTP 상태 | `error.code` | 발생 조건 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 필수값 누락, UUID·시간 형식 오류, 허용되지 않은 `source` |
| `401` | `UNAUTHORIZED` | Bearer 토큰 누락 또는 불일치 |
| `409` | `REQUEST_ID_CONFLICT` | 같은 `requestId`로 기존 요청과 다른 내용을 전송함 |
| `413` | `CONTENT_TOO_LARGE` | `contentText`가 4,000자를 초과함 |
| `422` | `SENSITIVE_CONTENT_DETECTED` | AI의 2차 검사에서 명백한 민감 정보가 확인됨 |
| `500` | `ANALYSIS_FAILED` | 서비스 내부 처리 또는 결과 형식 검증에 실패함 |
| `503` | `MODEL_UNAVAILABLE` | 모델 또는 외부 AI 서비스에 연결할 수 없음 |
| `504` | `ANALYSIS_TIMEOUT` | 인증과 요청 검증 완료 후 10초 안에 분석을 완료하지 못함 |

### 6.2 입력값 오류 예시

```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "요청 값을 확인해주세요.",
    "requestId": "01991a9a-9968-7f21-8b75-17edc5e534a3",
    "details": [
      {
        "field": "activity.domain",
        "reason": "EXTENSION 요청에는 domain이 필요합니다."
      }
    ]
  }
}
```

### 6.3 인증 오류 예시

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "AI 서비스 인증에 실패했습니다.",
    "requestId": null
  }
}
```

오류 메시지에는 인증 값, 입력 원문, 모델 응답 원문, 내부 예외 상세 정보를 포함하지 않는다.

## 7. 캐시와 중복 요청

- AI 서비스는 `requestId`를 캐시 키로 사용한다.
- 캐시에는 입력 원문이 아니라 요청 내용의 비교용 Fingerprint와 최종 분석 결과만 저장한다.
- Fingerprint는 인증 Header를 제외한 요청 JSON을 UTF-8, 키 이름 오름차순, 불필요한 공백 제거 형식으로 정규화한 뒤 SHA-256으로 계산한다.
- 선택 필드는 값이 없을 때 항상 생략하므로 `null`과 필드 생략을 서로 다른 요청으로 비교하는 문제를 만들지 않는다.
- 캐시 보존 시간은 최초 분석 완료 시점부터 24시간이다.
- 같은 `requestId`와 같은 요청 내용이 다시 들어오면 기존 결과를 반환한다.
- 캐시된 결과의 `analyzedAt`과 `analyzerVersion`은 최초 분석 값을 유지하고 `cached`만 `true`로 반환한다.
- 같은 `requestId`와 다른 요청 내용이 들어오면 `409 REQUEST_ID_CONFLICT`를 반환한다.
- 만료된 캐시 결과는 자동으로 삭제한다.
- AI 서비스는 캐시 결과 조회 API를 제공하지 않는다.
- 분석 결과의 영구 저장과 리포트 이력 관리는 Spring Server가 담당한다.
- AI 서비스를 여러 Instance로 실행하거나 재시작하더라도 24시간 중복 처리 계약을 유지할 수 있는 TTL 지원 공유 저장소를 사용한다. 구체적인 저장소 제품은 구현 계획에서 결정한다.

## 8. 개인정보와 로그

### 8.1 전달하지 않는 데이터

- 사용자 ID, 이메일 등 계정 식별 정보
- 원본 URL의 경로, 쿼리, Fragment
- 비밀번호, 인증 토큰, API Key
- 결제 정보와 개인 메시지
- 원본 화면 캡처
- 원본 카메라 영상
- MediaPipe 시선·자세 상태값

### 8.2 처리 원칙

- Server는 요청 전에 분석 제외 앱·도메인을 확인한다.
- Server는 `contentText`에서 민감 정보를 제거하고 필요한 최소 범위만 전달한다.
- AI 서비스는 모델 호출 전에 Bearer 토큰, JWT, 알려진 API Key 형식과 Luhn 검사를 통과한 결제 카드 후보를 2차 검사한다.
- 2차 검사에서 민감 정보가 확인되면 원문을 저장하거나 모델에 전달하지 않고 `SENSITIVE_CONTENT_DETECTED`를 반환한다.
- 개인 메시지는 문자열 규칙만으로 완전하게 식별할 수 없으므로 Server가 제외 도메인·앱 정책과 수집 단계에서 차단한다.
- AI 서비스는 요청 본문과 모델 입력·출력 원문을 로그에 기록하지 않는다.
- AI 로그에는 요청 시각, 기능명, 오류 코드, `requestId`, `sessionId`, 처리 시간만 기록한다.
- 서비스 토큰과 외부 모델 API Key는 환경 변수로 관리한다.
- AI 캐시에는 학습 목표와 활동 텍스트 원문을 저장하지 않는다.

## 9. 요청·응답 예시

### 9.1 Desktop 활동 분석

```json
{
  "requestId": "01991aa7-b3ad-7c0e-91cc-c7334ddca64a",
  "sessionId": "01991a98-42db-7982-b442-13f26e7c468a",
  "goal": {
    "goalId": "01991a98-c5cb-7ba7-9c54-60b046049f45",
    "text": "Java 백엔드 프로젝트 개발"
  },
  "activity": {
    "source": "DESKTOP",
    "appName": "IntelliJ IDEA",
    "bundleId": "com.jetbrains.intellij",
    "title": "SecurityConfig.java",
    "textSource": "OCR",
    "contentText": "Spring Security filter chain configuration"
  },
  "observedAt": "2026-09-05T03:10:00Z"
}
```

### 9.2 Extension 활동 분석

```json
{
  "requestId": "01991aa8-ac6b-7907-a1fc-fc93c694f046",
  "sessionId": "01991a98-42db-7982-b442-13f26e7c468a",
  "goal": {
    "goalId": "01991a98-c5cb-7ba7-9c54-60b046049f45",
    "text": "Spring Security 인증 구조 공부"
  },
  "activity": {
    "source": "EXTENSION",
    "appName": "Google Chrome",
    "domain": "docs.spring.io",
    "title": "Spring Security Reference",
    "textSource": "PAGE_TEXT",
    "contentText": "Authentication architecture and security context"
  },
  "observedAt": "2026-09-05T03:15:00Z"
}
```

### 9.3 동일 요청 재전송

같은 `requestId`와 같은 요청 내용을 24시간 안에 다시 전송하면 최초 결과를 재사용한다.

```json
{
  "success": true,
  "data": {
    "requestId": "01991aa8-ac6b-7907-a1fc-fc93c694f046",
    "sessionId": "01991a98-42db-7982-b442-13f26e7c468a",
    "state": "FOCUSED",
    "relevanceScore": 0.91,
    "confidence": 0.87,
    "reasonCode": "GOAL_RELATED",
    "reason": "현재 활동이 설정한 학습 목표와 관련되어 있습니다.",
    "analyzerVersion": "relevance-v1",
    "analyzedAt": "2026-09-05T03:15:01Z",
    "cached": true
  },
  "message": "관련성 분석이 완료되었습니다."
}
```

## 10. 검증 시나리오

- 정상 Desktop 입력이 분석 결과를 반환한다.
- 정상 Extension 입력이 분석 결과를 반환한다.
- 필수값 누락과 잘못된 UUID·시간 형식을 거부한다.
- `DESKTOP` 요청에 `appName`이 없으면 `INVALID_REQUEST`를 반환한다.
- `EXTENSION` 요청에 `domain`이 없으면 `INVALID_REQUEST`를 반환한다.
- `contentText`가 있는데 `textSource`가 없거나 허용되지 않은 값이면 `INVALID_REQUEST`를 반환한다.
- 4,000자를 초과한 `contentText`는 `CONTENT_TOO_LARGE`를 반환한다.
- 명백한 민감 정보가 포함되면 모델을 호출하지 않고 `SENSITIVE_CONTENT_DETECTED`를 반환한다.
- 인증 토큰이 없거나 잘못되면 `UNAUTHORIZED`를 반환한다.
- 동일한 요청을 재전송하면 최초 결과와 `cached: true`를 반환한다.
- 같은 `requestId`의 요청 내용이 달라지면 `REQUEST_ID_CONFLICT`를 반환한다.
- 모델 장애와 시간 초과를 `UNCERTAIN`이 아닌 오류 응답으로 반환한다.
- 요청 원문과 민감 정보가 로그와 캐시에 저장되지 않는다.

## 11. 후속 작업

- `analysis_rules.md`의 상태별 점수·신뢰도 임계값을 평가하고 확정한다.
- `prompt_guide.md`의 모델 출력 형식과 Prompt Injection 방어 규칙을 평가하고 확정한다.
- `AI/src/api/schemas.py`에 요청·응답 Schema를 구현한다.
- `AI/src/api/routes.py`에 관련성 분석 Endpoint를 구현한다.
- `AI/src/utils/cache.py`에 24시간 결과 캐시와 요청 Fingerprint 비교를 구현한다.
- Server 담당자와 인증, Timeout, 오류 코드 계약을 확인한다.
- 공통 상태 타입과 AI 응답 계약의 정합성을 유지하고, 변경 시 Desktop·Extension 소비자 빌드를 함께 검증한다.
- 관련 담당자 검토가 끝나면 이 문서의 상태를 `확정`으로 변경하고 합의 내용을 AI `DECISION_RECORD.md`에 기록한다.

### 후속 API 분리 원칙

- 페이지 이동, 이전 활동, 체류 시간은 후속 학습 흐름 분석 API에서 다룬다.
- `faceDetected`, `gazeState`, `poseState`는 후속 사용자 상태 보조 분석 API에서 다룬다.
- YouTube 자막과 PDF 추출 텍스트는 외부 콘텐츠 수집 정책을 확정한 뒤 별도 계약으로 추가한다.
- 후속 API에서도 원본 화면과 원본 카메라 영상은 입력받지 않는다.

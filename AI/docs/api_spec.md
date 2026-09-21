# AI 내부 API 계약 초안

## 상태

- 문서 상태: 설계 초안
- 구현 상태: `AI/src`와 `requirements.txt`가 비어 있어 실행 불가
- 외부 클라이언트 계약 원본: Notion `FocusOn API 명세서`

이 문서는 Spring Server가 AI 서비스를 호출할 때 사용할 내부 계약 후보를 설명한다. Desktop과 Extension은 AI 서비스를 직접 호출하지 않는다. 공개 API의 경로·응답·오류를 이 문서에서 새로 정의하지 않는다.

## 책임

```text
Desktop / Extension
  → 최소 활동 데이터
  → Spring Server
      → 세션·기기·개인정보 검증
      → AI 내부 서비스 호출
      → 공개 analysis-runs 계약으로 변환·저장
```

- Server가 제외 앱·도메인과 개인정보 정책을 먼저 적용한다.
- AI는 목표와 현재 활동의 관련성을 판단한다.
- 사용자·세션·리포트의 영구 저장은 Server가 담당한다.
- 원본 화면, 카메라 영상, 원본 URL query, token을 AI에 보내지 않는다.

## 내부 Endpoint 후보

```http
POST /internal/v1/relevance-analysis
Authorization: Bearer {serviceToken}
Content-Type: application/json
```

서비스 간 인증, timeout, payload 제한, 배포 주소는 구현 기획서에서 확정한다.

## Request 후보

```json
{
  "clientRunId": "run_01...",
  "sessionId": "session-uuid",
  "goal": {
    "goalId": "goal-uuid",
    "text": "Spring Security 인증 구조 공부"
  },
  "activity": {
    "source": "EXTENSION",
    "appName": "Google Chrome",
    "domain": "docs.spring.io",
    "title": "Spring Security Reference",
    "textSource": "PAGE_TEXT",
    "contentText": "민감 정보를 제거한 최소 분석 텍스트"
  },
  "observedAt": "2026-09-21T03:00:00Z"
}
```

## Response 후보

```json
{
  "clientRunId": "run_01...",
  "relation": "RELATED",
  "relevanceScore": 0.91,
  "confidence": 0.87,
  "reasonCode": "GOAL_RELATED",
  "analyzerVersion": "relevance-v1",
  "analyzedAt": "2026-09-21T03:00:01Z"
}
```

## 결과값

| `relation` | 의미 |
| --- | --- |
| `RELATED` | 현재 활동이 목표와 관련됨 |
| `UNRELATED` | 충분한 문맥에서 목표와 관련성이 낮음 |
| `UNCERTAIN` | 정보 또는 신뢰도가 부족함 |

공개 API에서 `PRIVACY_BLOCKED`, `EXCLUDED`, `UNCERTAIN`은 정상 분석 상태로 취급한다. 제외·개인정보 차단은 일반적으로 Server가 AI 호출 전에 결정한다.

## 구현 전 확정할 항목

- FastAPI, Pydantic, pytest 버전과 실행 명령
- 서비스 인증과 token rotation
- timeout과 최대 입력 길이
- 내부 오류를 공개 공통 오류로 변환하는 규칙
- `clientRunId` 중복 처리와 캐시 수명
- 평가 데이터 기준과 관련성 임계값

## 공개 계약 연결

- 요청 생성: `POST /api/v1/sessions/{sessionId}/analysis-runs`
- 결과 조회: `GET /api/v1/sessions/{sessionId}/analysis-runs`
- 공개 성공·오류 형식: `docs/API_CONTRACT.md`

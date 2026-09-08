# API 계약 규칙

이 문서는 Desktop, Extension, Server, AI 사이에서 공유할 기본 응답 형식을 정의한다. 실제 경로와 필드는 담당자 협의 후 확정한다.

## 공통 성공 응답

```json
{
  "success": true,
  "data": {},
  "message": "요청이 완료되었습니다."
}
```

## 공통 실패 응답

```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "요청 값을 확인해주세요."
  }
}
```

## 분석 결과 예시

```json
{
  "sessionId": "session-uuid",
  "source": "desktop",
  "state": "FOCUSED",
  "relevanceScore": 0.91,
  "confidence": 0.86,
  "reasonCode": "GOAL_RELATED",
  "reason": "학습 목표와 관련된 화면이 확인되었습니다.",
  "observedAt": "2026-09-03T10:00:00Z"
}
```

## 인증 및 기기 연결

Desktop에서 Google 로그인 후 Server가 Google 계정의 고유 식별자를 검증하고 FocusOn 사용자 토큰을 발급한다. Google Access Token을 Desktop과 Extension 사이에서 직접 공유하지 않는다.

Extension은 최초 1회 Desktop에 표시된 일회용 연결 코드 또는 QR을 사용해 같은 사용자의 기기로 연결한다.

예상 API:

```text
POST /api/v1/auth/google
POST /api/v1/devices/pairing-codes
POST /api/v1/devices/pair
GET  /api/v1/devices
```

연결 코드는 짧은 유효 시간과 1회 사용 조건을 가져야 한다.

## 학습 세션 동기화

Desktop과 Extension은 같은 `sessionId`를 사용한다. Server가 상태와 기준 시간을 관리하며, 클라이언트는 상태 변경 API를 호출한다.

예상 API:

```text
POST /api/v1/study-sessions
GET  /api/v1/study-sessions/active
POST /api/v1/study-sessions/{sessionId}/pause
POST /api/v1/study-sessions/{sessionId}/resume
POST /api/v1/study-sessions/{sessionId}/end
```

세션 상태 예시:

```json
{
  "sessionId": "session-uuid",
  "goalId": "goal-uuid",
  "status": "RUNNING",
  "plannedSeconds": 7200,
  "startedAt": "2026-09-05T03:00:00Z",
  "pausedAt": null,
  "totalPausedSeconds": 0,
  "serverTime": "2026-09-05T03:15:00Z",
  "version": 3
}
```

동시에 여러 명령이 도착하면 Server가 `version` 또는 서버 시각을 기준으로 오래된 요청을 거절하고 최신 세션 상태를 반환한다.

## 공통 타입 계약

TypeScript로 작성하는 공통 요청·응답 타입은 `packages/shared-types`에서 관리한다. 이 패키지는 데이터 전송이나 런타임 검증을 수행하지 않으며, Spring Boot DTO 검증과 AI 입력 스키마는 각 서버에서 별도로 유지한다.

## 기본 도메인

- 인증: Google 로그인, Desktop·Extension 기기 연결
- 사용자 설정: 휴식 시간, 카메라 사용 여부, 제외 앱·도메인
- 학습 목표: 목표 생성·수정·조회
- 학습 세션: 시작·일시정지·재개·종료
- 학습 이벤트: 앱·페이지 이동, 분석 상태, 집중 시간
- 분석: 관련성 판단 및 상태 결과
- 리포트: 집중 시간, 비집중 시간, 활동 목록

## 계약 규칙

- 날짜와 시간은 ISO 8601 형식과 UTC 기준을 사용한다.
- 식별자는 문자열 UUID를 우선 사용한다.
- 클라이언트는 `error.code`를 기준으로 분기하고 문구에 의존하지 않는다.
- 분석 결과는 신뢰도와 판단 근거를 함께 반환한다.
- API 변경은 Server와 모든 소비자에게 먼저 공유한다.

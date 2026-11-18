# 세션 분석 이력 조회 API 기획서

- 기능 ID: #272
- 우선순위: MVP 기본
- 브랜치: `feat/272-server-analysis-history`
- 정본: [분석 이력 API](https://app.notion.com/p/d6db8ba5fb1e44d8b3eb6cb08a43a2b1), [공통 오류 정책](https://app.notion.com/p/3de0b6e5e64c80f6bac6d980ba0c1df5)

## API

Desktop은 `GET /api/v1/sessions/{sessionId}/analysis-runs`에 Bearer 토큰을 보낸다. 세션 ID는 문자열이고 공통 인증·요청 제한을 적용한다. 요청 본문과 필수 기기 헤더는 없다.

본인 세션의 저장된 분석 결과를 `observedAt`, `analysisRunId` 순으로 오름차순 정렬한다. 동일 시각의 ID 정렬은 결과 순서를 고정하기 위한 서버 기준이다. 목록은 기존 성공 응답의 `data`에 담는다. 각 항목은 기존 분석 응답과 같은 결과 필드를 사용하며, `policyVersion`은 각 관측에 저장된 값을 반환한다.

```json
{
  "data": [
    {
      "analysisRunId": "42",
      "analysisStatus": "COMPLETED",
      "relation": "RELATED",
      "confidence": 0.9,
      "reasonCode": "SEMANTIC_GOAL_MATCH",
      "observedAt": "2026-11-18T01:00:00Z",
      "policyVersion": "v1"
    }
  ]
}
```

| 상황 | HTTP | 처리 |
| --- | --- | --- |
| 소유 세션에 분석 이력이 없음 | 200 | `{"data":[]}` |
| 인증 누락·실패 | 401 | 공통 인증 정책 |
| 연결된 Extension에서 조회 | 403 | `SESSION_QUERY_FORBIDDEN` |
| 세션 ID 형식이 잘못됐거나, 세션이 없거나, 다른 사용자가 소유 | 404 | `SESSION_NOT_FOUND` |
| 공통 요청 한도 초과 | 429 | `TOO_MANY_REQUESTS`와 `Retry-After` |

## 데이터와 흐름

1. 기존 인증 필터와 분석 서비스에서 Desktop 호출 및 세션 ID를 확인한다.
2. 읽기 전용 저장 계층에서 사용자 소유 세션을 확인한다. 진행 중인 세션과 종료된 세션 모두 조회할 수 있다.
3. `analysis_runs`의 세션 이력 인덱스 `ix_analysis_session_history`를 사용한다. 기기별 전경 관측 선별은 현재 개입 정책에서 계속 담당한다.
4. 기존 결과 DTO로 매핑한다. 쿼리 파라미터, 페이지네이션 또는 새 공개 필드를 도입하지 않는다. 화면·페이지 원문과 민감 메타데이터는 반환하지 않는다.

## 완료 조건

- 본인 세션에서 Bearer만으로 이력을 조회할 수 있다.
- 등록 순서와 관측 시각이 다르거나 관측 시각이 같아도 결과 순서가 일정하다.
- 완료·보호 결과의 필드 및 정책 버전, 빈 배열, 타인·없는 세션, 미인증과 Extension 접근 차단을 검증한다.
- 기존 분석 POST 회귀 테스트와 Backend 빌드에서 확인한다. 서버 검증 범위는 HTTP와 DB 저장 이력 조회다.

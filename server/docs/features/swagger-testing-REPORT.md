# 개발용 Swagger 테스트 설정 결과

- 작성일: 2026-10-27
- Issue: [#79](https://github.com/What-a-move/FocusOn/issues/79)
- Branch: `chore/#79-swagger-testing`
- 계획: [swagger-testing-PLAN.md](swagger-testing-PLAN.md)

## 구현

- 기본 UI·OpenAPI 비활성, 명시적 `local,swagger` 프로필의 loopback 접근.
- 문서 GET만 허용하는 별도 보안 체인, 기존 API 인증 유지.
- FocusOn JWT Authorize, 공개 exchange·refresh의 인증 요구 예외, 실제 구현 API 스키마.
- Google PKCE/state/임시 callback CLI, 비밀값을 출력하지 않는 0600 요청 JSON 준비.
- [실행·수동 테스트 사용법](../SWAGGER_TESTING.md), CONTEXT·NEXT_TASK·결정 027·문서 인덱스 갱신.

## 변경 위치

| 위치 | 역할 |
| --- | --- |
| `common/openapi/SwaggerConfiguration.java` | 문서 전용 체인·Bearer OpenAPI 모델 |
| 인증 Controller 2개 | 공개/보호 operation 설명 |
| application 기본·swagger 프로필 | 환경별 활성화·로컬 바인딩·UI 설정 |
| `scripts/google_pkce_request.py` | OAuth 요청 준비 도구 |
| `scripts/test_google_pkce_request.py`, JUnit Swagger 2개 클래스 | 도구·인증 경계 검증 |
| `server/docs/` | 사용법·계획·리뷰·QA·현재 상태 |

## 검증과 제한

Backend 62개·Python 10개·실제 PostgreSQL HTTP 32개를 통과했다. 독립 리뷰 Low 1건을 수정하고 재검토했다. [QA](swagger-testing-QA.md)와 [리뷰 기록](swagger-testing-code-review.md)에 범위·환경·제한을 남겼다.

새 제품 API·Entity·migration·토큰 수명·Frontend·Notion 변경은 없다. Google 실계정 성공은 사용자 본인 인증 후 별도 확인한다. 응답 성능/부하 개선 수치는 측정하지 않았다.

PR 생성·CI·CodeRabbit·merge·Issue 종료는 아직 진행하지 않았다. PR 초안 검토 및 별도 사용자 승인이 다음 단계다.

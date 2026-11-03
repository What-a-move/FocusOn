# Google 로그인 개발 오류 기록

- 날짜: 2026-10-25
- 관련 Issue: #49
- 상태: 아래 오류 모두 수정 후 회귀 검증
- 환경: Java 25, Spring Boot 4.1.1, H2 및 PostgreSQL 17.11

## 1. MockMvc 인증 요청이 401

- 재현: 새 토큰으로 GET /api/v1/auth/me를 호출했으나 401.
- 원인: 필터의 shouldNotFilter가 servletPath만 사용해 MockMvc의 빈 servletPath에서 인증 검사를 건너뛰었다.
- 수정: contextPath를 제외한 requestURI로 필터 적용 경로를 판정.
- 검증: 정상 Bearer의 auth/me 200 및 다른 X-Device-Id의 401 통과.

## 2. H2 동시 최초 로그인에서 사용자 중복

- 재현: 같은 Google subject로 네 Desktop이 동시에 최초 로그인할 때 여러 사용자가 생성됨.
- 원인: V1의 provider+subject UNIQUE는 PostgreSQL에 있었지만 H2 create-drop의 JPA 메타데이터에는 없었다.
- 수정: OauthIdentityEntity에 기존 V1과 같은 unique constraint를 명시. SQL migration 수정 없음.
- 검증: 사용자 1명·Desktop 3대 성공·1대 제한 테스트. 실제 PostgreSQL에서도 같은 사례 검증.

## 3. 테스트·스타일 진단

- Google JWK 모의 예외 생성자의 인자 누락과 이미 throw하도록 stub한 메서드 재설정 오류를 수정했다.
- import 정렬, Javadoc, 줄 길이, 사용 거리와 OAuth 클래스 이름을 Checkstyle 기준으로 맞췄다.
- 자세한 최종 실행 결과는 google-login-QA.md에 기록한다.

독립 리뷰에서 발견한 두 정책·보안 문제와 해결은 google-login-code-review.md에 분리 기록했다. 토큰·code·verifier·실제 계정 정보는 오류 기록에 남기지 않았다.

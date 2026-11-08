# Google 로그인 시각 오차 수정 결과

- Issue #177: Google ID 토큰의 발급 시각이 서버 시각보다 최대 30초 앞서도 허용한다. 허용 범위를 넘는 미래 발급 시각은 거부한다.
- 사용자가 수정 후 Google 로그인이 됐다고 확인했다. 테스트의 상세 요청과 응답은 별도로 수집하지 않았다.
- Backend 전체 `./gradlew build`와 Checkstyle이 통과했다. 서명된 테스트 토큰으로 20초 미래 발급은 허용하고 40초 미래 발급은 거부하는지 확인했다.
- 최신 dev `337713e` 통합 후 별도 PostgreSQL DB와 18087 서버에서 실제 Google 인증 코드 교환 201, 발급된 access token으로 `/api/v1/auth/me` 200을 확인했다. 기존 개발 DB는 사용하지 않았다.
- 다른 RSA 키로 서명한 토큰 거부 테스트를 추가했다. GoogleOauthClientTests 11개와 Backend 전체 build·Checkstyle이 통과했다. 발급자·대상·만료·서명 검증을 포함한다.
- 실제 실패 토큰의 발급 시각을 비교하지 못해 이전 401의 원인을 확정하지 않았다.
- #167은 PR #178에서 dev에 병합됐으며 해당 최신 dev를 이 브랜치에 통합했다.

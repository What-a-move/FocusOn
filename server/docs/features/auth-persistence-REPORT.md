# 인증 저장 모델 구현 결과

- 작성일: 2026-10-19
- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32)
- Branch: `feat/#32-auth-persistence`
- 기획서: [auth-persistence-PLAN.md](auth-persistence-PLAN.md)

## 구현

- Flyway V1에 `users`, `oauth_identities`, `user_devices`, `token_families`, `refresh_token_records`를 추가했다.
- PK/FK, 닉네임·OAuth subject·설치 해시·토큰 해시의 유일성, 상태값 CHECK, 기기당 활성 token family와 family당 활성 Refresh Token 제약을 정의했다.
- 동일한 5개 테이블의 JPA Entity와 Repository를 추가했다. 원본 Google Token, Access Token, Refresh Token, installation ID를 보관하는 컬럼은 없다.
- Spring Boot 4에서 Flyway 자동 설정을 활성화하도록 `spring-boot-starter-flyway`를 추가했다.
- API와 인증 서비스는 이번 범위에 포함하지 않는다.

## 확인 현황

- `./gradlew compileJava checkstyleMain --no-daemon`: 성공.
- `git diff --cached --check`: 성공.
- PostgreSQL 17.11의 빈 `focuson` 개발 DB에서 Flyway V1 적용 성공. `flyway_schema_history`에 `1:true`가 기록됐고 인증 테이블 5개가 생성됐다.
- Hibernate `ddl-auto=validate`와 Backend 기동: 성공. 기동에는 일회성 임시 JWT 키와 임의 로컬 포트를 사용했고, 확인 뒤 Backend 프로세스를 종료했다.
- 테스트 코드는 추가하거나 실행하지 않았다.
- 관련 오류 기록: [auth-persistence-ERROR.md](auth-persistence-ERROR.md)
- 독립 코드 리뷰에서 Refresh Token의 `issuedAt` 자동 설정 누락을 찾아 `@CreationTimestamp`를 추가했다. 상세: [auth-persistence-code-review.md](auth-persistence-code-review.md).
- 해당 수정 후 `./gradlew compileJava checkstyleMain --no-daemon`을 다시 실행해 성공했다.

## 남은 작업

- 코드 리뷰 후 변경분 컴파일 확인과 QA.
- PR 초안은 사용자에게 먼저 보여준 뒤 별도 승인 시 생성.

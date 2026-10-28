# 인증 저장 모델 구현 결과

- 작성일: 2026-10-19
- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32)
- 관련 PR: [#35](https://github.com/What-a-move/FocusOn/pull/35)
- Branch: `feat/#32-auth-persistence`
- 기획서: [auth-persistence-PLAN.md](auth-persistence-PLAN.md)

## 구현

- Flyway V1에 `users`, `oauth_identities`, `user_devices`, `token_families`, `refresh_token_records`를 추가했다.
- V1 적용 후 리뷰에서 찾은 Token Family의 사용자·기기 소유 관계를 Flyway V2의 복합 FK로 보강했다.
- PK/FK, 닉네임·OAuth subject·설치 해시·토큰 해시의 유일성, 상태값 CHECK, 기기당 활성 token family와 family당 활성 Refresh Token 제약을 정의했다.
- 동일한 5개 테이블의 JPA Entity와 Repository를 추가했다. 원본 Google Token, Access Token, Refresh Token, installation ID를 보관하는 컬럼은 없다.
- Google identity 로그인 조회는 `unlinked_at IS NULL`인 행만 반환한다. 전체 Google subject 유일성은 ERD 기준으로 유지한다.
- Spring Boot 4에서 Flyway 자동 설정을 활성화하도록 `spring-boot-starter-flyway`를 추가했다.
- API와 인증 서비스는 이번 범위에 포함하지 않는다.

## 확인 현황

- `./gradlew compileJava checkstyleMain --no-daemon`: 성공.
- `git diff --cached --check`: 성공.
- PostgreSQL 17.11의 빈 `focuson` 개발 DB에서 Flyway V1 적용 성공. `flyway_schema_history`에 `1:true`가 기록됐고 인증 테이블 5개가 생성됐다.
- PostgreSQL 17.11에서 Flyway V2 적용 성공. `flyway_schema_history`에 `1:true`, `2:true`가 기록됐다.
- Hibernate `ddl-auto=validate`와 Backend 기동: 성공. 기동에는 일회성 임시 JWT 키와 임의 로컬 포트를 사용했고, 확인 뒤 Backend 프로세스를 종료했다.
- H2 테스트 프로필에서 연결 해제된 Google identity 조회와 Refresh Token `issuedAt` 생성을 확인하는 Repository 테스트 2개를 추가했다.
- 관련 오류 기록: [auth-persistence-ERROR.md](auth-persistence-ERROR.md)
- 독립 코드 리뷰에서 Refresh Token의 `issuedAt` 자동 설정 누락을 찾아 `@CreationTimestamp`를 추가했다. 상세: [auth-persistence-code-review.md](auth-persistence-code-review.md).
- 해당 수정 후 `./gradlew compileJava checkstyleMain --no-daemon`을 다시 실행해 성공했다.
- 추가 리뷰에서 Token Family 소유 관계와 해제된 OAuth 계정 조회 문제를 확인해 반영했다. 상세: [auth-persistence-code-review.md](auth-persistence-code-review.md).
- Repository 변경 후 `./gradlew compileJava checkstyleMain --no-daemon`을 다시 실행해 성공했다.
- V2 복합 FK는 PostgreSQL에서 잘못된 사용자·기기 조합을 거부하고 정상 조합을 허용했다. QA용 행은 모두 rollback했다. 상세: [auth-persistence-QA.md](auth-persistence-QA.md).

## 남은 작업

- PR #35의 Backend CI 통과 확인. 사용자 승인 후 2026-10-22 `dev` 병합과 Issue #32 종료를 완료했다.

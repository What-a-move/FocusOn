# 인증 저장 모델 개발 오류 기록

- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32)
- 발생일: 2026-10-19
- 상태: 해결

## Checkstyle 이름·주석 규칙 위반

- 환경: Java 25, Spring Boot 4.1.1, Gradle Checkstyle
- 재현: `./gradlew checkstyleMain --no-daemon`
- 실제 결과: `OAuthIdentity*`의 약어 표기, Repository 메서드명의 `_`, 공개 enum·메서드의 Javadoc 누락으로 Checkstyle 실패.
- 원인: Java 컴파일은 통과했지만 저장소가 적용하는 Google Java Style 규칙과 새 코드가 달랐다.
- 해결: Java 클래스 이름을 `OauthIdentity*`로 정리하고, 복합 관계 조회는 명시적 JPQL 메서드로 바꿨다. 공개 enum과 Repository 메서드에 Javadoc을 추가했다.
- 확인: `compileJava checkstyleMain` 성공. PostgreSQL migration 실행과 API 동작은 이 확인에 포함되지 않는다.

## Spring Boot 4의 Flyway 자동 설정 누락

- 환경: Spring Boot 4.1.1, PostgreSQL 17.11, 빈 개발 DB
- 재현: 기존 `flyway-core` 의존성으로 `bootRun`하면 DB 연결 뒤 Hibernate가 `Schema validation: missing table [oauth_identities]`로 중단한다.
- 기대 결과: Hibernate 검증 전 Flyway V1이 적용되어야 한다.
- 원인: Spring Boot 4에서는 Flyway 자동 설정용 `spring-boot-starter-flyway`가 필요하다. `flyway-core`와 PostgreSQL 모듈만으로는 자동 설정이 등록되지 않았다.
- 해결: `server/build.gradle`에 `spring-boot-starter-flyway`를 적용하고 PostgreSQL용 `flyway-database-postgresql`은 유지했다.
- 확인: 빈 PostgreSQL 17 DB에 Flyway V1 적용 성공, `flyway_schema_history`의 `1:true`, Hibernate 스키마 검증 및 Backend 기동 성공.

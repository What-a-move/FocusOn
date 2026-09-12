# Backend 테스트 코드 규칙

## 프레임워크 (확인된 것과 확인 필요한 것)

- **JUnit5**: 확인됨. `server/build.gradle`에 `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`가 있고 `tasks.named('test') { useJUnitPlatform() }`로 설정되어 있다.
- **Mockito / AssertJ**: `server/build.gradle`에 명시적인 `mockito-core`/`assertj-core` 의존성이 없다. 이 프로젝트는 표준 `spring-boot-starter-test` 대신 계층별로 분리된 `-test` 스타터(`spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-webmvc-test`)를 쓰고 있어, Mockito/AssertJ가 자동으로 포함되는지 **확인이 필요하다**. 실제 테스트 작성 시 `import org.mockito.*` / `import org.assertj.core.api.Assertions.*`가 컴파일되는지 먼저 확인하고, 안 되면 명시적으로 의존성을 추가한 뒤 이 문서를 갱신한다.
- **DB**: 테스트는 `server/src/test/resources/application-test.properties` + H2(`testRuntimeOnly 'com.h2database:h2'`)를 쓴다. PostgreSQL 전용 문법(JSONB, 특정 함수 등)을 쓰는 로직은 H2로 검증되지 않으므로, 그런 로직은 Docker Compose로 실제 PostgreSQL을 띄워 별도 확인한다(`server/docs/rules/migration-convention.md` 참고).

## 클래스 구조 (권장, GONE 관례 기반 — 팀 확정 전까지 권장안)

- 메서드 단위로 `@Nested` 클래스 + `@DisplayName("메서드명")`으로 그룹화한다.
- 각 테스트 케이스는 `@Test @DisplayName("한글로 시나리오 설명")`을 쓴다.
- Mock을 쓸 경우 `@Mock` + `@InjectMocks`를 쓰고, 실제 PostgreSQL·외부 API는 호출하지 않고 목킹한다.

## 검증 범위

- 정상 케이스뿐 아니라 예외 케이스(`server/docs/DEVELOPMENT_RULES.md` BE-002의 ErrorCode에 대응하는 상황)까지 반드시 작성한다.
- 인증이 필요한 API는 인증 실패(401)·권한 부족(403) 케이스를 포함한다.
- 새 Service·Controller 로직을 추가하면 대응하는 테스트를 반드시 함께 작성한다(기획서에 정의된 엔드포인트당 최소 1개 이상).
- 커밋 전 `./server/gradlew -p server test` 통과를 확인한다(`docs/ENVIRONMENT.md` 실행 명령 기준).

## 현재 상태

- `server/src/test/java/com/capstone/backend/BackendApplicationTests.java` 외에 아직 실제 도메인 테스트 코드는 없다(Controller/Service 자체가 아직 없는 초기 단계). 이 문서는 앞으로 도메인 코드가 추가될 때부터 적용한다.

## 관련 문서

- `docs/ENVIRONMENT.md`
- `server/docs/DEVELOPMENT_RULES.md`
- `server/docs/rules/migration-convention.md`

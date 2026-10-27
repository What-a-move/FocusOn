# 사용자 설정 API 개발 오류 기록

- 관련 Issue: [#38](https://github.com/What-a-move/FocusOn/issues/38)
- Branch: `feat/#38-user-configuration`
- 작성일: 2026-10-26
- 상태: 해결

## 작업 중 해결한 문제

### Checkstyle 실패

- 재현: `./gradlew compileJava checkstyleMain --no-daemon`
- 현상: Java 컴파일은 통과했지만 새 사용자 설정 파일에서 Checkstyle 경고 15건으로 실패했다.
- 원인: 줄 길이 제한 초과와 Repository 메서드 Javadoc 누락, lambda 중괄호 형식 불일치다.
- 조치: 줄바꿈·Repository Javadoc·lambda 형식을 Google Java Style에 맞게 정리했다. Spring 생성자 선택 오류도 주입 생성자에 `@Autowired`를 지정해 해결했다.

## 알려진 제한

- 온보딩 준비 상태는 Extension pairing #58의 연결 상태를 필요로 하므로 이 작업 범위에서 구현하지 않는다.
- `./gradlew test checkstyleMain --no-daemon`: 성공

### 집중 설정 첫 저장 500

- 재현: 집중 설정이 없는 사용자가 `PUT /api/v1/users/me/focus-settings`를 호출했다.
- 현상: Hibernate가 `UserFocusSettingsEntity`의 null identifier를 감지해 500을 반환했다.
- 원인: `user_focus_settings.user_id`가 사용자 FK이면서 PK인데 Entity가 관계 매핑으로 이를 중복 관리했다.
- 조치: Entity의 식별자를 `userId` 단일 필드로 유지하고, Service가 인증 사용자 ID를 생성자에 전달하도록 바꿨다.
- 검증: 사용자 설정 API 전용 MockMvc 테스트와 전체 `./gradlew test checkstyleMain checkstyleTest --no-daemon`을 통과했다.

### Swagger 문서 경로 401

- 재현: H2 기반 MockMvc에서 `GET /v3/api-docs`
- 현상: Bearer 토큰이 있어도 401을 반환했다.
- 원인: Security 설정이 `/api/v1/**`와 health만 허용하고, Swagger 문서·UI 경로를 `denyAll()`로 차단했다.
- 조치: `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`을 공개 허용하고, 인증 없이 OpenAPI 문서가 생성되는 MockMvc 테스트를 추가했다.

### Swagger 인증·오류 본문 문서화 누락

- 재현: 재빌드한 로컬 Swagger UI에서 사용자 설정 API를 열고 인증 없는 `GET /api/v1/users/me`를 실행했다.
- 현상: 실제 응답은 `401 AUTH_RELOGIN_REQUIRED` 공통 오류 형식이지만, 문서에는 성공 DTO 예시가 표시됐다. 또한 Bearer 토큰을 입력할 `Authorize` 설정이 없었다.
- 원인: OpenAPI Security Scheme과 Controller의 Security Requirement가 없었고, 오류 응답에 media type·`ApiErrorResponse` schema를 명시하지 않았다.
- 조치: `OpenApiConfiguration`에 `bearerAuth` HTTP Bearer scheme을 추가하고 사용자 설정 Controller에 적용했다. 400·401·409 응답에는 `application/json`과 `ApiErrorResponse` schema를 명시했다.
- 검증: `/v3/api-docs` MockMvc 테스트에서 bearer scheme, API 보안 요구사항, 401 오류 schema reference를 확인했다.

### 분석 제외 도메인 후행 점 거부

- 재현: `PUT /api/v1/users/me/analysis-excluded-domains`에 `YouTube.COM.`을 전달했다.
- 현상: 계획서의 후행 점 제거 기준과 달리 400으로 거부했다.
- 원인: `normalizeHost`가 후행 점을 제거하지 않고 유효하지 않은 값으로 분류했다.
- 조치: 단일 후행 점을 IDNA 변환 전에 제거해 `youtube.com`으로 정규화했다. 연속 후행 점과 빈 host는 기존 정규식 검증에서 계속 거부한다.
- 검증: 사용자 설정 MockMvc 테스트에서 `YouTube.COM.`과 `youtube.com`이 한 개의 `youtube.com`으로 중복 제거되는 것을 확인했다.

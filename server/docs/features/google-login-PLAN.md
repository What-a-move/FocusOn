# Google 로그인 교환 및 인증 사용자 조회 기획서

> 상태: 2026-10-25 사용자 승인 완료. Issue #49에서 문서 push 후 구현을 진행한다.

## 기본 정보

- 기능명: Google 로그인 교환 및 인증 사용자 조회
- 기능 ID: `google-login`
- 작성일: 2026-10-24
- 재검토일: 2026-10-25 (`dev` 0b88bb8, Desktop 인증·목표 클라이언트와 최신 API 대조)
- 우선순위: MVP - 핵심
- 관련 Issue: #49
- 예정 Branch: `feat/#49-google-login`
- Labels: `area:server`, `type:feature`, `priority:mvp-core`
- 선행 작업: 인증 저장 모델 PR #35는 `dev`에 병합됐고 Issue #32는 종료됐다.
- 연동 대상: Desktop 인증 클라이언트; 사용자·목표 API Issue #38

## 기능 목적과 전체 로그인 작업 순서

Google에서 검증한 계정을 FocusOn 사용자·Desktop 기기로 연결하고, 기존 Access JWT 서비스와 인증 저장 모델을 실제 HTTP 요청에 연결한다. 백엔드 팀원이 인증된 사용자·기기를 일관된 인터페이스로 사용할 수 있게 한다.

저장소의 Issue당 API 1~2개 규칙과 순차 진행 규칙에 따라 다음 순서로 진행한다. 이 기획서의 승인 대상은 첫 번째 작업이다. 후속 Issue와 기획서는 현재 작업을 마무리한 뒤 작성한다.

1. 이번 작업: `POST /api/v1/auth/google/exchange`, `GET /api/v1/auth/me`, 공통 요청 인증과 초기 토큰 발급.
2. 후속 작업: `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, 원자적 rotation·재사용 감지·폐기 범위.
3. 후속 작업: `GET /api/v1/devices`, `DELETE /api/v1/devices/{deviceId}`, 기존 기기 선택 폐기.

사용자·동의·설정·목표 API는 Issue #38의 담당 범위를 유지한다. Extension pairing API, 세션 API, 개인정보 보관 cleanup worker는 별도 기능이다.

## 확인한 정본과 구현

2026-10-24 브라우저에서 로그인 교환·refresh·auth/me API, 인증·토큰·디바이스 정책과 공통 Error Code 정책을 읽었다. Notion 연결 도구는 해당 페이지에 404를 반환해 브라우저로 확인했다. Notion 내용은 수정하지 않았다.

- [Google 로그인 교환 API](https://app.notion.com/p/00f3391cae454614bf51bffbe995f0dc)
- [현재 인증 정보 API](https://app.notion.com/p/2ad306d88bfe42c094c900ceb3e7aff4)
- [Refresh API](https://app.notion.com/p/bacabe41fdc543f9a2575fc05e2e1f81)
- [인증·토큰·디바이스 정책](https://app.notion.com/p/3de0b6e5e64c80c89c6cfe0b703fd964)
- [공통 Error Code 정책](https://app.notion.com/p/Error-Code-3de0b6e5e64c80f6bac6d980ba0c1df5)
- [ERD](https://app.notion.com/p/ERD-3de0b6e5e64c80a4bf5ef7ff3ac01f17): 기존 V1·V2의 인증 5개 테이블을 재사용한다.
- [Google Desktop OAuth 공식 문서](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Google ID token 검증 공식 문서](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)
- 현재 서버에는 `AccessJwtService`, 인증 Entity·Repository와 공통 오류 응답이 있다. 로그인 Controller·Service와 Security Filter는 아직 없다.
- Desktop은 `apps/desktop/electron/main/auth-api.cts`에서 실제 API 계약의 필드와 HTTP 상태를 검증한다. 임의의 성공 응답으로 대체하지 않는다.

## API 정의

### POST /api/v1/auth/google/exchange

- 호출 주체: macOS Desktop Main
- FocusOn 인증 헤더: 없음
- 정상 응답: `201 Created`
- 요청 제한: IP + installationId 기준 10분당 5회; 초과 시 429와 `Retry-After`.

```json
{
  "authorizationCode": "google_authorization_code",
  "codeVerifier": "43_to_128_unreserved_characters",
  "state": "state_already_verified_by_desktop",
  "redirectUri": "http://127.0.0.1:49152/",
  "device": {
    "installationId": "desktop-installation-id",
    "deviceName": "FocusOn MacBook",
    "platform": "MACOS"
  }
}
```

```json
{
  "data": {
    "accessToken": "focuson_access_token",
    "refreshToken": "focuson_refresh_token",
    "accessTokenExpiresAt": "2026-10-24T03:15:00Z",
    "user": { "userId": "1", "nickname": null },
    "device": { "deviceId": "1", "deviceType": "DESKTOP" },
    "onboardingStatus": "INCOMPLETE"
  }
}
```

검증·처리 기준:

- `redirectUri`는 HTTP, 정확한 host `127.0.0.1`, 명시적 유효 포트, 빈 기본 경로 또는 `/`만 허용한다. userinfo·query·fragment·다른 host·scheme·경로는 거절한다. 검증 뒤 문자열을 재작성하지 않고 Google에 동일하게 전달한다.
- `codeVerifier`는 Google PKCE의 43~128자와 unreserved 문자 규칙을 따른다. `state`의 최초 값과 callback 일치 여부는 Desktop Main의 책임이며 서버가 검증했다고 기록하지 않는다.
- Google token endpoint는 서버에 고정된 주소를 사용한다. Desktop용 Client ID와 code·verifier·redirect URI를 form body로 전송한다. 클라이언트가 Google endpoint나 Client ID를 지정할 수 없다.
- Google ID token은 기존 JWT 라이브러리로 Google 공개 키의 서명, issuer, Desktop Client ID audience, 만료와 필수 `sub`를 검증한다. 이메일을 계정 키로 사용하지 않는다.
- Google 교환은 DB 잠금을 잡기 전에 수행하며 일회용 code를 자동 재전송하지 않는다. 외부 오류 원문과 토큰은 로그에 남기지 않는다.
- 검증된 `sub`로 연결된 사용자만 찾거나 생성한다. 최초 동시 로그인은 OAuth subject 유니크 제약과 전체 트랜잭션 재시도로 중복 사용자·고아 행을 남기지 않는다. 해제된 identity나 탈퇴 사용자를 자동 복구하지 않는다.
- 사용자 행 잠금 안에서 설치 해시로 기기를 찾거나 생성하고, 활성 Desktop 최대 3대를 적용한다. 같은 설치의 정상 재로그인을 새 기기로 중복 집계하지 않는다. 자동으로 다른 기기를 폐기하지 않는다.
- 같은 기기의 기존 활성 family를 정리한 뒤 새 family와 30일 Refresh Token을 발급하는 전 과정을 원자적으로 처리한다. 기존 V1·V2의 FK와 부분 유니크 인덱스를 유지한다.
- Access Token은 기존 서비스의 15분 정책을 사용한다. 신규 사용자는 닉네임 없이 `INCOMPLETE`이며, 기존 사용자의 온보딩 상태를 임의로 완료 처리하지 않는다.

### GET /api/v1/auth/me

- 호출 주체: Desktop·Chrome Extension
- 필수 헤더: `Authorization: Bearer {accessToken}`
- `X-Device-Id`는 이 API 명세의 필수 헤더가 아니므로 새로 강제하지 않는다.
- 정상 응답: `200 OK`
- 요청 제한: 인증된 userId + deviceId 기준 분당 60회.

```json
{
  "data": {
    "user": { "userId": "1", "nickname": null },
    "device": { "deviceId": "1", "deviceType": "DESKTOP" },
    "onboardingStatus": "INCOMPLETE"
  }
}
```

클라이언트가 보낸 사용자 ID로 조회하지 않고, 검증된 토큰이 가리키는 현재 사용자·기기를 반환한다. 응답 구조는 로그인 교환 응답의 사용자·기기 구조 및 기존 Desktop 파서와 맞춘다.

## 공통 인증과 팀원 연동 인터페이스

- Stateless Spring Security 인증을 사용한다. Google 교환·후속 refresh·공개 health 경로만 별도 허용하며, 폼 로그인과 Basic 인증을 노출하지 않는다.
- JWT 서명·issuer·audience·클레임 검증 후 사용자 미탈퇴, 기기 소유·활성 상태, family 소유·기기 일치·활성·유효 기간을 DB에서 검증한다. 유효한 서명만으로 요청을 인증하지 않는다.
- 내부 principal 초안은 `FocusOnPrincipal(userId, deviceId, tokenFamilyId, deviceType)`이다. ID는 내부 `long`, 공개 JSON에서는 문자열이다. 팀원은 `@AuthenticationPrincipal`로 principal을 받고, 요청의 사용자 식별자를 신뢰하지 않는다.
- `X-Device-Id`가 필수인 후속 API는 principal의 기기와 헤더가 일치하는지 공통 검증기를 호출한다. 필수 여부는 각 API 정본을 따른다.
- 필터에서 발생한 오류도 기존 `ApiErrorResponse`와 `requestId`를 사용한다. 만료된 정상 서명 JWT와 위조·형식 오류·폐기된 인증을 구분하며 만료 여부를 unsigned claim으로 판정하지 않는다.
- 접근 권한 오류에 새 임의 code를 만들지 않는다. 필요한 공개 오류가 정본에 없다면 해당 케이스만 별도 검토하고 계약을 추측해 확장하지 않는다.

### 로그인 유지와 후속 refresh의 연결

- Access Token 15분 만료만으로 로그아웃시키지 않는다. 정상 서명의 만료는 `AUTH_ACCESS_TOKEN_EXPIRED`로 반환해 Desktop의 single-flight refresh 경로로 연결한다.
- 현재 `AccessJwtService.verify`는 JWT 검증 실패를 공통 `IllegalArgumentException`으로 감싼다. 요청 인증 필터를 구현할 때 검증된 만료와 위조·기타 실패를 구분하도록 내부 오류 전달을 보강해야 한다. 서명 검증 전 payload의 `exp`만 읽어 만료로 분류하지 않는다.
- Desktop은 API 호출 시 만료 30초 전부터 갱신을 시도한다. 앱 재실행은 Keychain의 Refresh Token으로 복구하며, 로컬 타이머만으로 로그인 유효성을 보장하지 않는다. 네트워크·503·timeout은 토큰 폐기 근거로 쓰지 않는다.
- 최초 로그인에서 Refresh Token을 발급하는 작업과 refresh HTTP API 완성을 구분한다. 첫 Issue만 끝났을 때 실제 로그인 유지·재실행 복구까지 완료했다고 표시하지 않는다.
- `token_families.expires_at`과 개별 Refresh Token의 `expires_at`은 별도다. 후속 refresh 구현 전에 family의 고정 만료인지 갱신에 따른 연장인지 정본과 맞춘다. 단순 rotation만으로 family 수명이 자동 연장되거나 무기한 로그인된다고 가정하지 않는다. 기획 초안의 제안은 대조 기록에서 추적한다.

## 데이터 설계와 트랜잭션

- 재사용 Entity: `UserEntity`, `OauthIdentityEntity`, `UserDeviceEntity`, `TokenFamilyEntity`, `RefreshTokenRecordEntity`.
- 원본 installation ID는 SHA-256 해시로만 저장한다. Refresh Token은 암호학적 난수로 생성하고 SHA-256 해시만 저장한다.
- Google Access·Refresh·ID token, authorization code, verifier, state는 DB에 저장하지 않는다. DTO·토큰 객체의 `toString()`에서도 원문을 숨긴다.
- 계정·기기·family·초기 Refresh Token 저장과 재로그인 시 기존 family 정리는 한 DB 트랜잭션으로 처리한다.
- V1·V2를 수정하거나 `ddl-auto=update`로 스키마를 바꾸지 않는다. 추가 DB 구조가 필요한 경우 팀원 migration 번호와 충돌하지 않도록 최신 `dev`와 정본을 먼저 확인한다.
- 현재 Docker Compose의 단일 Backend 인스턴스를 기준으로 로그인 rate limit은 원자적 메모리 카운터와 만료 정리를 사용한다. 프로세스 재시작 시 카운터가 초기화되는 제한을 명시하고, 다중 인스턴스 배포 전에는 공유 저장소나 게이트웨이로 옮긴다. 임의의 forwarded header로 IP 한도를 우회하지 않도록 배포 proxy의 신뢰 경계도 검증한다.

## 예외 처리

| 상황 | HTTP | code | 처리 |
| --- | --- | --- | --- |
| 필수값·PKCE·redirect URI 형식 오류 | 400 | `VALIDATION_ERROR` | 안전한 필드명만 전달 |
| Google code·ID token 인증 실패 | 401 | `AUTH_OAUTH_REAUTH_REQUIRED` | 인증 단계 재시작; 일반 refresh 오류에 재사용하지 않음 |
| 정상 서명의 Access Token 만료 | 401 | `AUTH_ACCESS_TOKEN_EXPIRED` | 클라이언트가 후속 refresh를 1회 수행 |
| FocusOn 인증 누락·위조·폐기·소유 관계 불일치 | 401 | `AUTH_RELOGIN_REQUIRED` | 인증 실패; 토큰 원문 미노출 |
| unlink된 Extension 요청 | 401 | `LINK_TOKEN_REVOKED` | 재연결 안내 |
| 활성 Desktop 3대 초과 | 409 | `AUTH_DEVICE_LIMIT_EXCEEDED` | 해당 사용자의 해제 가능한 기기 목록; 토큰 미발급 |
| 요청 한도 초과 | 429 | `TOO_MANY_REQUESTS` | 본문과 `Retry-After`의 대기 시간 일치 |
| Google 일시 장애 | 503 | `SERVICE_UNAVAILABLE` | 일시 장애 안내; code 자동 재전송 금지 |
| Google timeout | 504 | `REQUEST_TIMEOUT` | 인증 흐름 재시작 여부를 클라이언트가 처리 |

공통 오류 본문은 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details`를 사용한다. 내부 예외·Google 응답 원문·비밀값은 포함하지 않는다.

## 검증 계획과 완료 조건

- [ ] 기존 Desktop 요청·응답 파서와 두 API의 계약이 일치한다.
- [ ] Google 교환 요청이 정확한 Client ID·code·verifier·redirect URI를 전송한다.
- [ ] 허용하지 않은 redirect URI, 잘못된 서명·issuer·audience·만료·누락된 sub를 거부한다.
- [ ] 신규·기존·동시 사용자 로그인과 동일 설치 재로그인, 4번째 Desktop 거절을 검증한다.
- [ ] JWT principal과 DB 사용자·기기·family의 소유·활성 검증을 확인한다.
- [ ] 인증 실패·폐기·만료·rate limit에 공통 오류와 requestId를 반환한다.
- [ ] 정상 서명 만료 JWT와 위조 JWT가 각각 만료·재로그인 오류로 구분돼 클라이언트 갱신 경로를 깨지 않는다.
- [ ] PostgreSQL에서 소유권 FK·활성 family/record 유일성과 트랜잭션 rollback을 확인한다.
- [ ] 토큰·code·verifier·state·원본 installation ID가 DB·로그·예외에 남지 않는다.
- [ ] 의미 있는 단위·HTTP·동시성 검증과 기존 테스트, Checkstyle, 빌드를 통과한다.
- [ ] 격리된 코드 리뷰, QA 결과와 실제 Google 왕복 검증 여부를 구분해 기록한다.
- [ ] `CONTEXT.md`, `NEXT_TASK.md`, 오류·결과 문서를 실제 상태에 맞춘다.

자동화 테스트는 Google token/JWK 응답을 모의해 결정적으로 실행한다. 실제 계정 왕복은 서버·Desktop에 동일한 Google Client ID를 설정한 뒤 사용자 인증이 필요한 수동 QA로 확인하며, 미실행이면 성공했다고 기록하지 않는다.

## 예상 변경 파일

```text
server/src/main/java/com/capstone/backend/auth/...
server/src/main/java/com/capstone/backend/auth/persistence/...Repository.java
server/src/main/java/com/capstone/backend/common/api/ApiErrorCode.java
server/src/main/resources/application.properties
server/src/test/java/com/capstone/backend/auth/...
server/src/test/resources/application-test.properties
server/.env.example
server/docker-compose.yml
server/docs/features/google-login-PLAN.md
server/docs/features/google-login-ERROR.md
server/docs/features/google-login-REPORT.md
server/docs/features/google-login-code-review.md
server/docs/features/google-login-QA.md
server/docs/CONTEXT.md
server/docs/NEXT_TASK.md
docs/ENVIRONMENT.md
```

## 후속 작업에서 확인할 계약 경계

- 2026-10-24 사용자가 공유·정정한 온보딩·리포트·개입 계약은 `docs/API_CONTRACT.md`, `server/docs/DECISION_RECORD.md` 결정 017·018에 반영한다. 이번 로그인 Issue의 API 수를 늘리거나 팀원 Issue #38의 사용자·동의 저장 API를 중복 구현하지 않는다.
- Extension 연결의 공개 응답은 `deviceType: EXTENSION`이고 DB·principal의 내부 enum은 `CHROME_EXTENSION`이다. 내부 enum이나 V1 migration을 바꾸는 방식으로 응답을 맞추지 않는다. 향후 응답 DTO에서 변환한다.
- 동의는 현재 버전·확정한 필드명을 사용하고 알림 준비 상태는 readiness API·DB에 새 필드를 추가하지 않는다. 3초 polling은 다른 인증 요청과 60회/분 한도를 공유한다.
- pairing 오입력의 실패 횟수를 귀속할 시도 문맥과 heartbeat 관측 시각·rolling window의 미정 부분, Notion 내 남은 충돌은 `docs/NOTION_SYNC_AUDIT.md`를 확인한다. 로그인 구현이 이 기능들까지 완료됐음을 뜻하지 않는다.
- Refresh rotation·로그아웃 API는 다음 Issue다. Refresh Token을 발급했다고 갱신·로그아웃까지 완료됐다고 표시하지 않는다.
- 4번째 Desktop에서 로그인 오류만 받은 사용자는 아직 FocusOn 토큰이 없다. 현재 기기 폐기 API는 Authorization이 필요하므로, 새 기기에서 즉시 기존 기기를 선택 폐기하는 전체 UX에는 인증 수단이 추가로 명확해야 한다. 이번 작업은 확정된 409와 기기 목록 반환을 지키며 새 임시 토큰·endpoint를 임의로 만들지 않는다. 기기 관리 작업 전에 이 경계를 검토한다.
- 온보딩 상태의 상세 판정은 Issue #38과 연결한다. 이 작업에서 닉네임·동의·권한·분석 제외 사이트 저장 API를 중복 구현하지 않는다.

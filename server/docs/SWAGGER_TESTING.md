# Swagger로 백엔드 로그인 테스트하기

Swagger는 현재 구현된 서버 API를 실행하는 도구다. 엔드포인트 계약 정본은 Notion `FocusOn API 명세서`이며, Swagger에 미구현 API는 나타나지 않는다. 이 설정은 [Issue #79](https://github.com/What-a-move/FocusOn/issues/79)의 개발 환경용이다.

## 1. 실행 준비

macOS에서 Java 25·Python 3·PostgreSQL 17이 필요하다. 저장소 루트에서 실행한다. `server/.env`에 DB·JWT·Desktop Google Client ID/Secret을 준비하며 파일을 커밋하지 않는다. 새 checkout에는 `.env`가 없으므로 별도 준비한다. 터미널의 `set -x`는 끈다.

```bash
docker compose --env-file server/.env -f server/docker-compose.yml up -d postgres
./server/gradlew -p server bootRun
```

DB가 이미 실행 중이면 Compose 명령은 생략한다. local 프로필은 `POSTGRES_PORT`가 있으면 그 값을, 없으면 `.env`의 `POSTGRES_HOST_PORT`를 사용한다.

서버의 local 프로필은 `.env`를 자동으로 읽으므로 서버 실행 전에 `source .env`를 할 필요가 없다. `server/`에서 실행할 때는 `./.env`, 저장소 루트에서 실행할 때는 `./server/.env`를 읽는다. 두 파일이 모두 있으면 server 쪽 값이 우선한다. IDE 실행도 두 디렉터리 중 하나를 작업 디렉터리로 사용한다. 파일 값보다 OS 환경변수·JVM 옵션·실행 인자가 우선한다.

`.env`는 `.env.example`처럼 `KEY=VALUE` 형식으로 쓴다. Spring이 Properties 형식으로 읽기 때문에 값 바깥의 따옴표·`export`·shell 명령을 붙이지 않는다. 파일이 없어도 OS 환경변수로 필수 DB/JWT 값을 제공할 수 있다. 자동 읽기는 local 전용이며 docker 프로필은 Compose가 전달하는 환경변수를 그대로 사용한다.

브라우저에서 [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)를 연다. OpenAPI JSON은 [API 문서](http://127.0.0.1:8080/v3/api-docs)다. `bootRun`의 기본 포트는 8080이고, 다른 포트는 `--args='--server.port=...'`로 지정한다.

- 기본 local 프로필은 서버 주소를 `127.0.0.1`로 제한하고 Swagger를 자동으로 켠다. 별도 swagger 프로필·실행 인자가 필요 없다.
- docker/test 프로필에서는 공통 설정의 Swagger 비활성을 유지한다. Docker를 개발에 쓰는 팀도 있지만, 현재 프로젝트에서는 Docker 실행에 Swagger를 켜지 않는다. 배포할 때는 Compose가 지정하는 docker 프로필을 사용한다.
- Swagger UI의 인증 영구 저장과 외부 validator를 끈다. 브라우저 탭 안의 요청/응답·개발자 도구에는 토큰이 남을 수 있으므로 테스트 후 지우고 탭을 닫는다.
- Swagger에는 FocusOn Access Token만 넣는다. Google Client Secret을 입력할 곳은 없다.

## 2. Google PKCE 요청 준비

PKCE Python 도구는 Spring 프로세스와 별개이므로 별도 터미널에서 아래처럼 환경변수를 로드한다. 서버의 자동 읽기는 shell 환경변수를 export하는 기능이 아니다. 서버는 계속 켜 둔다.

```bash
cd server
set -a
source .env
set +a
python3 scripts/google_pkce_request.py
```

시스템 브라우저에서 Google OAuth 테스트 사용자로 등록된 계정을 선택한다. CLI가 임시 `http://127.0.0.1:<포트>/` callback을 열고 최초 state 일치를 직접 검증한다. 서버는 최초 state를 저장하지 않으므로 state 일치 검증 책임을 이 도구가 대신한다.

인증이 끝나면 CLI는 JSON 내용 대신 **요청 파일 경로**만 알려 준다. macOS의 로컬 편집기로 파일을 열거나 `pbcopy < '<출력된 파일 경로>'`로 복사한다. 파일 권한은 0600이다. code·verifier·state를 채팅·Git·로그·스크린샷에 남기지 않는다.

이 도구는 기존 Desktop용 Client ID를 사용하고 code를 교환하지 않는다. 새 Web Client·고정 redirect URI·별도 Google Client Secret은 필요 없다. callback 포트는 실행마다 달라지며 JSON의 `redirectUri`를 바꾸면 안 된다. 실제 Desktop이 이미 소비한 code도 사용하면 안 된다.

기본 installationId는 실행마다 무작위다. QA 전용 기기를 재사용하려면 `--installation-id swagger-local-qa`로 고정할 수 있다. 실제 Desktop의 installationId는 쓰지 않는다. 같은 ID로 재로그인하면 이전 QA family가 폐기된다. 기본 인증 대기는 300초이며 `--timeout`은 1~600초다.

## 3. Swagger 실행 순서

| 순서 | 실행 | 기대 결과 |
| --- | --- | --- |
| 1 | `POST /api/v1/auth/google/exchange` → Try it out → 임시 파일 JSON을 body에 붙여 넣기 → Execute | 201, `data.accessToken`, `refreshToken`, `accessTokenExpiresAt`, user·device·onboardingStatus |
| 2 | 상단 Authorize → `accessToken` 값만 입력 (`Bearer ` 접두사 제외) → Authorize → Close | 이후 보호 API에 Bearer 자동 첨부 |
| 3 | `GET /api/v1/auth/me` 실행 | 200, 교환 응답과 같은 사용자·기기 |
| 4 | `POST /api/v1/auth/refresh`에 `{ "refreshToken": "현재 값" }` 입력 | 200, 새 토큰 3개 필드 |
| 5 | Authorize의 기존 값을 Logout으로 지우고 새 `accessToken` 입력, 새 `refreshToken`으로 메모 교체 | 회전 전 Refresh Token은 버림 |
| 6 | `POST /api/v1/auth/logout` 실행 | 204, 본문 없음; QA Desktop 슬롯 반환 |
| 7 | 같은 Access Token으로 `GET /api/v1/auth/me` 재실행 | 401 `AUTH_RELOGIN_REQUIRED` |

exchange·refresh는 Bearer 없이 실행한다. `Authorize`는 FocusOn JWT 입력 기능이며 Google 로그인 버튼이 아니다. Swagger가 기존 Authorize 값을 공개 API에도 첨부하더라도 서버는 exchange·refresh에서 그 Bearer를 인증 수단으로 사용하지 않는다.

Access JWT는 15분이며 refresh 성공 때 token·family 만료를 현재부터 30일 연장한다. 회전 전 Refresh Token을 재전송하면 해당 family가 폐기된다. 여러 탭에서 동시에 refresh하지 말고 실제 Desktop의 Refresh Token을 가져오지 않는다. 401이 Access 만료인 경우 유효 Refresh Token으로 한 번 갱신하고 새 Access Token으로 logout을 다시 실행한다.

Desktop 슬롯은 사용자당 최대 3대다. 테스트를 마치면 반드시 logout하고 임시 요청 파일을 삭제한다. 교환이 실패했거나 code가 만료·소비됐으면 기존 JSON을 반복 실행하지 말고 파일을 지운 뒤 도구로 새 인증을 시작한다. 기록·클립보드도 정리한다. 장기 보관할 것은 requestId·HTTP 상태·오류 코드 정도다.

## 4. 오류 QA

| 상황 | 예상 | 주의 |
| --- | --- | --- |
| exchange 필수값 누락 `{}` | 400 `VALIDATION_ERROR` | Google 호출 전 입력 검증 |
| 형식은 맞지만 잘못됐거나 소비한 code | 401 `AUTH_OAUTH_REAUTH_REQUIRED` | 새 OAuth를 시작 |
| Authorize를 비우고 auth/me·logout 실행 | 401 `AUTH_RELOGIN_REQUIRED` | 문서 공개와 API 인증은 별개 |
| 만료 Access Token | 401 `AUTH_ACCESS_TOKEN_EXPIRED` | 실제 오류 코드 확인, 유효 Refresh Token으로 갱신 |
| 폐기된 family의 Access Token | 401 `AUTH_RELOGIN_REQUIRED` | 재로그인 필요 |
| 이전 Refresh Token 재사용 | 401, family 폐기 | 의도적으로 폐기할 QA 세션에서만 확인 |
| 요청 한도 초과 | 429, `Retry-After` | 대량 요청으로 실계정 로그인 한도를 소모하지 않기 |

API 오류 원문 정책은 서버 ErrorCode·정본을 따른다. 로그에 토큰이나 JSON을 출력하는 방식으로 원인을 조사하지 않는다.

## 5. 검증 경계와 참고

오프라인 CLI 검증과 합성 Google 응답으로 하는 서버 테스트는 Google 실계정 OAuth 성공을 증명하지 않는다. 실제 사용자 인증·Google 외부 통신을 포함한 201→200→200→204는 위 순서로 별도 실행한다. Desktop callback·IPC·Keychain·화면 흐름 검증도 별개다.

- 자동 검증: `python3 -m unittest discover -s server/scripts -p 'test_*.py' -v`
- 서버 검증: `./server/gradlew -p server build --offline --no-daemon`
- [springdoc 설정](https://springdoc.org/properties)
- [Google 네이티브 앱 OAuth](https://developers.google.com/identity/protocols/oauth2/native-app)
- [계획](features/swagger-testing-PLAN.md), [독립 리뷰](features/swagger-testing-code-review.md), [QA](features/swagger-testing-QA.md)


## Extension 연결 (Issue #58 브랜치)

- 새 별도 키 PAIRING_SECRET_BASE64를 .env.example 형식으로 설정한다. ignored local .env에 이미 생성된 환경은 기존 bootRun 명령을 유지한다. JWT 키를 복사해 사용하지 않는다. 배포/재시작 간 키를 유지한다.
- Desktop으로 로그인·Authorize 후 POST /api/v1/pairing-codes를 실행한다. 공개 POST /api/v1/extension-pairings에 표시 코드와 Extension installationId를 보내고 새 token으로 me/refresh를 확인한다.
- DELETE /api/v1/pairing-codes/{pairingCodeId}는 발급 Desktop으로 호출한다. 이미 소비된 코드를 취소해도 연결은 해제되지 않는다.
- 설치별3회 오입력은3분 잠금이다. 세부 검증·Frontend 전달은 features/extension-pairing-QA.md와 frontend-handoff.md를 따른다.
- 기존 실행 중 서버는 재빌드 전 코드를 계속 실행한다. 브랜치 변경 후 새 API를 확인하려면 해당 코드로 서버를 다시 실행해야 한다.

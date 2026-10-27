# Swagger 테스트 설정 QA

- 일자: 2026-10-27 (Asia/Seoul)
- Issue: [#79](https://github.com/What-a-move/FocusOn/issues/79)
- 대상: `chore/#79-swagger-testing`, 기준 `origin/dev 65da4f0`
- 범위: `server/` 설정·문서·도구. 제품 API payload·DB schema·Frontend 변경 없음.

## 결과

| 검증 | 결과 | 의미 |
| --- | --- | --- |
| Gradle build·Checkstyle main/test | 통과 | Java 25, 기존 의존성 3.1.0 그대로 |
| Backend JUnit | 62개 통과, 실패/오류/skip 0 | 기존 58개 + Swagger 4개; Google 성공은 mock |
| Python PKCE 도구 | 10개 통과 | 외부 Google 호출 없이 실제 loopback 포함 |
| PostgreSQL·실제 HTTP | 32개 확인 통과 | 실제 jar, 새 임시 DB, 활성·비활성 3가지 실행 구성 |
| 독립 리뷰 | 최초 Low 1건 수정·재검토 완료 | 미해결 지적 없음 |
| Google 실계정 OAuth 성공 | 미실행 | 사용자 본인 인증이 필요함 |
| PR CI·CodeRabbit | 미실행 | PR 생성 전, 별도 승인 대상 |

JUnit 62개에는 합성 Google 응답을 사용하는 exchange201 → me200 → refresh200 → logout204 → 폐기 JWT401 흐름이 포함된다. 테스트 수를 운영 로그인 성공률로 해석하지 않는다.

## 실제 HTTP 32개

- 기본 `local`: Swagger HTML·UI·OpenAPI JSON·UI 설정·YAML 5개 경로 모두401.
- `local,swagger`: UI 진입·HTML·JSON·YAML·UI 설정 성공. 구현된 인증 경로 4개만 문서화.
- Bearer scheme·전역 요구·exchange/refresh 빈 security·me/logout 상속·principal 비노출 확인.
- persistAuthorization=false·외부 validator 없음 확인.
- 문서 POST 차단, me/logout 무토큰401, 잘못된 토큰401, exchange `{}`400, refresh `{}`401.
- 별도 QA DB에 합성 사용자·기기·family·해시 Refresh Token을 준비했다. 테스트 프로세스 메모리에서 서명한 JWT로 me200 → 실제 DB refresh200 → 새 Access Token me200 → logout204(빈 본문) → 같은 JWT me401을 확인했다. 제품에 테스트 토큰 발급 API를 추가하지 않았다.
- `local,swagger`에 api-docs/UI 명시적 false를 덮어쓴 구성에서 UI·JSON401 확인.
- Flyway V1/V2·Hibernate validate로 기동했다. 끝나면 QA 서버를 종료하고 이번 실행이 만든 임시 DB를 삭제했다. 기존 DB를 정리하거나 변경하지 않았다.

MockMvc에서 문서 POST는403이다. 실제 servlet 컨테이너에서는 오류 디스패치가 기존 기본 거절 체인을 거쳐401이 된다. 두 경우 모두 문서 응답을 공개하지 않는지를 확인했고, 제품 API 오류 계약은 변경하지 않았다.

## PKCE 도구 10개

S256 일치·길이·난수, state 일치/불일치·중복 parameter·경로·코드 길이·취소, 실제 callback의 잘못된 Host/state 후 정상 복귀, 동일 redirect URI 보존, code 미교환·원문 비출력, 브라우저 실패·타임아웃, 0600 파일·실패 시 삭제, CLI 누락 Client ID, 입력 공백 정규화·UTF-16 길이 경계를 확인했다.

처음 샌드박스 실행에서는 loopback bind/Gradle 캐시 권한이 막혔다. 필요한 로컬 권한으로 재실행해 검증했다. Docker가 종료돼 있어 다시 실행한 뒤 PostgreSQL을 사용했다. 이를 코드 결함이나 실계정 로그인 실패로 기록하지 않는다.

## 재실행

```bash
./server/gradlew -p server build --offline --no-daemon
python3 -m unittest discover -s server/scripts -p 'test_*.py' -v
```

실계정 수동 검증은 [SWAGGER_TESTING.md](../SWAGGER_TESTING.md)를 따른다. 별도 installationId로 진행하고 refresh 순차 실행·logout·임시 JSON 삭제를 지킨다. 테스트 로그·요청 파일에 인증 원문을 보관하지 않는다.

## 남은 제한

- 실제 Google 테스트 사용자 동의·외부 token endpoint·키 검증을 포함한 성공 왕복은 미실행이다.
- Desktop callback·IPC·Keychain·UI, 원격 배포·proxy 환경은 이 설정 QA의 범위가 아니다.
- Python 검증은 현재 GitHub Backend CI에서 자동 실행되지 않는다. 위 로컬 명령으로 실행했다. Java 검증은 기존 Gradle build에 포함된다.

## 추가 QA: local .env 자동 읽기 (2026-10-27)

- 실제 SpringApplication을 사용한 격리된 합성 파일 검사 7개 통과: server 작업 디렉터리, 저장소 루트, server 파일 우선순위, OS 환경변수 우선순위와 포트 override, optional 파일 누락, 파일 없이 OS만 제공, docker에서 local 파일 미로딩.
- 실제 `bootRun` 검사 6개 통과: DB/JWT/Google 자격 증명을 시작 환경변수에서 제거한 사실 확인, source·프로필 인자 없이 DB readiness200, JWT 서비스 기동 및 me401 인증 유지, 기본 local 선택, 이번 임시 DB에 Flyway2개 적용, jar에 .env 미포함. 기존 DB 대신 새 QA DB를 사용했고 Gradle/서버 프로세스와 임시 DB를 정리했다.
- 변경 뒤 build·Checkstyle 통과, JUnit62개 실패/오류/skip0. PKCE 도구 코드는 변경하지 않아 기존10개를 불필요하게 재실행하지 않았다.
- 독립 리뷰는 별도 합성 환경에서 경로·우선순위·포트·docker/test 제외도 확인했다. 실행 인자·OS 환경변수는 파일보다 우선한다.
- 실제 .env는 기존 자격 증명을 현재 작업 checkout에 0600으로 준비했으며 원래 checkout 값은 변경하지 않았다. Git ignore·Docker build context 제외·jar 미포함을 확인했다. 비밀값을 테스트 로그·문서에 기록하지 않았다.
- 로컬 파일 자동 읽기는 Spring 내부 설정 소스로 추가하는 기능이다. Python PKCE CLI 등 별도 프로세스에 shell 환경변수를 export하지는 않는다.

# Google 로그인 QA

- 날짜: 2026-10-25
- Issue: #49
- 환경: Java 25 / Spring Boot 4.1.1 / H2 / PostgreSQL 17.11
- 범위: 승인된 Google 교환·auth/me·공통 인증·초기 토큰 발급

## 실행 결과

| 검증 | 결과 |
| --- | --- |
| Backend clean build·Checkstyle·회귀 | 전체 29개 테스트 통과(failure/error 0), Checkstyle 통과. 합성 Google 키·모의 Google HTTP 사용 |
| PostgreSQL 17.11 | 별도 임시 QA DB에 Flyway V1·V2 적용, Hibernate validate, 로그인·저장 모델 테스트 통과 |
| 동일 Google 계정 동시 최초 로그인 | 사용자 1명으로 수렴, Desktop 3대 성공·4번째 제한 |
| 동일 설치 재로그인 | deviceId 유지·새 family와 토큰 발급·이전 토큰 요청 거절 |
| auth/me | 검증된 사용자·기기만 200. 다른 X-Device-Id·Bearer 누락은 공통 401 |
| Google ID token | 합성 RSA 서명·issuer·audience·만료·sub 검사 |
| Google 교환 | 고정 token endpoint·POST form·code·PKCE·redirect 원문 전달 검사 |
| 만료/위조 | 서명된 만료만 만료 예외, 위조된 만료 payload는 일반 인증 실패 |
| Google JWK 장애 | 모의 네트워크 오류 503·timeout504 구분 |
| 한도 | 키별 한도·Retry-After 양수·익명 공간 포화가 인증 공간에 영향 없음 |
| 민감값 | DTO·토큰 응답의 toString 원문 숨김, refresh·installation ID는 해시 저장 |

## PostgreSQL 재현 방식

기존 개발 DB를 건드리지 않고 `focuson_auth_qa_20261025` 임시 DB를 생성했다. 환경변수로 datasource·driver를 PostgreSQL로 지정하고 Flyway=true, ddl-auto=validate로 로그인·저장 모델 테스트를 실행했다. migration 이력은 1:true, 2:true였으며 해당 QA DB만 종료 후 제거했다. 계정 값·비밀번호·token은 출력하지 않았다.

## 미검증·제한

- 실제 사용자 Google 로그인 → Desktop callback → 서버 → Keychain 왕복은 미실행.
- refresh·logout·Extension pairing·기기 관리 API는 이번 구현 범위 밖.
- HTTP 테스트는 MockMvc로 Controller·필터·저장 모델을 연결했으며 실제 외부 Google만 대체했다. 합성 키 검증 통과를 실제 Google 계정 검증 완료로 표현하지 않는다.
- 초기 QA 시점에는 PR이 없었다. 현재 PR #51이 생성됐으며 원격 CI 결과는 GitHub에서 별도 확인한다.
- 새 Google client·key 초기 다운로드의 실제 네트워크 지연·부하 성능은 측정하지 않았다.

## 2차 적합성 검사

- 현재 Notion Google exchange·auth/me의 필드·응답·헤더·권한을 다시 읽고 Desktop auth-api 파서와 대조했다.
- 최신 origin/dev를 fetch했으며 작업 시작 이후 새 dev 변경은 없었다.
- 독립 재검토에서 새 actionable P1/P2가 보고되지 않았다.
- 테스트 2개를 추가해 정상 서명의 다른 사용자 ID, 폐기된 Desktop, Access Token 유효기간 안의 ACTIVE family 만료 시각을 재현했다. 모두 auth/me에서 401 AUTH_RELOGIN_REQUIRED로 거절했다.
- 위 추가 사례를 별도 PostgreSQL QA DB에서도 실행하고 V1·V2 성공을 재확인했다. 기존 개발 DB와 migration 파일은 변경하지 않았다.
- 실행 코드의 추가 수정은 필요하지 않았으며 실제 Google 사용자 왕복과 refresh·logout 등 후속 범위는 미검증 상태를 유지한다.

## CodeRabbit 수정 후 회귀

- 최신 origin/dev `bdd267d`(PR #50)를 작업 브랜치에 병합했다. Backend 변경 충돌 없음. 사용자 staged IDE 파일은 별도 보존·복원했다.
- clean build·Checkstyle·전체 32개 테스트 통과(failure/error 0).
- IP별 상한 선행 적용으로 installationId를 바꾼 11,000회 시도를 제한하고 다른 IP·인증 API가 영향받지 않음을 확인했다. 기존 복합 키 5회 제한과 서비스 호출 전 차단도 확인했다.
- 이번 변경은 Controller·메모리 한도·문서이며 SQL 변경 없음. PostgreSQL 검증 결과는 앞 절의 기존 검증이며 이번에 재실행하지 않았다.

## azp 검증 보강 후 QA

- clean build·Checkstyle·전체 38개 테스트 통과(failure/error 0).
- 정상 단일 aud+azp 생략은 기존 테스트로 유지하고, 단일/다중 aud+일치 azp 허용을 검증했다.
- 다른 Client의 azp, 다중 aud+azp 누락, 빈 문자열·공백·숫자·배열 azp를 서명된 합성 토큰으로 거절했다.
- azp가 일치해도 aud가 다른 Client면 거절함을 확인했다. aud 누락도 azp 유무와 관계없이 인증 실패로 반환하며 예외로 500이 되지 않음을 확인했다.
- 외부 실제 Google 계정 왕복과 PostgreSQL 재실행은 이번 claim 검증 보강에서 수행하지 않았다.

## 사용자 요청 재실행 QA (2026-10-25)

- 최신 dev `49eacce`(PR #53)를 작업 브랜치에 통합했다. Extension CONTEXT 충돌은 refresh·팝업 구현 완료를 반영한 최신 dev 문서로 해결했다.
- PostgreSQL 17.11의 별도 QA DB에서 LoginApiTests·AuthPersistenceRepositoryTests를 재실행했고 성공했다. Flyway 이력 V1/V2 모두 true, Hibernate validate 통과. 종료 후 해당 임시 DB만 제거했다.
- 실제 실행 jar와 별도 PostgreSQL DB·127.0.0.1:18080 서버로 HTTP QA 10건을 수행했고 모두 통과했다.

| 실제 HTTP/환경 QA | 결과 |
| --- | --- |
| readiness 및 DB 연결 | 200 UP |
| Desktop/Backend Google Client ID | 일치 (원문 출력 안 함) |
| auth/me Bearer 누락·위조 | 401 AUTH_RELOGIN_REQUIRED + requestId |
| 비공개 actuator 접근 | 401 |
| 잘못된 JSON | 400 VALIDATION_ERROR |
| loopback 이외 redirectUri | 400 VALIDATION_ERROR |
| 실제 Google token endpoint에 무효 code 교환 | 401 AUTH_OAUTH_REAUTH_REQUIRED |
| 실패 후 users/token_families | 생성된 행 없음 |
| Flyway V1/V2 | 모두 성공 |

- 실제 Google token endpoint 통신은 모의 대체 없이 수행했다. 성공하는 실계정 ID token·Desktop callback·Keychain 왕복은 이 검증에 포함하지 않는다.
- QA 서버 종료 및 임시 DB 제거 완료. 기존 개발 DB·로컬 인증 파일·사용자 staged IDE 파일을 보존했다.
- 현재 체크아웃에는 Desktop node_modules·컴파일 산출물이 없어 Desktop 실행 QA는 수행하지 않았다. 실계정 Google 로그인은 본인 인증이 필요해 사용자에게 진행 여부를 요청했다.

- 최신 dev 통합·PostgreSQL 실행 후 전체 clean build를 다시 실행했다. 최종 38개 테스트 failure/error 0, Checkstyle·build 통과. 원격 CI는 연결 도구에서 실행 목록을 확인하지 못해 통과로 표시하지 않는다.

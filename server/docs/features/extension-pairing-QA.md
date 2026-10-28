# Extension 연결 QA

- Issue #58 / Branch feat/#58-extension-pairing / 2026-10-28
- 기준 dev b293bf1. 사용자 개발 DB와 기존8080 서버를 유지하고, 별도 QA DB·8081 서버를 사용했다.

| 검증 | 결과 |
| --- | --- |
| 전체 build·Checkstyle | 통과 |
| 최신 dev 통합 후 전체 JUnit (H2) | 83 / 실패0 / 생략0 |
| 최신 dev 통합 후 실제 PostgreSQL pairing 통합 | 15 / 실패0 |
| Flyway·Hibernate | 신규 격리 DB에서 사용자 설정 V3·연결 V4 포함 V1~V4 적용 및 validate 통과 |
| 실제 HTTP (8081, 이전 dev 기준) | 33 / 실패0. 최신 통합 후 별도 HTTP 재검증 전 |
| 실제 DB 제약 거절 (이전 dev 기준) | 6 / 실패0 |
| 독립 리뷰 | 초기 P2 및 최신 dev 통합·24시간 정리 재리뷰 통과, 문서 경로 지적 수정 |

## 확인한 흐름

- INCOMPLETE Desktop 발급201, 원문 대신 별도 HMAC 저장, 발급당5분·최대3회 입력.
- 공개 claim은 잘못된 Bearer를 무시하고 코드로 인증, Extension201·문자열 ID·UTC token 만료 응답.
- 코드 형식/기소비/만료/취소 오류, 설치별 실패1/2/3회 commit·remainingAttempts2/1/0.
- 잠금180초 동안 유효 코드도 거절, 179초에는1초 대기, 180초 경계 성공.
- 성공 reset, 미완료1~2회는 마지막 실패 후24시간까지 보존하고 경계에서 초기화한다. 재실패 시 시각을 갱신하며 cleanup도 만료 행을 삭제한다. terminal code는 terminal_at 기준30일 삭제.
- 같은 설치·같은 사용자 재연결은 deviceId 재사용·이전 family/refresh 폐기. 새 family는 이전 refresh 재사용 폐기에 영향받지 않음.
- 다른 계정 활성 설치409 LINK_007, 충돌 코드 미소비.
- 취소204 멱등·타 소유 no-op·기소비 코드 취소 시 token 유지.
- 같은 코드 동시 입력1승자, 다른 계정의 같은 설치 동시 연결1승자. cancel/claim·logout/claim 경쟁에서 상태 부활 없음.
- 재발급·logout·재로그인 시 이전 코드 취소. 이미 만료된 경우 EXPIRED·expires_at 기준 유지.
- Extension refresh·me 성공 및 Desktop logout 뒤 LINK_TOKEN_REVOKED. Extension 발급/취소 요청401.
- 발급4번째429·Retry-After. OpenAPI 공개 claim security 배열 비어 있음.
- DB6개: parent/issuer/consumer 소유권 FK, 실패 상태 CHECK, terminal null CHECK, 활성 설치 UNIQUE의 잘못된 저장 거절.

## 실행 방법과 한계

- 기본: JAVA_HOME=JDK25 ./server/gradlew -p server build --offline --no-daemon.
- PostgreSQL 검증은 test 프로필의 datasource를 **격리 PG DB**로 대체하고 ddl-auto=validate, flyway=true로 PairingApiTests를 실행했다. H2 결과만으로 SQL 정합성을 주장하지 않는다.
- HTTP 검증은 격리 DB의 합성 Desktop 인증 fixture를 사용했다. 이번33개를 Google 실계정 OAuth 또는 Desktop·Extension UI 연동 성공으로 표현하지 않는다. 기존 Google 실계정 Swagger 왕복은 선행 작업에서 별도로 완료했다.
- 이번 변경의 원격 CI·CodeRabbit·PR은 아직 없다. Frontend 코드는 수정하지 않았다. 취소 UI·기존 installation 충돌 안내는 handoff 후 통합 QA가 필요하다.
- 단일 서버 유한 메모리 rate limiter다. 다중 인스턴스 공유 한도·HMAC 키 교체는 별도 설계가 필요하다.
- 수치는 통제된 기능 검증 건수이며 운영 성공률·성능 지표가 아니다.

## 프론트 필드 대조 후 재검증 (2026-10-28)

- `origin/dev` Frontend의 Desktop 코드 파서와 Extension 연결 파서·입력 요청을 대조했다.
- Backend HTTP 테스트로 codeDisplay·expiresAt·pairingCodeId, 하이픈 없는 pairingCode·installationId, accessToken·refreshToken·accessTokenExpiresAt·device를 확인하고, 소비 뒤 코드 취소가 연결을 유지하는지 검사했다.
- 기존 작업 브랜치에서 전체 build·Checkstyle 및 JUnit 84개 통과. 이 테스트는 H2·MockMvc이며 실제 앱·Chrome·실계정 통합 검증은 아니다.
- 최신 origin/dev 6c93515의 협업자 V4와 우리 V4가 충돌해 아직 dev를 통합하지 않았다. 따라서 이번 빌드는 최신 dev와 결합된 전체 빌드를 뜻하지 않는다.


## 협업자 PR #84 통합 검증 (2026-10-28)

- 최신 dev 6c93515를 작업 브랜치에 통합했고 사용자 설정 V4는 유지, 연결 migration은 V5로 조정했다.
- build·Checkstyle·전체 H2 JUnit 87개 성공. H2는 Flyway를 사용하지 않아 PostgreSQL의 nickname partial UNIQUE 및 제약명 매핑을 생성하지 않는다. 동시 닉네임 분기는 실제 PostgreSQL 검증으로 한정하고 동의·집중 설정 동시성은 H2에서도 유지한다.
- 별도 PostgreSQL DB에 V1~V5 적용·Hibernate validate 및 pairing 16개와 사용자 설정 API 7개 테스트 통과. PostgreSQL 동시 요청 시작은 확인했으나 두 사전 검사 시점이 겹쳤는지는 계측하지 않았다.
- 실제 Desktop·Extension 통합 UI QA 및 원격 PR CI는 아직 미실행이다. 이전의 V4 기반 QA 결과는 당시 기준의 이력이다.

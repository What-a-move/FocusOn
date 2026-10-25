# Refresh Token 회전 및 Desktop 로그아웃 기획서

> 상태: 2026-10-26 사용자 승인 완료. Issue #59 구현·로컬 QA 완료, PR·원격 리뷰 진행.
## 기본 정보
- 기능명/ID: 로그인 수명주기 / auth-lifecycle
- 작성일: 2026-10-26
- 우선순위: MVP 핵심 / Server
- 관련 Issue: #59
- Branch: feat/#59-auth-lifecycle
- 선행: #49 / PR #51 dev 병합 완료
- 다음: #58 Extension pairing (기획 대기). 기기 목록/선택 폐기는 별도 API 단위 작업.
- 관련 Client: Desktop auth-api.cts·auth-service.cts, Extension features/session/api-client.ts

## 기능 목적
Google 로그인 이후 Access Token 만료·앱 재실행 시 로그인 유지, Refresh Token rotation과 재사용 탐지, 현재 Desktop 로그아웃을 제공한다. 오류 때 세션을 되살리거나 다른 Desktop을 로그아웃시키지 않는다.

## API 정의
### POST /api/v1/auth/refresh
- Bearer 없이 JSON refreshToken으로 인증한다. 만료된 Bearer를 추가해도 refresh 인증을 Bearer로 대체하지 않는다.
- Request: {"refreshToken":"opaque token"}
- 200 {"data":{"accessToken":"...","refreshToken":"...","accessTokenExpiresAt":"UTC ISO 8601"}}
- 누락·빈 값·만료·미확인 token: 401 AUTH_RELOGIN_REQUIRED. 지나치게 긴 입력은 공통 400 검증 오류.
- ROTATED/REVOKED token 재사용: 401 AUTH_TOKEN_REUSE_DETECTED, 해당 family 전체 폐기.
- 발급 사용자·기기·family 및 Extension의 연결 Desktop 소유/로그인 상태를 검증한다.
- 2026-10-26 사용자 확정: 갱신 성공 시 새 Refresh Token과 family 만료를 현재부터 30일로 연장한다. 30일간 유효한 갱신이 없으면 재로그인한다.
- 승인된 방어 한도: 익명 refresh IP 300회/10분과 유효 token의 사용자+기기 60회/분을 적용한다. 무작위 token으로 인증 사용자 키 공간을 소진하지 못하도록 공개 refresh 공간을 분리한다. proxy 전에는 remoteAddr만 신뢰한다.
### POST /api/v1/auth/logout
- 활성 Desktop Bearer 인증, body 없음. 현재 principal에서 대상을 정한다.
- 성공 204 빈 본문.
- 현재 Desktop family와 그 Desktop에 연결된 Extension family·활성 Refresh Token 폐기. 해당 기기 상태도 LOGGED_OUT/REVOKED로 갱신한다.
- 다른 Desktop 및 그 Extension은 유지한다. Extension 직접 호출은 허용하지 않는다.
- 이미 만료/폐기된 Bearer는 기존 공통 401이다. Desktop은 만료 Access Token을 refresh한 뒤 logout을 호출하는 기존 구현을 유지한다.

## 데이터 설계
- 기존 users/user_devices/token_families/refresh_token_records 및 V1/V2 재사용. 새 컬럼 필요 여부를 검토하되 기존 migration은 수정하지 않는다.
- 새 token 원문은 응답에서만 전달하고 SHA-256 hash만 저장한다.
- 새 record.parentToken은 이전 record, 이전 token은 ROTATED·usedAt 기록. family당 ACTIVE Refresh Token 1개 제약 유지.
- 수명은 서버 UTC 기준이며 family/record expiresAt을 같은 30일 시각으로 맞춘다.
- ROTATED·REVOKED 과거 record는 reuse 탐지를 위해 보존한다. cleanup은 별도 보관 정책 작업.

## 처리 흐름과 동시성
1. token hash로 최소 소유 문맥만 조회한다.
2. 사용자 행 → family 행 순서로 잠금을 획득하고 token을 다시 읽는다. Google 재로그인·logout과 동일 사용자 잠금 순서로 직렬화한다.
3. 사용 가능성 검증 후 기존 ACTIVE token을 먼저 flush해서 ROTATED로 바꾼다.
4. 새 ACTIVE record·JWT·family expiry를 같은 transaction에서 확정한다.
5. 재사용 탐지에 따른 폐기는 commit한 뒤 401을 반환한다. 예외 rollback으로 폐기가 취소되지 않게 결과와 오류 반환을 분리한다.
6. 동시 같은 token 2회는 1회만 rotation 성공하고 재사용 요청으로 family가 폐기되는 엄격한 정책이다. Client single-flight를 유지하며 네트워크 재전송 grace는 임의로 추가하지 않는다.

## 예외 처리
| 상황 | HTTP/code | 처리 |
| --- | --- | --- |
| 누락/알 수 없음/만료 | 401 AUTH_RELOGIN_REQUIRED | Google 재인증 코드와 구분 |
| 회전/폐기 token 재사용 | 401 AUTH_TOKEN_REUSE_DETECTED | 해당 family만 폐기, commit 검증 |
| 잘못된 JSON/과도한 입력 | 400 VALIDATION_ERROR | 비밀값 비노출 |
| 한도 초과 | 429 TOO_MANY_REQUESTS | 양수 Retry-After |
| DB 장애 | 공통 오류 정책 | 원문 토큰/예외 비노출 |

## API 설계 원칙
정본의 두 endpoint·응답·error envelope 유지, rotation과 logout 사용 사례 분리, 사용자·기기 소유권과 transaction 경계 검증, 제한/비밀값 비저장, API/DB 역할 분리. UI·AI·pairing API는 추가하지 않는다.

## QA 계획
- HTTP 회전 200, 누락/미확인/만료 401, 재사용 401, 기존 Access JWT 만료 후 refresh→me 복구.
- 동시 회전·회전 대 재로그인·회전 대 logout 경쟁.
- 재사용 폐기 commit, 다른 family 유지, 이전 Access Token이 폐기 후 거절되는지 검증.
- Desktop logout 후 연결 Extension 폐기·다른 Desktop 유지·Desktop 3대 슬롯 해제.
- Extension refresh 정상/잘못된 부모/부모 family 만료 및 미인증 logout 거절.
- 전체 회귀·Checkstyle·PostgreSQL V1/V2 검증, Desktop parser/서비스 계약 테스트.
- 실제 jar HTTP QA, 실제 Google 계정은 본인 인증 후 Desktop callback/Keychain 성공 흐름을 별도 확인한다.
- 구현 대화가 없는 독립 리뷰 후 QA 기록, PR 및 CodeRabbit 재검토 요청.
- PR merge·이슈 종료는 별도 승인받는다.

## 완료 조건
- [x] PLAN 사용자 승인 및 정본·결정 기록 반영
- [x] refresh/logout·한도·동시성·소유권 구현
- [x] 독립 리뷰·자동/실제 PostgreSQL·HTTP QA 통과
- [x] 실계정 QA 수행 범위와 미검증 구분
- [x] CONTEXT/NEXT_TASK/REPORT/QA/ERROR 기록
- [x] PR 생성 및 CodeRabbit 요청·피드백 반영

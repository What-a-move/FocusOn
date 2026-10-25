# 로그인 수명주기 QA

- 날짜: 2026-10-26 / Issue #59
- 환경: Java 25·Spring Boot 4.1.1, H2 및 PostgreSQL 17.11, macOS Node 24·기존 Desktop 인증 모듈.

## 실행 결과

| 범위 | 결과 |
| --- | --- |
| Backend 전체 | 55개 테스트 실패/오류 0, clean build·Checkstyle 통과 |
| PostgreSQL | 전용 QA DB의 LoginApiTests·TokenLifecycleApiTests·저장 모델 테스트 25개 통과. V1/V2 성공·Hibernate validate |
| 실제 jar HTTP + Desktop Keychain | 23건 통과. 운영/개발 DB와 별개인 임시 DB 및 무작위 loopback port 사용 |
| 기존 Desktop 인증 | PKCE·callback·state·IPC·복구·logout 22개 테스트 통과 |
| 독립 리뷰 | 구현 대화 없는 별도 에이전트가 diff·PLAN·계약·규칙 대조, 추가 actionable P1/P2 없음 |

## 주요 검증

- refresh는 만료/위조 Bearer가 함께 있어도 body token으로 처리한다. 성공의 token 3개 필드와 UTC 만료 시각은 Desktop 파서로 검증했다.
- token/family 30일 연장·parent 이력·ROTATED 및 ACTIVE 1개 제약, genuine expired Access JWT → refresh → me 복구.
- 같은 token 동시 요청: 1회 회전 성공 뒤 reuse 폐기. 401 반환 후 별도 transaction/HTTP 요청으로 폐기 commit을 확인했다.
- refresh 대 logout·같은 설치 재로그인 경쟁: 새 로그인 family를 이전 token으로 폐기하지 않으며 logout 뒤 활성 token이 남지 않는다.
- logout은 현재 Desktop과 연결 Extension만 폐기하고 다른 Desktop·그 Extension은 유지한다. 기기 3대 슬롯도 해제된다.
- Extension 단독 refresh 허용, 직접 Desktop logout 거절, 부모 Desktop 없음/만료 시 refresh 및 Access 인증 거절.
- 누락·공백·미확인·만료·삭제 사용자·폐기 기기·형식 오류·한도·Retry-After·requestId 검증.
- 공개 refresh 키 공간 포화가 Google 로그인·인증 API 한도 공간을 소진하지 않는다.

## 실제 HTTP·Keychain 재현 범위

합성 사용자와 기기를 **전용 QA DB에만** 생성하고 현재 서명 키로 테스트 Access JWT를 메모리에서 만들었다. token 원문은 DB·로그에 기록하지 않았다. 실제 jar와 PostgreSQL에 HTTP로 refresh/reuse/logout을 호출했다.

기존 Desktop의 AuthApiClient·AuthService·AuthSessionStore·MacKeychainRefreshTokenVault를 수정 없이 사용했다. 무작위 QA origin의 Keychain 항목이 비어 있는 것을 확인한 뒤 합성 Refresh Token 저장·읽기, restore의 실제 refresh/me, 새 회전 token 저장, token 없는 이벤트, logout·Keychain 삭제를 검증했다. 테스트 종료 후 QA 서버·DB·Keychain 항목을 정리했다.

## 제한

- 실제 Google 계정 로그인·동의 완료는 사용자 본인 인증이 필요해 진행 여부를 요청했다. 위 합성 세션 성공을 Google 실계정 성공으로 표시하지 않는다.
- 전체 Desktop build:electron은 새 Swift observer의 로컬 compiler/SDK 불일치로 실패했다. TypeScript·preload 컴파일 및 인증 테스트·Keychain 연동은 별도로 성공했다. 전체 UI/native 빌드 성공을 뜻하지 않는다.
- 분산 서버 한도·실제 배포 proxy·부하/침투 테스트는 미실행이다.
- PR #60 Backend CI #45 성공. CodeRabbit 전체 리뷰는 dd93475 기준 actionable comment 0건·status success.
- CodeRabbit의 주석 커버리지 경고에 따라 테스트 검증 의도·fixture·projection·DTO 마스킹 설명을 추가하고 refresh 한도 요약표를 보완했다. 실행 로직·계약 변경은 없다. 커버리지 비율 재산정 결과는 확인하지 않았다.

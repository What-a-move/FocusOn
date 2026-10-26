# 로그인 수명주기 QA

- 날짜: 2026-10-26 / Issue #59
- 환경: Java 25·Spring Boot 4.1.1, H2 및 PostgreSQL 17.11, macOS Node 24·기존 Desktop 인증 모듈.

## 실행 결과

| 범위 | 결과 |
| --- | --- |
| Backend 전체 | 55개 테스트 실패/오류 0, clean build·Checkstyle 통과 |
| PostgreSQL | 전용 QA DB의 LoginApiTests·TokenLifecycleApiTests·저장 모델 테스트 25개 통과. V1/V2 성공·Hibernate validate |
| 실제 jar HTTP + Desktop Keychain | 33건 통과. 운영/개발 DB와 별개인 임시 DB 및 무작위 loopback port 사용 |
| Desktop 전체 Electron | 89개 테스트 통과. 기존 인증 22개 외 logout 경계 회귀 7개를 추가했고 세션·관측·IPC 등 전체 회귀도 실행 |
| 최신 Extension | 22개 테스트·전체 build 통과. 실제 pairing 서버 연동 완료를 의미하지 않음 |
| Desktop 전체 build | 중복 Swift modulemap만 제외한 임시 VFS QA 환경에서 TS·preload·Swift·Next static export 통과. 일반 환경의 도구 복구는 별도 |
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

- 실제 Google 계정 로그인·동의 완료는 사용자 본인 인증이 필요하다. 최종 재검증 때 Mac이 잠겨 있어 실제 앱 화면·계정 인증을 진행하지 못했다. 위 합성 세션 성공을 Google 실계정 성공으로 표시하지 않는다.
- 일반 Desktop build:electron은 2023년의 swift/module.modulemap과 2024년 bridging.modulemap이 SwiftBridging을 중복 정의해 실패한다. 같은 Swift·SDK에서 옛 파일만 숨긴 임시 VFS 보기와 전용 캐시로 전체 pnpm build가 성공했다. 시스템 파일·SDK·컴파일러·저장소 빌드 스크립트를 변경하지 않았으며, 기본 명령의 환경 문제 해결이나 실제 UI 실행 성공으로 표현하지 않는다. 관리자 암호 없이 설치 복구할 수 없고 softwareupdate에는 CLT 업데이트가 없었다.
- 분산 서버 한도·실제 배포 proxy·부하/침투 테스트는 미실행이다.
- PR #60 Backend CI #45 성공. CodeRabbit 전체 리뷰는 dd93475 기준 actionable comment 0건·status success.
- CodeRabbit의 주석 커버리지 경고에 따라 테스트 검증 의도·fixture·projection·DTO 마스킹 설명을 추가하고 refresh 한도 요약표를 보완했다. 실행 로직·계약 변경은 없다. 커버리지 비율 재산정 결과는 확인하지 않았다.

## 최종 재검증과 수정

- 최신 dev 905c06c를 통합하고 새 lockfile로 의존성을 맞췄다. 기존 사용자 파일과 미커밋 분석 계약은 보존했다.
- 독립 리뷰에서 Desktop logout의 모든 401 성공 처리 P2를 발견했다. 오류 코드 전달·만료 여유·refresh 뒤 한 번 재시도·명시적 종료 시에만 로컬 정리하도록 수정했다. 독립 재검토에서 기존 P2 해결 및 새 P1/P2 없음.
- 새 회귀 7개: API의 만료 401 전달, 실제 서비스/파서의 refresh→logout 재시도, 갱신 실패 보존, 두 번째 만료 반복 방지, 알 수 없는 401 보존, 이미 폐기된 로그인 정리.
- 실제 jar/PG/Keychain QA는 합성 인증 문맥에 진짜 서명된 만료 JWT를 넣고 클라이언트 만료 인식을 늦춰 경쟁을 재현했다. 서버 logout401→refresh200→logout204를 직접 확인하고 Desktop·연결 Extension의 LOGOUT 폐기 commit·활성 슬롯 반환을 별도 DB 조회했다.
- 최초 Google 인증 성공은 합성 문맥과 별개다. 계정 로그인·동의, 실제 잠금 해제된 앱 UI, 배포 서명/패키징은 남아 있다.

- 실제 Google token endpoint에 합성 잘못된 code를 보내 AUTH_OAUTH_REAUTH_REQUIRED 401과 사용자·OAuth identity 미생성을 확인했다. 이는 실제 계정의 성공 인증과 별개다.
- 로컬 Backend/Desktop의 Client ID 설정이 모두 존재하고 일치하며 Desktop API origin도 설정돼 있음을 비밀값 출력 없이 확인했다. Desktop 실행 셸에서 .env.local을 source해야 하는 기존 실행 규칙은 유지한다.
- 수정된 Desktop auth-api/auth-service/회귀 테스트 ESLint 검사 통과. 최초 pnpm wrapper의 서명 조회 네트워크 실패와 별도로 설치된 ESLint를 직접 실행했다.

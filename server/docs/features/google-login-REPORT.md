# Google 로그인 교환·인증 사용자 조회 결과

## 기본 정보

- 기능 ID: google-login
- 작성일: 2026-10-25
- Issue: #49
- Branch: feat/#49-google-login
- 기획서: google-login-PLAN.md
- PR: 아직 생성하지 않음

## 구현 결과

- POST /api/v1/auth/google/exchange: PKCE·loopback 입력 검증, 고정 Google token endpoint 교환, Google 공개 키·issuer·audience·만료·sub 검증, 사용자·Desktop·family·초기 토큰 발급.
- GET /api/v1/auth/me: 검증된 현재 사용자·기기·온보딩 상태 반환.
- 요청 인증: JWT 서명·필수 클레임과 DB 사용자·기기·family 소유·활성·만료 검사, 만료 JWT와 위조 구분, 공통 오류·requestId.
- 팀원 인터페이스: FocusOnPrincipal을 @AuthenticationPrincipal로 사용. 필수 기기 헤더는 DeviceHeaderValidator.require로 검증.
- 동시성: Google 통신은 DB 잠금 밖에서 수행, 사용자 행 잠금과 DB UNIQUE로 최초 계정 중복·Desktop 3대 초과 방지. 같은 설치는 기기 재사용·기존 family 폐기.
- 저장 모델: 기존 V1·V2 그대로 재사용. OAuth UNIQUE를 Entity 메타데이터에도 명시했으며 새 migration은 없다.

## 변경 위치

| 위치 | 책임 |
| --- | --- |
| auth/controller, dto | HTTP 계약·검증·안전한 응답 |
| auth/service | Google 통신·DB 로그인 transaction·한도·해시 |
| auth/security | principal·현재 인증·필터·기기 헤더 |
| AccessJwtService | 검증된 만료를 위조와 구분 |
| auth/persistence | 기존 제약 metadata·기기 조회·이전 refresh 폐기 |
| application.properties, docker-compose.yml, .env.example | 서버 Google 설정·직접 연결 IP 기준 |

## 검증

- 정상·누락·잘못된 값·인증 실패·기기 위장·재로그인·기기 한도·동시 최초 로그인 검증.
- Google 테스트는 합성 RSA 키·모의 HTTP 응답으로 검증. 실제 Google 계정 왕복은 미실행.
- 전체 빌드·Checkstyle·회귀 테스트와 PostgreSQL 결과는 google-login-QA.md에 기록.
- 내부 검증과 독립 리뷰는 분리했으며 발견사항 두 건은 수정·재검토했다.

## 현재 제한

1. Refresh Token 초기 발급까지만 포함. refresh·logout API와 실제 로그인 유지·앱 재실행 복구는 후속이다.
2. Extension pairing·기기 관리·사용자 설정 API는 이번 범위 밖이다. 4번째 새 기기의 기존 기기 폐기 인증 수단도 기기 관리에서 정해야 한다.
3. 한도는 단일 프로세스 fixed window·각 공간 최대 10,000키다. 로그인 공간 포화는 429로 거절하며 기존 인증 공간과 독립이다. 재시작 시 초기화되고 다중 인스턴스에서는 공유 저장소가 필요하다.
4. forward-headers-strategy=none으로 임의 forwarded IP를 신뢰하지 않는다. reverse proxy 배포에서는 proxy 신뢰·IP 제한을 별도로 구성해야 한다.
5. 초기 family·Refresh Token은 30일. 갱신 시 family 수명 연장 방식은 후속 refresh 계약에서 확정한다.
6. 로컬 server/.env의 Google 설정·JWT 키와 Desktop .env.local은 Git에서 제외했다. 값과 원본 첨부 JSON은 commit하지 않는다.

## 문서 상태

- CONTEXT·NEXT_TASK·시작 문서·환경 안내를 현재 코드 상태로 갱신한다.
- PR 생성·merge·이슈 종료는 아직 수행하지 않는다.

## PR #51 후속 리뷰

CodeRabbit 지적 4건을 수정했다. IP별 300회/10분 선행 보호와 기존 복합 키 5회/10분을 함께 적용하고 문서 3건을 정정했다. 최신 dev PR #50 반영 후 전체 32개 테스트·build·Checkstyle 및 독립 리뷰를 통과했다. 병합·Issue 종료와 실제 Google 계정 왕복은 아직이다.

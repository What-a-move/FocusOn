# Backend 다음 작업

## 현재 작업 (2026-10-27)

- Issue #79: 개발용 Swagger·FocusOn JWT Authorize·Google PKCE 보조 도구. Branch `chore/#79-swagger-testing`, 기준 dev `65da4f0`.
- 구현·독립 리뷰·build/Checkstyle·Backend62·Python10·실제 PostgreSQL HTTP32 확인을 완료했다. 실제 Google 계정 성공 테스트는 사용자 인증이 필요하다.
- 다음 승인 단계는 PR 생성이며 제목·본문 초안을 먼저 제시한다. merge·Issue 종료도 별도 승인한다.
- 이전 #59 / PR #60은 dev 병합·종료됐다. 아래 내용은 당시 기록이며 현 작업으로 재개하지 않는다.
- 사용법: [SWAGGER_TESTING.md](SWAGGER_TESTING.md).

## 이전 작업 기록

- Issue #59: refresh·회전·재사용 감지·Desktop logout 구현·QA 완료, PR #60 생성 및 CodeRabbit 전체 리뷰 actionable comment 0건.
- Branch: `feat/#59-auth-lifecycle`; dev 직접 push 금지.
- 선행 #49 / PR #51은 dev 병합·이슈 종료 완료.
- 계획: `features/auth-lifecycle-PLAN.md`, 검증: QA·code-review·REPORT·ERROR.

## 이어갈 순서

1. #59 주석 보완 커밋의 원격 CI 결과를 확인하고 병합 여부를 검토한다. merge·이슈 종료는 별도 사용자 승인.
2. Google 실계정 OAuth 성공은 본인 인증 응답이 필요하다. 실제 서버/Keychain으로 합성 로그인 세션의 복구·회전·logout은 검증했으나 실계정 성공으로 표현하지 않는다.
3. Desktop 전체 빌드의 로컬 Swift compiler/SDK 불일치 해결은 Frontend 환경 작업으로 구분한다. 인증 TS·서비스 테스트와 Keychain은 검증됐다.
4. #58 pairing 기획 검토를 재개한다. 코드 오입력 시도 대상·화면 닫기 즉시 취소·재연결 경계를 임의로 정하지 않는다.
5. 기기 조회·선택 폐기, Extension unlink를 별도 API 작업으로 진행한다. #38 사용자·온보딩·목표 구현을 중복하지 않는다.
6. 이후 목표·세션·분석·리포트 API는 관련 최신 정본과 2026-10-26 화면 분석 공유 계약을 먼저 대조한다.

## 구현 유지 기준

- refresh 요청은 body token만으로 인증하며 Bearer를 요구하지 않는다.
- 성공마다 새 Refresh Token과 family 만료를 30일 연장한다. single-flight·strict reuse 정책이며 같은 token의 재전송 grace는 없다.
- 로그인·refresh·logout의 DB 변경 잠금 순서는 사용자→family다. 신규 인증 변경도 이 순서와 폐기 commit 경계를 확인한다.
- 기존 V1/V2를 수정하지 않고 새 schema는 후속 migration으로 분리한다.

## PR 검증 결과

PR #60 생성, Backend CI #45 성공, CodeRabbit dd93475 전체 리뷰 actionable comment 0건. 주석 경고 보완 후 CI를 다시 확인한다. 병합·Issue #59 종료는 사용자 별도 승인이 필요하다.

## 로그인 최종 QA 갱신 (2026-10-26)

- PR #60에 최신 dev 통합과 Desktop logout 만료 재시도 수정, 독립 재검토·Backend/PG/Desktop/Extension/HTTP QA 결과를 반영했다.
- 세부 결과는 auth-lifecycle-QA.md가 현재 기준이다. 임시 VFS 전체 빌드 성공과 기본 Swift 설치 복구, 합성 인증과 실제 Google 계정 성공을 구분한다.
- Mac 잠금 해제·실계정 인증, 관리자 인증 후 개발 도구 복구를 확인한다. PR의 최신 CI·CodeRabbit 결과 확인 뒤 병합·Issue 종료는 별도 승인받는다.

## CodeRabbit 재리뷰 수정 완료 (2026-10-26)

- 유효 Access Token으로 logout을 먼저 시도하고, 실제 만료에서만 refresh·한 번 재시도한다.
- POST refresh IP 한도는 본문 바인딩 전 Filter에서 한 번 집계한다. 잘못된 JSON·길이초과도 301번째에429, 정상 형식은300회까지처리·301번째429다.
- 잔여20초·refresh503·logout204 조건20회에서 성공0→20회, 불필요한refresh20→0회. Backend58·Desktop96·PostgreSQL28·실제HTTP/Keychain33건 회귀와 독립 리뷰를 통과했다. 수치는 통제된 기능 검증이며 운영성공률이 아니다.
- 최신 dev d03c2d2 통합·문서 충돌 해소, 로그인 결정026/공유타입결정025 분리. PR #60의 push·CI·리뷰 스레드 결과를 확인하며 merge·Issue종료는 별도 승인받는다.

# Backend 다음 작업

## 현재 작업

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

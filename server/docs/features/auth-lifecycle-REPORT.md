# 로그인 수명주기 구현 결과

- 날짜: 2026-10-26 / Issue #59 / feat/#59-auth-lifecycle
- 상태: 구현·로컬/실제 DB/HTTP QA 완료. PR #60 생성, Backend CI·CodeRabbit 전체 리뷰 통과, dev 미병합.

## 구현

POST /api/v1/auth/refresh와 POST /api/v1/auth/logout을 추가했다. Google 교환 DTO/Controller와 분리해 TokenLifecycleController·DTO·Service로 관리한다. TokenFamilyGuard는 요청 인증과 refresh가 같은 소유/활성·부모 Desktop 로그인 조건을 사용하게 한다.

사용자→family 잠금 아래 token 회전, parent 이력, 새 ACTIVE 및 30일 token/family 만료 연장을 원자적으로 처리한다. 재사용 시 해당 family를 commit으로 폐기하고 401을 반환한다. 현재 Desktop logout은 직접 연결된 Extension만 함께 폐기한다. 공개 refresh 한도 공간은 Google·인증 요청과 분리했다.

기존 V1/V2와 Entity 컬럼을 재사용해 migration 변경은 없다. 원문 token 비저장·DTO 문자열 비노출·공통 오류를 유지했다. Backend 팀원의 #38 API와 #58 pairing 구현은 수정하지 않았다.

## 검증

- 전체 55개 Backend 테스트·clean build·Checkstyle 통과
- 실제 PostgreSQL 25개 테스트·V1/V2·validate 통과
- 실제 jar HTTP·기존 Desktop 인증 서비스·macOS Keychain 33건 통과
- Desktop 전체 Electron 테스트 89개·Extension 22개 통과. 최종 독립 리뷰의 Desktop P2 1건을 수정하고 재검토 완료

정확한 범위·제한은 auth-lifecycle-QA.md와 code-review.md를 따른다. 실제 Google 실계정 성공, 전체 Desktop native 빌드, 기기 목록·선택 폐기·pairing·unlink는 완료로 표시하지 않는다. merge·이슈 종료는 별도 승인받는다.

## 원격 검토

- PR: https://github.com/What-a-move/FocusOn/pull/60
- Backend CI #45 success, CodeRabbit dd93475 전체 리뷰 actionable comment 0건.
- 주석 커버리지 경고는 메서드·테스트 의도 설명을 보강했다. 수치 재평가 통과를 의미하지 않는다.

## 최종 로그인 연동 보완

최신 dev 905c06c를 통합했다. Desktop logout의 만료 401 성공 오처리를 수정해 refresh 뒤 서버 폐기까지 한 번 재시도한다. 이 변경은 QA에서 확인된 기존 API 소비자의 버그 수정이며 새 endpoint나 정책은 없다.

Desktop 전체 build는 임시 Swift VFS QA 환경에서 성공했고 Extension 전체 build도 성공했다. 기본 Swift 환경은 관리자 인증을 통한 설치 복구가 남아 있다. Mac 잠금으로 실제 UI와 Google 계정 인증은 진행하지 못했다. 분석 계약 미커밋 문서는 이번 로그인 커밋에 넣지 않는다.

실제 Google의 잘못된 code 거절(401)·사용자 미생성까지 검증했으며 Backend/Desktop Client ID 일치를 확인했다. Google 계정의 정상 인증 성공을 대신하지 않는다.

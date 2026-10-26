# 로그인 수명주기 구현 결과

- 날짜: 2026-10-26 / Issue #59 / feat/#59-auth-lifecycle
- 상태: PR #60 dev 병합·Issue #59 종료 완료(2026-10-26). 아래는 구현·초기 QA의 과거 기록이다. 후속 Desktop 수정과 최신 검증은 Issue #74에서 구분한다.

## 구현

POST /api/v1/auth/refresh와 POST /api/v1/auth/logout을 추가했다. Google 교환 DTO/Controller와 분리해 TokenLifecycleController·DTO·Service로 관리한다. TokenFamilyGuard는 요청 인증과 refresh가 같은 소유/활성·부모 Desktop 로그인 조건을 사용하게 한다.

사용자→family 잠금 아래 token 회전, parent 이력, 새 ACTIVE 및 30일 token/family 만료 연장을 원자적으로 처리한다. 재사용 시 해당 family를 commit으로 폐기하고 401을 반환한다. 현재 Desktop logout은 직접 연결된 Extension만 함께 폐기한다. 공개 refresh 한도 공간은 Google·인증 요청과 분리했다.

기존 V1/V2와 Entity 컬럼을 재사용해 migration 변경은 없다. 원문 token 비저장·DTO 문자열 비노출·공통 오류를 유지했다. Backend 팀원의 #38 API와 #58 pairing 구현은 수정하지 않았다.

## 초기 검증 (이전 단계의 기록)

- 전체 55개 Backend 테스트·clean build·Checkstyle 통과
- 실제 PostgreSQL 25개 테스트·V1/V2·validate 통과
- 실제 jar HTTP·기존 Desktop 인증 서비스·macOS Keychain 33건 통과
- Desktop 전체 Electron 테스트 89개·Extension 22개 통과. 최종 독립 리뷰의 Desktop P2 1건을 수정하고 재검토 완료

정확한 범위·제한은 auth-lifecycle-QA.md와 code-review.md를 따른다. 실제 Google 실계정 성공, 전체 Desktop native 빌드, 기기 목록·선택 폐기·pairing·unlink는 완료로 표시하지 않는다. 당시 merge·이슈 종료는 별도 승인 대상이었으며 이후 PR #60은 병합·Issue #59는 종료됐다.

## 초기 원격 검토 (dd93475·CI #45 기준)

- PR: https://github.com/What-a-move/FocusOn/pull/60
- Backend CI #45 success, CodeRabbit dd93475 전체 리뷰 actionable comment 0건.
- 주석 커버리지 경고는 메서드·테스트 의도 설명을 보강했다. 수치 재평가 통과를 의미하지 않는다.

## 최종 로그인 연동 보완

최신 dev 905c06c를 통합했다. Desktop logout의 만료 401 성공 오처리를 수정해 refresh 뒤 서버 폐기까지 한 번 재시도한다. 이 변경은 QA에서 확인된 기존 API 소비자의 버그 수정이며 새 endpoint나 정책은 없다.

Desktop 전체 build는 임시 Swift VFS QA 환경에서 성공했고 Extension 전체 build도 성공했다. 기본 Swift 환경은 관리자 인증을 통한 설치 복구가 남아 있다. Mac 잠금으로 실제 UI와 Google 계정 인증은 진행하지 못했다. 분석 계약 미커밋 문서는 이번 로그인 커밋에 넣지 않는다.

실제 Google의 잘못된 code 거절(401)·사용자 미생성까지 검증했으며 Backend/Desktop Client ID 일치를 확인했다. Google 계정의 정상 인증 성공을 대신하지 않는다.

## Docker 검증

실제 배포 Dockerfile의 이미지 빌드와 docker 프로필 컨테이너 기동을 확인했다. UID10001·read-only rootfs·tmpfs·no-new-privileges 환경에서 HTTP/Keychain·Google 오류·회전·logout 35건을 통과했다. 임시 DB·컨테이너·환경 파일은 정리했고 개발 데이터는 유지했다. 최신 검증 커밋537a09a의 Backend CI #53도 통과했다.

## CodeRabbit 재리뷰 수정 검증

- 최신 dev d03c2d2 통합·문서 충돌 해소. shared-types 결정025 유지, 로그인 결정026으로 분리했다.
- 유효 token logout의 선갱신 제거: 잔여20초·refresh503·logout204 조건20회에서 성공0/20→20/20, refresh20→0회, logout0→20회. 이는 통제된 기능 회귀이며 운영 성공률 측정이 아니다.
- refresh IP 한도를 본문 바인딩 전 Filter로 옮겼다. 잘못된 JSON·4097자 token은 처음300회400 후301번째가 수정 전400→수정 후429. 정상 형식은 처음300회401,301번째429 유지해 중복 집계가 없음을 확인했다. 모든 차단 응답의 양수 Retry-After·requestId와 다른 IP 격리를 검증했다.
- Backend 58개·build·Checkstyle, PostgreSQL 28개·V1/V2·validate, Desktop 96개·TypeScript·수정 auth 파일 ESLint, 실제 Google 잘못된 code 거절/HTTP/Keychain 33건 통과. PG 28은 전체58의 일부를 다른 DB 환경에서 실행한 결과다.
- 독립 리뷰에서 추가 P1/P2 없음. 실제 Google 계정 성공·기본 Swift 환경 복구는 기존 제한 그대로다. Docker35건은 이전 head의 검증이며 이번 변경의 새 Docker 재검증으로 표현하지 않는다.


## PR #60 종료와 후속 수정 상태

- 최종head `7d019f5`: Backend CI #59 success, CodeRabbit추가리뷰완료. 새코드지적없음·문서3건후속정리. PR60은merge6cd5d58로dev 병합,Issue59종료.
- PR60최종QA: Backend 58·PG 28·Desktop 96·HTTP/Keychain 33. 앞의55/25/89는초기기록이다.
- Issue74는새Desktop인증경쟁버그수정이며 API/DB정책을바꾸지않는다. 이번 로컬QA는Backend 58·PG 28·Desktop 130·HTTP/Keychain 45이고 `apps/desktop/docs/features/auth-session-races-QA.md`를따른다.
- 실계정 Google성공·전체 native 환경·운영배포검증은완료로표시하지않는다. 후속 PR의CI·CodeRabbit은각head기준으로따로확인한다.

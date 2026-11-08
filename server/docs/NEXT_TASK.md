# Backend 다음 작업

## Google 로그인 시간 오차 (#177)

`fix/177-google-login-clock-skew`에 #167이 병합된 최신 dev를 통합하고 실계정 로그인·인증 사용자 조회를 확인했다. PR 후 원격 CI와 리뷰를 확인한다. 이전 401과 시각 오차의 인과관계는 미확정이다.

## Issue #167 이어서 확인 (2026-11-08)

- PR #178 리뷰 수정 커밋을 푸시하고 재리뷰·원격 CI를 확인한다. 시계 역행과 이전에 처리되지 않은 지연 신호는 순번 없이 완전히 구분할 수 없다.
- 최신 dev 통합 후 격리 PostgreSQL의 V1~V13 적용과 Heartbeat 테스트를 검증했다. 사용자 개발 DB의 이전 Heartbeat V11 이력은 백업·복제 검증 후 데이터 보존 방식으로 해결했다. 다른 DB는 별도로 확인해야 한다. 상세는 `features/session-heartbeat-REPORT.md`.

## 현재 작업: #151 CORS·확장1개

- PR #170의 migration 잠금·POST preflight 지적과 외부 P2(만료된 최신 확장 보존)를 수정했다. 인증 가능 연결 우선 순위를 적용하고 실제 Flyway 업그레이드 회귀9종을 CI에 추가했다. V11 NOT VALID→V12 별도 검증, H2 전체137개 실패0/skip4. 최신 커밋의 원격 CI·재리뷰와 실제 Chrome QA를 확인한다. 병합·이슈 종료는 별도 승인한다.
- 실제 Chrome 권한 철회→서버 요청 전송→local-events 자동 멈춤은 팀원 5번 담당과 연동해 확인한다. CORS 허용만으로 host 권한 문제가 모두 해결됐다고 판단하지 않는다.
- 기존 다중 확장 정리 V11의 영향과 배포 CORS_ALLOWED_ORIGINS를 확인한다. 사용자 개발 DB에 즉시 적용하지 않는다.
- 이후 사용자 담당 6번 분석 요청·결과 저장/개입→7번 리포트 순서다. 세션 시작·heartbeat·종료 등 5번은 팀원 범위다.
- #86은 최신 dev 구현 확인 후 닫혔고 실제 앱 QA는 후속이다. #163은 Draft PR164에 보존한다.

## 과거 작업 기록 (2026-10-28, 현재 작업 아님)

- 2026-11-03 Issue #132: Swagger와 격리 PostgreSQL 동시 출발·기존 세션 데이터의 V10 적용 전후 보존 및 Backend 빌드·Checkstyle을 확인했다. Heartbeat 연계는 후속 작업이다. 상세는 `features/session-pause-resume-REPORT.md`를 따른다.

- 2026-11-02 Issue #97: 목표 수정·삭제 구현, 사용자 Swagger 테스트 및 PostgreSQL 동시 출발·실제 잠금 대기 테스트를 수행했다. Notion 정본의 PATCH 응답·재삭제 정책 대조가 남았다. 상세는 `features/goal-update-delete-REPORT.md`를 따른다.
- Issue #97 리뷰 대응의 사용자 Swagger 확인 및 PostgreSQL 잠금 회피 테스트를 완료했다. PR 리뷰에서 변경사항을 확인한다.
- 2026-11-01 Issue #100: 사용자 Swagger 확인과 Backend 빌드·Checkstyle, PostgreSQL 닉네임 동시성 테스트 및 V9 적용·소유권 제약 검증을 수행했다. 실제 Desktop·Extension 통합과 카메라 OS 권한 경계는 별도 확인한다. 세션 종료·Heartbeat는 후속 범위다. 상세는 `features/session-start-active-REPORT.md`를 따른다.
- 2026-10-28 Issue #87: 목표 생성·상세 조회를 구현 중이다. 사용자가 Swagger 테스트, Notion 경로·메서드 일치 및 `goals` 테이블 생성을 확인했다. AI 명확화 세션 검증은 해당 기능이 준비된 뒤 연동해야 한다. 기존 닉네임 동시성 테스트 실패와 수동 테스트의 세부 결과를 확인한다. 상세는 `features/goal-create-read-REPORT.md`를 따른다.

- #58 연결과 #86 Extension 연결 해제·기기 조회는 PR #91에 제출돼 리뷰 중이다. #85 온보딩 준비 상태 PR #92는 #91에 의존한다. 두 PR 모두 dev 미병합이다.
- Issue #83: PostgreSQL 동시 시작 테스트와 local 서버의 실계정 인증 HTTP QA를 수행했다. PR 전 최신 dev의 migration 번호를 대조한다. 개발 DB와 격리 테스트 DB에 V4 적용은 확인했다. 사용자 `UserEntity` 미커밋 변경은 별도로 보존한다.
- Issue #38의 사용자 설정 PR #82가 dev에 병합됐다. 관련 CodeRabbit 지적을 다룬 PR #84가 dev에 병합됐다.
- 검증: 최신 dev 통합 후 build/Checkstyle·H2 JUnit87·PostgreSQL 연결16/사용자 설정7·V1~V5 통과. 이전 기준 HTTP33·DB제약6. 실제 Frontend UI 통합은 미완료다.
- PR 생성·병합·이슈 종료는 승인 없이 실행하지 않는다.

## 과거 작업 순서 (현재 작업 아님)

1. PR #91·#92의 리뷰와 CI를 확인하고 지적을 수정한다. #91을 먼저 처리하며 병합·이슈 종료는 별도 승인을 받는다.
2. Frontend 담당에게 extension-pairing-frontend-handoff.md를 전달하고 실제 앱 연결 QA. 이 AI가 Frontend 파일을 수정하지 않는다.
3. #86 기기 조회·Extension unlink와 선행 #58 연결 코드를 PR #91에서 검토한다. 기기 선택 폐기 및 Frontend 화면은 별도 범위다. #85 readiness PR은 #91에 의존한다.
4. #79는 PR81 병합·실제 Google QA 완료 상태이며 종료는 승인 후 진행한다.

아래는 과거 작업 이력이다. 미완료 문구를 현재 지시로 재개하지 않는다.

## 이전 작업 기록

- Issue #59: refresh·회전·재사용 감지·Desktop logout 구현·QA 완료, PR #60 생성 및 CodeRabbit 전체 리뷰 actionable comment 0건.
- Branch: `feat/#59-auth-lifecycle`; dev 직접 push 금지.
- 선행 #49 / PR #51은 dev 병합·이슈 종료 완료.
- 계획: `features/auth-lifecycle-PLAN.md`, 검증: QA·code-review·REPORT·ERROR.

## 과거 이어갈 순서 (현재 순서 아님)

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

## PR 검증 결과 (아래 이전 항목은 당시 이력)

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

- #58 → #86 → #85 의존 변경의 PR·병합은 승인 절차 후 순서대로 처리한다. 기기 목록 응답의 data.devices 래퍼를 Frontend와 확인하고, Frontend의 연결 취소·오류·연결 해제 UI 반영 후 실제 Desktop·Extension QA를 진행한다.

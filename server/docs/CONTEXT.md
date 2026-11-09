# Backend 작업 컨텍스트

## 실제 AI 연결 검사 준비 (2026-11-09)

- 학교 모델 IP 100.116.183.94를 받았으나 HTTPS443/HTTP11434 연결 시간 초과, 현재 경로 en0. 사용자가 11434 포트를 확인했으며 Tailscale 연결 확인 중이다. 학교 모델/실제 AI 성공으로 기록하지 않는다.
- AI PR #190 최신 d703397의 candidate 기준은 0.6이다. 아래 0.75 인계 필요 문장은 이전 head 당시 기록이다. Backend AI 주소/키와 실제 실행 AI 서비스는 추가 확인이 필요하다.
- 합성 검사 도구/명시 실행 Java live 검사 준비. loopback14개 통과, Backend build·Checkstyle 239개 실패0·생략12(실제 AI2개 포함). 상세는 features/analysis-ai-live-qa.md.
- 새 공개 계약 검토안: features/intervention-policy-integration-contract.md. 전경 context/receipt·lease·수신 기준 cooldown은 승인 전이며 구현하지 않았다. 기존 production 생성 보류는 유지한다.


## #182 서버 검증 결과 (2026-11-09)

- `feat/182-server-intervention-policy`: 내부 코어/영속화/V16/개입 GET·ack/최소 연결 구현. 전체 build·Checkstyle 통과(226개 실패0·생략10), 격리 PG87개 전부 통과. 임의 포트 실제 HTTP 검증 포함.
- 격리 리뷰 P1 순서 불명 관측의 정책 전이 누락 보완·재검토 완료. PG pending_slot NULL CHECK 보강 검증.
- 남은 필수 연결: P1/P2 전경 context·관측 소속·유효성, P3/P4 전송 receipt, P5 세션 담당 경계, P6 #154, P7 전체 앱 QA. 기본 production 생성 보류.
- Notion 문단 원문 복구 확인, 신규 rolling/30초 정책 정본 반영은 미완료. 정본 상세 404/actedAt nullable도 대조 필요. PR/CI/병합/Issue 종료 미수행.
- QA/리뷰/결과: `features/intervention-policy-{QA,code-review,REPORT}.md`.


## #182 내부 구현 진행 (2026-11-09)

- 작업 브랜치 `feat/182-server-intervention-policy`, 최신 dev `5462afd` 기반. 기존 staged IDE 파일·stash 보존.
- 사용자 지적 반영: 재분석 PENDING은 확정 UNCERTAIN과 분리해 같은 episode/누적을 유지하고 대기 시간/생성만 동결한다.
- 사용자 추가 승인: rolling 창 내부 clipping·열린 구간 1회, 미전송 PENDING TTL 30초. Notion은 원문 복구만 승인받아 복구했고 신규 정책 반영은 미완료다.
- 코어/typed JSONB 상태·개입 이벤트/V16/기존 조회·ack API/최소 분석·heartbeat 연결 구현, 검증 중. 전체 완료 아님.
- 전경/관측 소속 adapter 기본값은 없음. 전송 receipt 수신도 미구현. 합성 테스트와 production 앱 알림은 구분한다.
- 실제 통합 P1~P7은 Issue #182·PLAN·handoff에서 추적한다. Frontend/shared-types/AI 직접 수정 없음.


## Issue #182 상세 기획 검토 (2026-11-09 KST)

- 사용자 승인으로 [#182](https://github.com/What-a-move/FocusOn/issues/182) 생성 완료. 단계 0~3 완료, 계약 승인·기획 최종 승인·구현 승인은 대기다. 브랜치·구현·PR은 아직 없다.
- `features/intervention-policy-PLAN.md`에 내부 저장/서버 시간 구간/잠금/멱등 ack/outbox 설계와 미정 사용자 동작 권고안을 작성했다.
- 확인했어 이후 5분은 확정된 Desktop UI 상태다. 미정은 서버 실제 전송 확인 계약이며 GET/생성을 전송으로 간주하지 않는다.
- 기기 선택은 Desktop 전경 context 위임을 권고한다. 기존 기기별 counter/시각 비교나 임의 보호 TTL로 최신 화면을 추정하지 않는다.
- 추가 승인 대상: 전경 context 계약, 별도 delivery receipt와 수신 기준 cooldown, rolling 창 경계, 미전송 PENDING 만료. `features/intervention-policy-handoff.md`는 미전송 인계 초안이다.
- #154 미완료와 무관하게 Policy/API·합성 테스트는 진행 가능하다. 실제 AI→포키 QA는 별도다.


## 최신 원격 확인·다음 범위 검토 (2026-11-09 KST)

- fetch한 origin/dev는 `fc340ffe578ff36762ebb027a8c09c545acf89e1`. PR #172 MERGED, head `e77438e`, 원격 Backend CI 두 job SUCCESS를 GitHub에서 재확인했다. CodeRabbit NEUTRAL은 한도에 따른 SKIPPING이며 최종 수정분 봇 재검토 완료가 아니다.
- #171 체크리스트 0~11은 완료이며 별도 종료 승인이 없어 OPEN이다. 아래 PR172 미병합 문장은 과거 기록이다.
- 사용자 담당은 ① 기반 ② 인증·기기 ⑥ 분석·개입 ⑦ 리포트·학습 정리다. 사용자·목표·세션 상태 명령은 팀원 범위이며 Frontend/shared-types/AI는 읽기·인계만 한다.
- 다음 Policy Engine/개입 기존 Issue·PR은 전체 목록 검색에서 발견하지 못했다. `features/intervention-policy-PLAN.md`는 Issue 등록·기획 검토 승인을 위한 초안이며 구현 승인이 아니다. 실제 표시/cooldown·기기 간 현재 화면 선택 등 미정 경계를 확정 계약으로 취급하지 않는다.
- #154는 OPEN이며 dev AI 정식 경로가 아직 RECEIVED를 반환함을 코드로 확인했다. 실제 AI/앱 통합 완료와 Server 구현 완료를 구분한다.
- 현 checkout만 사용하고 IDE·Claude outputs·기존 stash를 보존한다. 새 worktree·FocusOn-swagger는 사용하지 않는다.

## 현재 작업: #171 분석 요청·결과 저장 (2026-11-08)

- feat/171-analysis-runs는 최신 dev 91cd524에서 분기했다. POST /sessions/{id}/analysis-runs의 공개 요청·응답, runner/연결 Extension 권한, 보호 상태, 결과 저장과 AI 내부 adapter를 구현했다. 계획·검증은 features/analysis-runs-*를 따른다.
- 기기별 source 무관 observationSequence 우선 조회와 metadata UNRELATED의 유효 UNCERTAIN 변환을 제공한다. 개입·heartbeat Policy Engine·리포트는 아직 구현하지 않았다. 세션 명령은 팀원 #167~#169 범위다.
- AI 정본(2026-11-03)은 정식 /internal/v1/analysis-runs의 판정 반환·서비스 Bearer와 베타 uncalibrated 예외를 명시한다. 현재 dev AI 코드는 수신 확인만 반환하므로 서버는 안전 fallback하며, 실제 판정 통합은 #154 이후 확인한다.
- 기본 Backend build/Checkstyle·테스트200개(실패0/오류0/생략9)를 통과했다. PostgreSQL 결과는 features/analysis-runs-QA.md를 따른다. PR #172 리뷰의 host 누락 검증·테스트 보강을 반영했다. merge·이슈 종료는 승인 후 진행한다.
- 실제 개발 DB/8080/Frontend/AI는 수정하지 않았다. 관측 순서 정본은 사용자 위임에 따라 Notion에도 반영했다.

- PR172 후속 P2: TAB_META host 검증과 기기·서버 시각 직접 비교를 보완했다. 고정±60초 시작/재개 회귀를 추가했으며, 실행 도중 기기 시계 보정은 선택 observationSequence·구형 판정 보류/보호 유지로 보완했다. 최신 dev24db7de 통합 후 로컬 PG80개 통과, 실제 Frontend counter는 analysis-observation-order-handoff.md에 인계했다.

## Google 로그인 시간 오차 (#177)

Google ID 토큰 발급 시각에 30초 오차를 허용한다. 최신 dev 통합 후 실제 Google 로그인 교환 201과 인증 사용자 조회 200을 격리 DB에서 확인했다. 이전 401의 정확한 원인은 미확정이며 상세는 `features/google-login-clock-skew-REPORT.md`를 따른다.

## 진행 중: Issue #167 Heartbeat (2026-11-08)

- PR #178 리뷰에 따라 자동 정리 잠금·실패 격리와 시계 역행 복구를 구현했다. 전체 build·Checkstyle과 격리 PostgreSQL 테스트를 확인했으며 지연 신호 구분의 한계는 `features/session-heartbeat-REPORT.md`를 따른다.
- 실계정 HTTP와 PostgreSQL 동시성 검증을 수행했다. 상세 및 미검증 사항은 `features/session-heartbeat-REPORT.md`를 따른다.

## 직전 작업: #151

- 사용자 담당 범위는 1기반·2인증/기기·6분석/개입·7리포트다. 기존 #151을 재사용하고 feat/151-extension-access에서 CORS·활성 확장1개를 구현했다.
- 최초 H2 전체135개·분리PG30개를 통과했고, PR170 리뷰 수정 후 H2 전체137개 중 실패0·생략4. 최신 PG 결과는 features/extension-access-QA.md를 따른다. 실제 HTTP preflight200·미인증 실제 요청401 및 V11 기존 데이터 보존·unique 거절 검증 완료. 상세는 features/extension-access-REPORT.md를 따른다.
- PR #170을 제출했고 CodeRabbit의 migration 잠금 지적과 POST preflight 개선을 반영했다. 최신 커밋 CI·재리뷰·실제 Chrome 권한 철회 QA는 남아 있다. 사용자 개발 DB·8080 서버에는 적용하지 않았다.
- #163 세션 종료는 담당 범위 밖의 기존 작업으로 별도 브랜치와 Draft PR164에 보존한다. #116은 PR162 병합·이슈 종료 완료다.

- 외부 P2 지적으로 V11을 실제 인증 가능 연결 우선 순위로 수정했다. ExtensionMigrationTests의 V10→V12 회귀9종을 CI에 추가하고 관련 PostgreSQL41개 모두 통과했다.

## 이전 작업: 서버 요청 추적 (#116, 당시 기록)

`feat/#116-server-tracing`에서 traceId·JSON 요청 로그를 구현하고 로컬 QA를 수행했다. 범위와 한계는 `features/server-tracing-PLAN.md`, `server-tracing-QA.md`, `server-tracing-REPORT.md`를 확인한다. 독립 리뷰·PR은 아직 완료하지 않았다. 사용자의 기존 문서 수정은 작업 시작 전 Git stash에 보존했다.

## 최신 작업 인계 (2026-11-01)

이전 작업 상태 기록은 오래됐을 수 있다. Issue #112의 수정과 검증 범위, 로깅·가짜 판정기 후속 작업과 사용자 선호는 [NEXT_CHAT_HANDOFF.md](NEXT_CHAT_HANDOFF.md)를 먼저 확인한다. 주요 장애와 재현 결과는 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)에 정리했다. 시작 전 실제 Git·이슈 상태를 다시 확인한다.

## 담당 영역

- Spring Boot REST API
- 인증, 사용자, 기기 연결, 목표, 세션, 분석 이벤트, 리포트
- Desktop·Extension·AI 연동
- PostgreSQL 스키마와 Flyway migration

## 현재 작업 (2026-10-28)

- 2026-11-03 Issue #132: 세션 일시정지·재개를 구현했다. Swagger·PostgreSQL 동시 출발 및 전체 빌드·Checkstyle을 확인했다. 격리 DB에서 V9 세션 3건의 Flyway V10 적용 전후 기존 데이터 보존을 확인했다. Heartbeat 연계는 후속 작업이다. 상세는 `features/session-pause-resume-REPORT.md`를 따른다.

- 2026-11-02 Issue #97: `feat/97-goal-update-delete`에서 목표 수정·soft delete와 활성 세션 참조 차단을 구현했다. 사용자 Swagger 테스트 확인 후 PostgreSQL 동시 출발과 실제 잠금 대기 테스트가 각각 2개씩 통과했다. Backend 빌드·Checkstyle도 통과했으며 Notion 세부 계약 대조는 남았다. 상세는 `features/goal-update-delete-REPORT.md`를 따른다.
- Issue #97 리뷰 대응: 잘못된 목표 문구는 사용자 행 잠금을 피하도록 개선했다. 사용자 Swagger 확인과 PostgreSQL 잠금 회피·오류 우선순위 테스트 및 전체 빌드·Checkstyle이 통과했다.
- 2026-11-01 Issue #100: `feat/100-session-start-active`에 세션 시작·활성 조회, V8 세션·멱등성 저장과 V9 소유권 제약을 구현했다. 사용자 Swagger 확인, H2 빌드·Checkstyle, 별도 PostgreSQL 닉네임 동시성 테스트와 Flyway V1~V9 적용을 확인했다. 실제 Desktop·Extension 통합은 별도다. 상세는 `features/session-start-active-REPORT.md`를 따른다.
- 2026-10-28 Issue #87: `feat/87-goal-create-read`에서 수동 목표 저장·본인 상세 조회를 구현 중이다. AI 명확화 이력 검증은 선행 기능이 없어 미완료이며, 상세 검증 현황은 `features/goal-create-read-REPORT.md`를 따른다.

- Issue #58, feat/#58-extension-pairing, 최초 기준 origin/dev b293bf1에서 구현하고 최신 dev 6c93515를 통합했다. server/만 수정했고 PR은 아직 생성하지 않았다.
- Issue #83: 닉네임 유일 제약 오류 변환, 동의·집중 설정 저장 잠금, 고정 닉네임 테스트를 수정했다. 사용자 결정으로 최신 동의 출처를 저장한다. PostgreSQL 동시 시작 테스트는 통과했지만 닉네임 사전 검사 두 건이 겹쳤는지는 확인하지 못했다. 상세는 `features/user-configuration-review-REPORT.md`를 따른다.
- Issue #38 사용자 설정 API: 도메인 반복 교체 충돌과 닉네임 문자 검증을 수정하고 계정 격리 테스트를 보강했다. 실행 중인 PostgreSQL에서 Flyway V1~V3 성공 기록과 Docker 서버 8개 API의 무인증 401을 확인했다. 최신 로컬 변경의 실계정 HTTP 재검증과 Notion 오류·권한 상세 대조가 남아 있다. 상세는 `features/user-configuration-REPORT.md`를 따른다.
- 코드 발급·공개 Extension 연결·코드 취소, 설치별3회 실패/3분 잠금·재연결·소유권 제약을 구현했다.
- Notion API·인증 정책·공통 오류·ERD에 사용자 승인 계약 반영. 결정030 및 extension-pairing-PLAN.md가 현재 기준이다.
- 최신 dev 통합 후 build/Checkstyle·H2 JUnit87·PostgreSQL 연결16/사용자 설정7·V1~V5 통과. PostgreSQL 닉네임 동시 사전 검사의 정확한 겹침은 미계측이다. 실제 Frontend UI 통합은 별도다.
- 전달 사항은 features/extension-pairing-frontend-handoff.md. 닉네임·프로필은 팀원 작업이며 #86에 연결 해제·기기 조회가 구현됐다. readiness는 후속 #85 브랜치에서 구현됐다.
- 선행 Swagger PR #81은 dev b293bf1에 병합됐고 실제 Google Swagger exchange→me→refresh→logout→401도 완료했다. Issue #79 종료는 별도 사용자 승인 전이다.
- #59/PR #60도 이미 병합·종료됐다. 아래의 병합 전/실계정 미완료 문구는 당시 이력이며 현재 상태를 뜻하지 않는다.

## 현재 상태

### 완료

- Spring Boot 4.1.1 / Java 25 / Gradle 기본 프로젝트
- PostgreSQL 17과 Backend를 함께 실행하는 Docker Compose 구성
- local·docker 프로필과 환경 변수 예제
- Flyway migration 위치와 작성 규칙
- 2026-10-12 정본 엔드포인트 42개를 로컬 목록과 대조했다. 현재 세부 계약의 누락·충돌은 `docs/NOTION_SYNC_AUDIT.md`에서 추적하며, 전체 명세가 최종 확정됐다는 의미는 아니다.
- 로컬 API 계약과 Backend 불변 규칙 최신화
- 분석 제외 도메인, 세션별 개입 정책 버전, 집중 달력, 학습 정리 저장 계약 반영
- Issue #25: 15분 Access JWT 발급·서명/클레임 검증 서비스와 환경 변수 설정
- Issue #27: Backend PR용 GitHub Actions CI와 AI 리뷰 가이드

### 최근 구현

- Issue #30: 공통 API 성공·오류 응답, requestId, Controller 예외 처리 구현 ([PR #31](https://github.com/What-a-move/FocusOn/pull/31))
- Issue #32: 인증 5개 테이블의 Flyway V1·소유 관계 보강 V2와 Entity·Repository를 구현했다. PostgreSQL 17.11에서 migration 적용·Hibernate 스키마 검증·소유권 FK를 확인했고, 빌드·QA·CodeRabbit 리뷰를 마쳤다. [PR #35](https://github.com/What-a-move/FocusOn/pull/35)는 2026-10-22 `dev`에 병합됐고 Issue #32도 종료됐다.
- 2026-10-24 Frontend 공유·최종 정정에 맞춰 동의·Extension 연결·개입 v1·시간대·리포트 계약을 로컬 문서에 반영했다. 현재 미구현 API의 완료를 뜻하지 않는다. Notion 내 이전 AI 정책과 일간 배열 항목 누락, pairing 실패 시도 문맥 등 남은 검토는 `docs/NOTION_SYNC_AUDIT.md`에 기록했다.

### 인증 구현 현황 (아래 항목은 당시 이력)

- Issue #49 / PR #51은 2026-10-26 dev에 병합·종료됐다. Google 교환·auth/me·요청 인증·초기 토큰 발급·azp 검증을 포함한다.
- Issue #59는 `feat/#59-auth-lifecycle`에서 refresh·rotation·reuse 감지·Desktop logout을 구현했다. 아직 dev 병합 전이다.
- refresh 성공 때 새 token과 family 만료를 현재부터 30일로 함께 연장한다(사용자 확정). 사용자→family 잠금과 transaction 결과 분리로 재사용 폐기를 commit한 뒤 401을 반환한다.
- logout은 현재 Desktop과 직접 연결된 같은 사용자 Extension만 폐기하고 다른 Desktop은 유지한다. Extension 자체 unlink는 #58 후속 API 단위 작업이다.
- RequestAuthenticationService와 refresh는 TokenFamilyGuard의 동일 소유권·활성/만료·연결 Desktop 로그인 검사를 사용한다.
- #59 QA: 전체 55개 Backend 테스트·build·Checkstyle, PostgreSQL 25개 테스트와 V1/V2·validate, 실제 HTTP/Keychain 연동 23건, 기존 Desktop 인증 테스트 22개 통과. Google 실계정 성공 및 전체 Desktop 앱 빌드는 아래 제한과 QA 문서를 따른다.
- 관련 문서: `features/auth-lifecycle-{PLAN,REPORT,QA,code-review,ERROR}.md`.

### 다음 구현 범위

- #59 PR·CI·CodeRabbit 검토 후 병합과 종료 승인을 받는다.
- #58 Extension pairing은 기획 대기이며 코드 오입력 시도 문맥·취소 경계를 정본에서 해결한 뒤 구현한다.
- 기기 목록·선택 폐기, unlink는 별도 1~2개 API 단위 작업이다. #38 사용자·설정·목표 API는 타 담당 범위를 유지한다.

### 당시 미구현 기록 (현재 #86 상태는 상단 참고)

- 사용자·목표·세션 등 나머지 도메인 HTTP API. 인증 Entity·Repository와 #49 로그인 요청 인증은 구현돼 있다.
- 기기 관리·Extension 연결 API. refresh·logout 수명주기는 #59 브랜치에 구현됐으며 dev 병합 전이다.
- 목표·세션·분석·리포트 API
- 보관 기간 cleanup worker와 학습 정리 worker
- AI 서비스 연동
- 운영 배포 구성

## 기술 기준

- Java 25, Spring Boot 4.1.1, Gradle
- PostgreSQL 17, Flyway
- REST `/api/v1`
- Database와 애플리케이션 시각 기준 UTC

## 필수 조건

- Notion `FocusOn API 명세서`를 엔드포인트 계약의 원본으로 사용한다.
- 공통 오류와 세션 상태는 Notion 하위 정책을 따른다.
- Controller, Service, Repository 책임을 분리한다.
- 요청 DTO와 응답 DTO를 분리한다.
- 원본 화면·카메라 영상과 인증 정보를 저장·로그하지 않는다.
- 계약 변경은 Notion을 먼저 갱신하고 소비자 및 로컬 문서를 같은 작업에서 맞춘다.

## 참고

- `docs/API_CONTRACT.md`
- `docs/DATA_PRIVACY.md`
- `server/docs/DEVELOPMENT_RULES.md`
- `server/docs/DECISION_RECORD.md`
- `server/docs/rules/migration-convention.md`

## 로그인 최종 연동 재검증 (2026-10-26)

- 최신 dev 905c06c를 통합한 PR #60에서 Desktop logout 만료 401 오처리 P2를 수정했다. 실제 서버 오류 뒤 refresh와 logout을 한 번 재시도하며 성공/명시적 재로그인에서만 로컬 token을 정리한다.
- Backend 55개·PostgreSQL 25개·Desktop 전체 Electron 89개·Extension 22개 테스트 통과. 실제 Google 잘못된 code 거절과 HTTP/Keychain 만료 경쟁까지 33건 통과. 독립 재검토에서 기존 P2 해결·추가 P1/P2 없음.
- Desktop full build는 오래된 중복 SwiftBridging modulemap을 숨긴 임시 VFS QA 환경에서 통과했고 Extension full build도 통과했다. 시스템 파일이나 빌드 스크립트를 수정하지 않았다. 기본 Swift 환경의 관리자 복구와 Mac 잠금 해제 후 실제 UI·Google 계정 성공은 남아 있다.
- #59 병합·종료는 사용자 별도 승인 대기. 세부 실행 증거는 features/auth-lifecycle-QA.md를 따른다.

## CodeRabbit 재리뷰 수정 완료 (2026-10-26)

- 유효 Access Token으로 logout을 먼저 시도하고, 실제 만료에서만 refresh·한 번 재시도한다.
- POST refresh IP 한도는 본문 바인딩 전 Filter에서 한 번 집계한다. 잘못된 JSON·길이초과도 301번째에429, 정상 형식은300회까지처리·301번째429다.
- 잔여20초·refresh503·logout204 조건20회에서 성공0→20회, 불필요한refresh20→0회. Backend58·Desktop96·PostgreSQL28·실제HTTP/Keychain33건 회귀와 독립 리뷰를 통과했다. 수치는 통제된 기능 검증이며 운영성공률이 아니다.
- 최신 dev d03c2d2 통합·문서 충돌 해소, 로그인 결정026/공유타입결정025 분리. PR #60의 push·CI·리뷰 스레드 결과를 확인하며 merge·Issue종료는 별도 승인받는다.

- 2026-10-28 진행 중: #58 위에 #86 Extension 연결 해제와 #85 온보딩 준비 상태를 순차 브랜치로 개발했다. 두 기능은 dev 미병합이며 실제 앱 통합 QA와 기기 목록 기반 복구 경로 검증이 남았다. #85는 Desktop별 화면 권한/제외 단계 marker와 연결·필수 동의로 상태를 계산한다.

- PR172의 9d131e9 CodeRabbit 재리뷰 actionable5건 중4건과 테스트 제안4건을 보완했다. 구형 counter 유실은 전역 번호/새 세션 복구 계약을 유지한다. OSIV를 켠 실제HTTP 경합 회귀와 PostgreSQL 경계2종·거절4종을 검증했다. 최신 QA·리뷰 문서를 따른다.

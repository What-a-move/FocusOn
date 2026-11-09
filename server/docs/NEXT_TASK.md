# Backend 다음 작업

## #182 실제 알림 서버 연결 — 최신 상태 (2026-11-09)

- `feat/182-server-intervention-policy`: 구현792d36f, 최신 dev0ca256e 통합c767669. 기존 개입 조회/ack에 context PUT/GET·receipt POST와 분석/heartbeat 선택 필드를 연결했다. V16 세션 연장/V17 개입/V18 연결이며 배포된 migration은 수정하지 않았다.
- 사용자 승인: #182 범위 확장, 전경 lease30초, 최초 SENT 수신부터 서버 cooldown5분, UNKNOWN/유실 생성 보류, Notion/계약 동기화. 확인했어 이후5분 UI 상태는 유지한다. 원문·토큰 비저장 및 기존 관측/보호 계약은 유지한다.
- Notion 새 API3행과 기존 분석/heartbeat/개입 조회·화면 분석 추가 정책을 반영했다. docs/API_CONTRACT.md·PROJECT_CONTEXT.md도 동기화했다. 아래 '미합의/빈 adapter'는 확장 전 이력이다.
- 전체 build·Checkstyle267개 실패0·생략14, 격리 PostgreSQL131개 실패0·생략0. 실제 임의 포트 HTTP와 독립 리뷰 포함. 기존 CORS preflight1회404는 단독/전체 재실행에서 재현되지 않았으며 원인 미확정으로 QA에 기록했다.
- 다음: PR 초안 검토·생성 승인 → 원격 CI/리뷰. Desktop/Extension/shared-types 담당의 context·표시 직전 보호·receipt 영속 재시도 연결 후 일반 모드 포키 실표시 QA. AI/학교 모델 실연결은 사용자 지시로 보류. 서버 구현 완료와 실제 앱 알림 완료를 구분한다.
- 상세: features/intervention-policy-integration-contract.md, -handoff.md, -QA.md, -code-review.md, -REPORT.md. 외부 담당자에게 메시지는 전송하지 않았다. PR 생성·병합·Issue 종료는 각각 별도 승인이다.

## 아래는 이전 진행 이력 — 위 최신 상태 우선


## 실제 알림 작업 재개 (2026-11-09)

- 사용자 지시로 AI 실연결 QA는 보류하고 실제 알림용 서버 연결 작업을 우선한다. 새 공개 계약/정책/범위 확장 검토안을 제시했으며 구체 승인 응답은 대기다. Frontend 코드는 직접 수정하지 않는다.
- 원격 dev 255085a: AI #190 및 세션 연장 #194 병합. 기존 체크리스트의 미병합 문구는 과거 기록이다. 실제 AI 네트워크/모델/앱 QA 완료를 뜻하지 않는다.
- dev의 V16 세션 연장과 충돌한 미배포 개입 migration을 V17로 조정한다. V16 데이터에서의 보존 회귀로 기준을 갱신한다. 이전 V15→V16/PG87 결과는 당시 번호로 실행한 이력이다.


## 학교 모델 연결 확인 후 진행 (2026-11-09)

- 학교 모델 IP 100.116.183.94를 받았으나 HTTPS443/HTTP11434 연결 시간 초과, 현재 경로 en0. 사용자가 11434 포트를 확인했으며 Tailscale 연결 확인 중이다. 학교 모델/실제 AI 성공으로 기록하지 않는다.
- AI PR #190 최신 d703397의 candidate 기준은 0.6이다. 아래 0.75 인계 필요 문장은 이전 head 당시 기록이다. Backend AI 주소/키와 실제 실행 AI 서비스는 추가 확인이 필요하다.
- 합성 검사 도구/명시 실행 Java live 검사 준비. loopback14개 통과, Backend build·Checkstyle 239개 실패0·생략12(실제 AI2개 포함). 상세는 features/analysis-ai-live-qa.md.
- 새 공개 계약 검토안: features/intervention-policy-integration-contract.md. 전경 context/receipt·lease·수신 기준 cooldown은 승인 전이며 구현하지 않았다. 기존 production 생성 보류는 유지한다.


## 최신 confidence 기준 변경 (2026-11-09)

- 사용자 최신 승인으로 0.75 → 0.6. 서버 numeric confidence/경계 회귀 변경·전체 build/Checkstyle 통과(234개 실패0·생략10). 이전 0.75 결정은 대체됐다.
- 공통 API 문서·Backend 규칙·Notion AI 분석 계약 4.4/7을 0.6으로 동기화했다. AI PR #190은 아직 0.75여서 AI 담당 수정 필요. 정확한 수정안은 features/intervention-policy-handoff.md. 아직 외부 담당자에게 전송하지 않음.
- 기존 null 베타 예외·metadata 비누적·보호·실제 앱 연동 보류는 그대로다.


## 시연 준비 변경 확인 (2026-11-09)

- 최신 원격 dev 4e212de: Desktop 시연 모드 추가(#191/#193). #182 실제 QA는 FOCUSON_DEMO=1을 끈 일반 실행으로 한다. demo는 interventions GET을 가로채며 일부 종료/리포트를 합성한다.
- AI #154 구현 PR #190 OPEN, 아직 dev의 정식 경로는 RECEIVED. 로컬 server/.env AI 주소/키 비어 있음. 실제 AI 왕복 미검증.
- #164 OPEN/Draft/CONFLICTING. 팀원 rebase/QA/승인 필요, 직접 수정하지 않음. 자세한 인계는 features/intervention-policy-handoff.md.


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


## 최신 우선 작업 (2026-11-09 KST)

1. PR #172는 dev `fc340ff`에 병합 완료다. #171은 종료 승인 대기 OPEN이므로 재구현·재PR하지 않는다. 아래 병합 전 기록은 당시 이력이다.
2. `features/intervention-policy-PLAN.md`의 1차 화면 OFF_TASK Policy Engine + 개입 조회/확인 API 2개를 검토한다. 기존 Server Policy/개입 Issue·PR은 발견하지 못했으며 새 Issue 생성 승인을 먼저 받는다. 아직 새 Branch·구현은 없다.
3. 기기 간 현재 화면 선택·서버 기준 구간 시간·rolling 구간 포함·실제 표시/cooldown·ack 수명 경계를 정본과 함께 확정하고 최종 구현 승인을 받는다. 기존 heartbeat #167은 최소 정책 연결만 검토한다.
4. #154 실제 AI 판정, Frontend 영속 observationSequence·표시/ack 흐름은 담당자 인계와 실제 연동 QA로 확인한다. 다른 영역 코드를 직접 수정하지 않는다.
5. BREAK_SUGGESTION·카메라 개입, 분석 이력·local-events, summary/달력/일간, 학습 정리·cleanup은 후속 단위로 나누며 담당·기존 Issue를 각각 다시 확인한다. #163/PR164·#168 등 세션 명령은 팀원 범위를 유지한다.
## Issue #168 세션 연장 후속

- 최신 dev `8a5933f`와 충돌을 해결하고 연장 migration을 V16으로 조정했다. 기존 개발 DB 복제본에서 V15 상태를 재현한 뒤 V16 적용 및 주요 데이터 보존을 확인했다.
- 실제 Google 로그인과 연장 HTTP 및 PostgreSQL 동시 요청을 확인했다. 접근 가능한 Notion API 원본 대조와 Swagger UI·Desktop 통합 검증은 남았다.
- 상세 구현과 검증은 `features/session-extend-REPORT.md`를 따른다.

## 현재 작업: #171 분석 요청·결과 저장

- feat/171-analysis-runs에서 구현·격리 정적 리뷰와 QA를 수행했다. 최신 검증 수치·한계는 features/analysis-runs-QA.md, REPORT.md를 따른다. PR #172 생성·초기 CI2개 성공 후 리뷰 지적을 수정했다. 수정 커밋의 CI·재리뷰를 확인하며 병합·이슈 종료는 별도 승인 대상이다.
- AI #154가 dev에 반영되면 정식 내부 경로의 판정·서비스 키·같은 traceId·실제 Desktop/Extension 입력으로 통합 QA한다. 현재 수신 확인 응답은 UNCERTAIN/ANALYSIS_UNAVAILABLE로 처리된다.
- 다음 사용자 담당6번은 Policy Engine·개입 생성/조회·확인, 이후7번 summary/일간/달력이다. heartbeat에서 판정할 수 있도록 팀원 #167과 호출 경계를 맞춘다. metadata UNRELATED 시간 제외와 기기별 source 무관 findCurrent 조회를 재사용한다.
- #151의 PR170은 병합·CI 성공이나 실제 Chrome QA는 남았다. #163 Draft PR164와 #167~#169 세션 상태 명령은 다른 담당 범위다. 실제 테스트 없이 완료로 닫지 않는다.

- 관측 순서 계약: 사용자 위임으로 선택 observationSequence·V15·구형 판정 보류/보호 유지를 구현했다. 현재 조회는 findCurrent만 사용한다. Frontend가 전용 인계에 따라 전역 counter를 추가한 뒤 실제 앱에서 시계 역행/재시작/혼합 요청을 QA한다. 실제 AI 연결·최신 원격CI/재리뷰도 별도 확인한다.

## Google 로그인 시간 오차 (#177)

`fix/177-google-login-clock-skew`에 #167이 병합된 최신 dev를 통합하고 실계정 로그인·인증 사용자 조회를 확인했다. PR 후 원격 CI와 리뷰를 확인한다. 이전 401과 시각 오차의 인과관계는 미확정이다.

## Issue #167 이어서 확인 (2026-11-08)

- PR #178 리뷰 수정 커밋을 푸시하고 재리뷰·원격 CI를 확인한다. 시계 역행과 이전에 처리되지 않은 지연 신호는 순번 없이 완전히 구분할 수 없다.
- 최신 dev 통합 후 격리 PostgreSQL의 V1~V13 적용과 Heartbeat 테스트를 검증했다. 사용자 개발 DB의 이전 Heartbeat V11 이력은 백업·복제 검증 후 데이터 보존 방식으로 해결했다. 다른 DB는 별도로 확인해야 한다. 상세는 `features/session-heartbeat-REPORT.md`.

## 직전 작업: #151

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

- PR172 CodeRabbit9d131e9 재리뷰 후 host·범위·fresh snapshot·테스트 보강을 push하고 원격CI를 확인한다. 기본OSIV=false는 유지한다. Counter 유실 복구는 전용 Frontend 인계대로 적용하고 실앱에서 확인해야 한다. 병합/이슈 종료는 별도 승인이다.

## Issue #195 세션 조회 (2026-11-09)

- feat/195-session-queries에서 상세·목록 조회를 구현했다. 실계정 HTTP와 PostgreSQL 검증 결과는 features/session-queries-REPORT.md를 따른다. Notion 원문 상세 대조와 앱 통합은 미완료다.

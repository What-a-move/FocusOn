# Backend 결정 기록

> Spring Boot 백엔드의 API·DB·인증 결정을 기록한다.
> 기존 결정을 지우지 않고 새로운 번호로 추가한다.

## 결정 001 - 백엔드 프레임워크

- 결정일: 2026-09-03
- 담당 영역: Backend
- 상태: 확정

### 결정 내용

백엔드는 Spring Boot와 Java를 사용한다.

### 결정 이유

REST API 기반으로 Desktop·Extension·AI를 연결하고 팀의 백엔드 기술 방향을 맞추기 위해서다.

## 결정 002 - 계층 책임 분리

- 결정일: 2026-09-03
- 담당 영역: Backend
- 상태: 제안

### 결정 내용

Controller는 요청·응답 처리, Service는 비즈니스 로직, Repository는 데이터 접근을 담당한다.

### 후속 확인

- [ ] 패키지 구조 확정
- [ ] 공통 응답 형식 확정
- [ ] 예외 코드 목록 확정
- [ ] API 문서화 방법 확정

## 결정 003 - 공통 응답 형식 및 Authorization 헤더 (결정 010으로 변경)

- 결정일: 2026-09-12
- 담당 영역: Backend
- 상태: 변경됨
- 관련 Issue: #4

### 결정 내용

응답 형식은 루트 `docs/API_CONTRACT.md` 기준(`success`/`data`/`message`, 최상위 `code` 없음)을 그대로 따른다. 인증이 필요한 API는 `Authorization: Bearer {token}` 헤더를 사용한다. 상세는 `server/docs/DEVELOPMENT_RULES.md` BE-001, BE-004 참고.

### 결정 이유

당시 사용하던 Notion `FocusOn API 데이터베이스`의 일부 응답 예시에 최상위 `code` 필드가 섞여 있어 클라이언트마다 다르게 파싱할 위험이 있다고 판단했다.

### 고려한 대안

- Notion 예시대로 최상위 `code`를 표준으로 채택 — 루트 `docs/API_CONTRACT.md`를 다시 바꿔야 하고 이미 이 형식을 참고 중인 다른 영역과 재조율이 필요해 기각.

### 영향받는 API·Entity

- 모든 REST 응답 (공통 형식)

### 후속 확인

- [ ] Desktop·Extension·AI 담당자에게 공유하고 이견 확인
- [ ] Notion API 데이터베이스의 `code` 필드 예시 정리

최신 Notion 명세가 확정되면서 이 형식은 사용하지 않는다. 결정 010을 따른다.

## 결정 004 - 공통 ErrorCode 초안 (결정 010으로 변경)

- 결정일: 2026-09-12
- 담당 영역: Backend
- 상태: 변경됨
- 관련 Issue: #4

### 결정 내용

`INVALID_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR` 6개를 공통 ErrorCode 초안으로 둔다. 상세는 `server/docs/DEVELOPMENT_RULES.md` BE-002 참고.

### 결정 이유

Notion `ErrorCode` 페이지가 비어 있어 모든 API 명세가 실체 없는 "공통 ErrorCode 처리"만 참조하고 있었다.

### 후속 확인

- [ ] Notion `ErrorCode` 페이지에 동일 내용 반영
- [ ] 기능별 추가 코드 발생 시 이 결정에 이어서 기록

Notion `공통 Error Code`가 확정되었으므로 이 초안은 사용하지 않는다.

## 결정 005 - AUTH_001(구글 로그인) 응답 스펙

- 결정일: 2026-09-12
- 담당 영역: Backend
- 상태: 폐기 (Notion API 계약으로 대체)
- 관련 Issue: #4

### 결정 내용

이 기록의 미정 초안은 더 이상 사용하지 않는다. Google 로그인 교환의 요청·응답은 Notion `FocusOn API 명세서`의 `POST /api/v1/auth/google/exchange` 계약을 기준으로 한다. 로컬 엔드포인트 요약은 `docs/API_CONTRACT.md`를 함께 갱신한다.

## 결정 006 - notifications.triggerSource

- 결정일: 2026-09-12
- 담당 영역: Backend
- 상태: 폐기
- 관련 Issue: #4

### 결정 내용

미정. API 응답의 `triggerSource` 필드가 ERD `notifications` 테이블 컬럼에 없다. 컬럼을 추가할지, 다른 필드 조합으로 응답 시점에 계산할지 확인이 필요하다.

최신 MVP API 명세에 이 요구가 없으므로 현재 구현 범위에서 제외한다.

### 후속 확인

- [ ] ERD `notifications` 테이블 컬럼 추가 여부 결정

## 결정 007 - REPORT_001 필드

- 결정일: 2026-09-12
- 담당 영역: Backend
- 상태: 해결됨
- 관련 Issue: #4

### 결정 내용

미정. 두 필드가 ERD `session_reports` 테이블 컬럼에 없다. 저장값인지, 조회 시점에 다른 데이터로 집계하는 값인지 확정이 필요하다.

리포트 필드는 최신 Notion의 일간·세션 요약·학습 정리 행을 그대로 따른다.

### 후속 확인

- [ ] 저장 vs 집계 여부 결정 후 ERD·API 문서 갱신

## 결정 008 - 목표 수정 응답

- 결정일: 2026-09-12
- 담당 영역: Backend
- 상태: 해결됨
- 관련 Issue: #4

### 결정 내용

미정. `GOAL_004`(목표 수정) 응답 필드가 `GOAL_002`/`GOAL_003`과 다르다. 수정된 필드만 부분 응답할지, 전체 목표 객체를 반환할지 확정이 필요하다.

최신 계약은 `PATCH /api/v1/goals/{goalId}` Notion 행을 따른다.

### 후속 확인

- [ ] 부분/전체 응답 여부 결정

## 결정 009 - 수정 API의 HTTP 메서드

- 결정일: 2026-09-12
- 담당 영역: Backend
- 상태: 해결됨
- 관련 Issue: #4

### 결정 내용

미정. `USER_004`는 PUT, `GOAL_004`는 PATCH를 사용 중이다. 전체 필드 교체는 PUT, 부분 수정은 PATCH로 통일할지 기준 확정이 필요하다.

사용자 설정·프로필은 PUT, 목표 일부 수정은 PATCH로 최신 Notion 행에 확정되었다.

### 후속 확인

- [ ] 수정 API 전체에 적용할 HTTP 메서드 기준 확정 후 `docs/API_CONTRACT.md` 반영

## 결정 010 - API 계약 원본과 공통 응답

- 결정일: 2026-09-21
- 상태: 확정

### 결정 내용

Notion `FocusOn API 명세서`를 엔드포인트 계약의 원본으로 사용한다. 성공은 `{ "data": ... }`, 오류는 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details`를 최상위에 둔다. 폐기된 예전 `API 명세`는 사용하지 않는다.

## 결정 011 - 세션 명령과 Heartbeat

- 결정일: 2026-09-21
- 상태: 확정

### 결정 내용

세션 생성·일시정지·재개·연장·종료는 `Idempotency-Key`를 사용하고 기존 세션 상태 명령은 `If-Match-Version`도 사용한다. Heartbeat에는 두 헤더를 사용하지 않으며 10초 주기로 보낸다. 무신호 60초는 `ABANDONED` 후보 기준이다. 현재 RUNNING 구간의 첫 유효 heartbeat 전에는 `lastResumedAt`(최초 시작은 `startedAt`)을 기준으로 삼고, 마지막 유효 신호 뒤 총 3분(180초)까지 복구되지 않으면 마지막 서버 확정 누적 시간과 `HEARTBEAT_TIMEOUT` 사유로 `ABANDONED` 자동 종료한다. 그 전에 신호가 복구되면 후보를 해제한다.

## 결정 012 - MVP와 P1

- 결정일: 2026-09-21
- 상태: 확정

### 결정 내용

`GET /api/v1/sessions`와 `GET /api/v1/reports/weekly`는 P1으로 유지하고 나머지 확정 API를 MVP로 구현한다.

## 결정 013 - PostgreSQL과 migration

- 결정일: 2026-09-21
- 상태: 확정

### 결정 내용

PostgreSQL 17을 Docker Compose로 실행하고 Flyway로 스키마를 관리한다. 애플리케이션과 DB 시간대는 UTC를 사용한다.

## 결정 014 - 제외 도메인·개입 정책·학습 정리

- 결정일: 2026-10-12
- 상태: 확정

개입 임계값·UNCERTAIN 처리의 최신 기준은 결정 018로 변경됐다. 아래 내용은 2026-10-12 당시 결정 이력이다.

### 결정 내용

- 분석 제외 목록은 사용자별 별도 저장 모델로 관리하고 정규화한 `host`만 저장한다. 온보딩에서 빈 목록을 허용하며 설정 API의 `PUT`은 전체 교체 방식으로 동작한다.
- 개입 정책 버전은 세션 시작 시 고정하고 분석 기록과 개입 이벤트에 함께 저장한다. `UNCERTAIN`은 30초까지 기존 이탈 구간을 보류하고 초과 시 종료하며, `RELATED`가 확인되면 기존 이탈 구간과 카메라 알림 후보를 즉시 초기화한다. 15초 미만의 짧은 화면 전환은 이탈 구간으로 집계하지 않는다.
- 학습 정리는 `overview`, `keyConcepts`, `nextStartPrompt`만 제공한다. 자동 복습 질문과 퀴즈는 MVP에서 제외한다.
- 집중 달력은 `studyDate`와 종료 확정 세션의 검증된 누적 시간을 사용하고, 하루 `60,000ms` 이상이면 활동일로 표시한다.

### 영향받는 API·Entity

- `GET`, `PUT /api/v1/users/me/analysis-excluded-domains`
- `GET /api/v1/reports/calendar?month=YYYY-MM`
- `user_analysis_excluded_domains`, `learning_sessions`, `analysis_runs`, `intervention_events`, `learning_review_evidences`, `learning_summaries`

## 결정 015 - 첫 migration의 인증 범위

- 결정일: 2026-10-19
- 주제: 로그인용 PostgreSQL 저장 모델
- 상태: 확정
- 결정 내용: Flyway V1은 인증에 필요한 `users`, `oauth_identities`, `user_devices`, `token_families`, `refresh_token_records`로 범위를 한정한다. 기기당 활성 token family 최대 1개와 family당 활성 Refresh Token record 최대 1개는 PostgreSQL 부분 유니크 인덱스로 보장한다. 목표·세션 등 다른 도메인은 후속 migration에서 추가한다.
- 결정 이유: 첫 로그인 작업에 필요한 저장 모델을 하나의 Issue에서 ERD와 대조하고, 동시 로그인·토큰 회전의 중복 활성화를 DB에서 막기 위해서다.
- 영향받는 API·Entity: 인증 저장 모델; 공개 API 계약 변경 없음.
- 관련 Issue·PR: [#32](https://github.com/What-a-move/FocusOn/issues/32), [PR #35](https://github.com/What-a-move/FocusOn/pull/35).

## 결정 016 - 인증 저장 모델 리뷰 반영

- 결정일: 2026-10-19
- 주제: Token Family 소유 관계와 Google 계정 조회
- 상태: 확정
- 결정 내용: 로컬 DB에 적용된 V1은 그대로 두고 V2에서 `token_families(device_id, user_id)`가 같은 사용자 소유의 `user_devices(id, user_id)`를 참조하도록 복합 FK를 추가한다. Google 계정의 `(provider, provider_subject)` 전체 유일성은 Notion ERD대로 유지한다. 로그인 조회 Repository는 `unlinked_at IS NULL` 행만 반환한다.
- 결정 이유: 서로 다른 사용자와 기기를 묶은 Token Family를 DB에서 막고, 연결 해제 이력이 로그인 대상으로 조회되지 않게 한다. Google 계정 해제·재연결 API는 MVP의 현재 범위에 없으므로 이 Issue에서 활성 행만 유일하게 만드는 정책 변경은 하지 않는다.
- 후속 확인: Google 계정 해제·재연결 API를 도입할 때 기존 identity 재활성화·계정 이전·이력 보존 정책을 별도 Issue에서 확정한다.
- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32).

## 결정 017 - Frontend 온보딩·Extension 연결 계약 수용

- 결정일: 2026-10-24
- 상태: 확정 (사용자 공유·최종 정정, 관련 Notion·Frontend 코드 대조)
- 결정 내용: 동의 응답은 `currentVersion`과 `consents[{consentType,isGranted,consentVersion,grantedAt,withdrawnAt}]`를 사용하고 현재 문서 버전을 그대로 저장 요청에 보낸다. 닉네임은 저장 시 409로 중복을 판단한다. Extension 연결은 토큰 3개 필드와 `deviceId`, 공개 `deviceType: EXTENSION`을 반환하며 내부 `CHROME_EXTENSION`은 유지한다. 코드 오입력 1~2회는 400 `LINK_006_CODE_MISMATCH`와 숫자 `remainingAttempts`, 3번째는 410이다. 400 입력 오류의 자동 재전송은 금지하고 `retryable: false`로 정리한다.
- 권한·범위: 알림은 Desktop 필수 확인이지만 readiness API·DB에 새 알림 필드를 추가하지 않는다. MVP는 제외 도메인만 저장한다. 3초 polling은 기본 20회/분이며 다른 인증 요청·재시도와 60회/분을 공유한다.
- 적용 범위: 계약과 구현 계획에 반영한다. 실제 pairing·동의 HTTP API는 아직 구현 전이며, 로그인 Issue와 팀원 Issue #38의 담당 범위를 혼합하지 않는다.
- 남은 계약 경계: 틀린 코드의 대상 record를 식별할 시도 문맥과 세션 시작에서의 알림 준비 신호는 구현 전 명확히 한다. 상세는 `docs/NOTION_SYNC_AUDIT.md`.

## 결정 018 - 개입 v1·시간대·리포트 형식

- 결정일: 2026-10-24
- 상태: 확정 (사용자 공유·최종 정정, 최신 화면 분석 5.2·5.3 및 리포트 3.3·5.3 대조)
- 개입: `docs/API_CONTRACT.md`의 `v1` 연속/반복 OR 표를 따른다. 이전 120초 기준과 UNCERTAIN 30초 보류 규칙은 새 정책에서 사용하지 않는다. `초과`는 엄격한 `>`이며 UNCERTAIN은 연속 구간을 끊는다. 숫자는 서버가 관리하고 `interventionPolicyVersion`은 세션 생성 때 `v1`로 고정한다.
- 알림: 실제 전송부터 세션·유형별 5분 cooldown을 적용한다. `확인했어` 뒤 5분은 Client UI의 재집중 상태이며 서버 상태나 AI 판정을 바꾸지 않는다.
- 시간: 서버 UTC 시각과 Desktop monotonic 누적값의 서버 검증을 함께 사용한다. 세션 시작의 기기 IANA `timeZone`을 snapshot으로 고정하고 잘못된 값·누락은 `Asia/Seoul`로 처리한다. IP로 추정하지 않는다. `study_date`는 최초부터 NOT NULL이며 시작일 귀속을 바꾸지 않는다. 출시 전 세션 모델의 백필은 하지 않는다.
- 리포트: 학습 정리 `keyConcepts`는 `name`, `briefExplanation`, `sessionEvidence` 객체 최대 5개이고 서로 다른 근거 2개 미만이면 `INSUFFICIENT_DATA`다. 일간 `sessions[]`는 `sessionId`, `goalTextSnapshot`, `startedAt`, `endedAt`, `accumulatedActiveMs`를 사용한다.
- 근거와 제한: Notion AI 분석 계약 7장에는 이전 규칙이 남아 있고 일간 API에는 배열 내부 필드가 생략돼 있다. 이번 사용자 확정 기준을 로컬에 반영하되 Notion이나 미구현 정책을 구현 완료로 표시하지 않는다. 순환 window의 구간 포함 시각 기준도 구현 전 확정한다.

## 결정 019 - 목표 확인·MVP 세션 생성 최신 계약

- 결정일: 2026-10-25
- 상태: 확정 계약 반영 (사용자 공유 및 현재 Notion API 대조)
- 목표 확인: 첫 요청은 previousAnswers 빈 배열·대화 ID 생략, 응답은 서버 대화 ID 필수. 이후 같은 ID·질문 답을 보내며 서버는 소유권·유효성을 확인한다. AI 일시 실패 재시도는 Backend가 1초 → 3초 최대 2회 처리하고 질문 수·턴을 중복 확정하지 않는다.
- 세션 생성: 5~300분의 1분 단위 targetDurationMs를 필수로 받고 null을 거절한다. 이전 무기한 세션 유지 결정을 현재 MVP에 적용하지 않는다. sessionOptions는 개입 강도·중복 없는 알림 채널·카메라 사용값이고 breakSuggestionEnabled는 제외한다.
- 권한: startReadiness에는 screenAnalysisReady만 받는다. 알림은 Desktop의 요청 전 확인 책임이며 서버는 NOTIFICATION_PERMISSION을 반환하지 않는다. 결정 017의 알림 신호 미정 부분은 이 결정으로 대체한다.
- 이전 기록 정정: 결정 018의 일간 배열 항목 누락은 현재 Notion에서 해결됐다. 목표 시간 null 허용이 남은 ERD·세션 정책은 문서 동기화 대상이다.
- 로그인 연동: 만료 JWT와 위조 JWT의 구분은 구현 예정 인증 필터의 필수 조건이다. Access JWT 내부 서비스·인증 저장 모델은 HTTP 로그인 완료를 뜻하지 않는다. family 만료 연장 방식은 후속 refresh 구현 전 검토 항목이며 무기한 유지로 확정하지 않는다.
- 영향: `docs/API_CONTRACT.md`, `PROJECT_CONTEXT.md`, `NOTION_SYNC_AUDIT.md`, Backend 개발 규칙·로그인 PLAN. 코드·SQL·Notion 변경 없음.

## 결정 020 - 로그인 요청 인증 구현 경계

- 결정일: 2026-10-25
- 상태: 구현 반영 (Issue #49, dev 병합 전)
- 내용: Google 외부 통신은 DB 잠금 밖에서 수행한다. 계정 최초 생성 경쟁은 DB unique와 별도 transaction 재시도로 처리하고 기기 한도·family 교체는 사용자 잠금 안에서 처리한다.
- 요청 인증은 JWT와 DB 사용자·기기·family 소유·활성·만료를 함께 검사한다. 만료 예외는 서명·필수 클레임을 통과한 경우에만 분리한다.
- 한도: 단일 서버의 유한 메모리 fixed window를 사용하고 익명·인증 공간을 분리한다. PR #51 리뷰 보강으로 IP별 300회/10분 보호 상한을 먼저 검사한 뒤 기존 IP+installationId별 5회/10분을 검사한다. 공유 NAT를 고려한 내부 남용 방어이며 다중 IP 공격 전체를 차단하는 장치는 아니다. 포화는 429로 거절한다. 배포 proxy를 구성하기 전에는 forwarded header를 신뢰하지 않는다.
- 개인정보: Google Desktop secret은 서버 로컬 환경에만 설정한다. DTO 문자열과 로그·오류에 인증 원문을 노출하지 않는다.
- 저장 모델: V1·V2는 수정하지 않고 기존 provider+subject UNIQUE를 JPA metadata에도 명시한다.
- 후속: refresh·logout·family 만료 연장·기기 관리·pairing과 실제 Google 계정 왕복은 이번 구현 결과와 구분한다.

## 결정 021 - FocusOn 로그인 수명주기 완성

- 결정일: 2026-10-26 / Issue #59, 사용자 PLAN 승인.
- refresh 성공 시 token·family의 expiresAt을 현재부터 30일로 같이 연장한다. 최초 로그인 기준 고정 30일이 아니며, 30일 동안 유효 갱신이 없으면 재로그인한다.
- ROTATED·REVOKED token 재사용 시 해당 family만 폐기한다. transaction에서 폐기를 commit한 뒤 오류 응답을 반환해 rollback으로 무효화되지 않게 한다.
- 잠금은 사용자→family 순서이며 같은 token 동시 갱신은 1회 성공 뒤 reuse 폐기로 처리한다. Client single-flight 유지, 재전송 grace는 도입하지 않는다.
- 현재 Desktop logout은 연결 Extension까지 폐기하지만 다른 Desktop은 유지한다. 기기 상태를 LOGGED_OUT/REVOKED로 바꿔 재인증·기기 슬롯에 반영한다.
- refresh 공개 IP 보호 300회/10분은 별도 유한 공간, 확인된 token 소유 user/device는 일반 60회/분 한도를 공유한다. proxy 신뢰 경계·단일 서버 메모리 한계는 기존 환경 문서를 따른다.
- TokenFamilyGuard로 요청 인증과 refresh의 부모 Desktop 활성 family·수명·소유 검사를 통일한다.
- Notion 원문은 이번 작업에서 수정하지 않았으며, 새 30일 연장·한도 세부값은 최신 사용자 승인 결정이다. 정본 문서 동기화 시 이 결정과 일치시킨다.
- #58 pairing·기기 관리·unlink 및 실제 Google 실계정 성공 QA는 별도로 추적한다.


## 결정 027 - 개발용 Swagger 접근과 PKCE 테스트 도구

별도 swagger 프로필 활성화 방식은 결정029로 변경됐다. 아래는 최초 결정 이력이다.

- 결정일: 2026-10-27 / Issue #79 범위·구현 사용자 승인.
- 기본 local·docker는 OpenAPI·Swagger UI를 끈다. 개발자가 `local,swagger` 프로필로 명시적으로 켜면 서버를 IPv4 loopback에 바인딩하고 문서 GET 경로만 별도 체인에서 허용한다. 제품 API의 Bearer·DB 인증은 기존 체인을 유지한다.
- OpenAPI 전역 HTTP Bearer는 FocusOn Access JWT를 받는다. exchange·refresh는 명시적 빈 security 목록으로 표시하며 Google OAuth 원클릭 기능으로 오인하지 않게 한다. 인증 영구 저장·외부 validator·기본 Petstore 문서는 사용하지 않는다.
- Python 표준 라이브러리 로컬 도구로 Desktop Client ID·PKCE S256·state·임시 loopback을 사용한다. callback을 검증한 뒤 미사용 code를 담은 0600 JSON 파일만 만든다. Google token endpoint 호출·Client Secret 출력·임의 FocusOn 토큰 발급을 하지 않는다.
- `common/openapi` 패키지는 인증 비즈니스 로직과 문서 전용 보안·OpenAPI 모델의 책임을 분리하기 위해 추가한다. 공개 API·DB·토큰 수명·Frontend 변경은 없다.
- 실계정 Google 성공·Desktop UI 검증은 오프라인/합성 QA와 구분한다. PR 생성·merge·Issue 종료는 별도 승인한다.
- 사용법: `SWAGGER_TESTING.md`; 계획·리뷰·QA는 `features/swagger-testing-*`를 따른다.


## 결정 028 - 개발용 .env 자동 로딩

- 결정일: 2026-10-27 / 사용자 명시 요청, Issue #79 추가 범위.
- local 프로필의 `spring.config.import`로 `.env`를 Properties 형식으로 읽는다. `server/`에서 실행하면 `./.env`, 저장소 루트면 `./server/.env`를 사용하며 두 파일이 있으면 후자가 우선한다. OS 환경변수·JVM 옵션·실행 인자는 파일보다 우선한다.
- `optional:`은 파일이 없어도 IDE/OS 환경변수로 실행할 수 있게 하기 위한 것이다. DB password·JWT 등 기존 필수 설정 검증을 없애지 않는다. `.env`는 Git 및 jar에 포함하지 않는다.
- `.env.example`의 단순 KEY=VALUE 형식을 공유한다. 값 바깥 따옴표·export·shell 구문을 지원한다고 표현하지 않는다. Spring 내부 설정 소스로 읽으므로 별도 Python 프로세스의 shell 환경으로 export되지 않는다.
- local DB 포트는 `POSTGRES_PORT` → `POSTGRES_HOST_PORT` → 5432 순서로 선택한다. docker 프로필은 기존 Compose 환경변수 전달 방식을 유지한다.
- 이 결정 당시 Swagger 활성화 방식은 결정027을 유지했다. 이후 사용자가 local 통합을 승인해 현재는 결정029를 따른다.


## 결정 029 - local에서 Swagger 자동 활성화

- 결정일: 2026-10-27 / 사용자 명시 승인, Issue #79 보완.
- Swagger 속성을 application-local.properties에 통합하고 application-swagger.properties를 삭제한다. Java 문서 설정의 Profile도 local로 변경한다. 기본 local은 `.env`를 읽고 loopback에 바인딩하며 UI·OpenAPI를 자동으로 연다. DB가 켜져 있으면 bootRun만으로 개발한다.
- 공통 설정의 springdoc false를 유지해 docker/test 실행에서는 Swagger를 열지 않는다. Docker 자체가 운영 전용이라는 의미가 아니라 현재 팀이 정한 실행 구성이다.
- GET 문서 전용 체인·제품 API 인증·공개 exchange/refresh 예외·Bearer UI·PKCE 도구·환경변수 우선순위는 유지한다.
- 검증: local 문서+인증 통합, docker 비공개, 명시적 disable, 실제 추가 인자·source 없는 bootRun. PR·merge·Issue 종료는 별도 승인한다.

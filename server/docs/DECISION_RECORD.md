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

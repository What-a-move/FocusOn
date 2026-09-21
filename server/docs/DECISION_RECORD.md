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
- 상태: 해결됨
- 관련 Issue: #4

### 결정 내용

미정. Notion `AUTH_001` 응답 스펙이 비어 있다. Google 로그인 성공 시 반환할 필드(FocusOn 토큰, 만료 시간, 최초 로그인 여부 등)를 확정해야 한다.

최신 계약은 `POST /api/v1/auth/google/exchange` Notion 행에서 관리한다.

### 후속 확인

- [ ] AUTH_001 응답 필드 확정
- [ ] Notion 및 `docs/API_CONTRACT.md`에 반영

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

세션 생성·일시정지·재개·연장·종료는 `Idempotency-Key`를 사용하고 기존 세션 상태 명령은 `If-Match-Version`도 사용한다. Heartbeat에는 두 헤더를 사용하지 않으며 10초 주기와 60초 무신호 기준을 적용한다.

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

## 새 결정 기록

### 결정 014

- 결정일:
- 주제:
- 상태: 제안 / 확정 / 변경됨 / 폐기
- 결정 내용:
- 결정 이유:
- 영향받는 API·Entity:
- 관련 Issue·PR:

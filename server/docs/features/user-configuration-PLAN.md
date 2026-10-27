# 사용자 프로필·동의·집중 설정·분석 제외 도메인 구현 계획

> 상태: Issue #38의 범위를 사용자 설정 API로 축소했다. 구현 시작 전 계획 문서다.

## 기본 정보

- 기능 ID: `user-configuration`
- 작성일: 2026-10-26
- 관련 Issue: [#38](https://github.com/What-a-move/FocusOn/issues/38)
- Branch: `feat/#38-user-configuration`
- Labels: `area:server`, `type:feature`, `priority:mvp-core`
- 기준: Notion `FocusOn API 명세서`, `docs/API_CONTRACT.md`, 공통 오류 정책
- 선행 작업: PR #60이 `dev`에 병합된 최신 기준에서 브랜치를 생성했다.

## 목적과 범위

인증된 사용자가 자신의 프로필, 동의, 집중 설정, 분석 제외 도메인을 조회·저장하게 한다. 모든 조회와 변경은 인증 principal의 사용자 소유 범위에서만 처리한다.

이번 작업의 API는 다음과 같다.

```text
GET /api/v1/users/me
PUT /api/v1/users/me/profile
GET /api/v1/users/me/consents
PUT /api/v1/users/me/consents/{consentType}
GET /api/v1/users/me/focus-settings
PUT /api/v1/users/me/focus-settings
GET /api/v1/users/me/analysis-excluded-domains
PUT /api/v1/users/me/analysis-excluded-domains
```

## 계약과 구현 기준

- `GET /users/me`는 현재 인증 사용자의 `userId`, `nickname`, `onboardingStatus`만 반환한다.
- 프로필 수정은 현재 사용자만 대상으로 하며, 닉네임 형식과 중복을 검증한다.
- 동의 조회에는 `currentVersion`과 동의별 `consentType`, `isGranted`, `consentVersion`, `grantedAt`, `withdrawnAt`을 반환한다. 동의 수정은 명세의 `consentType`과 현재 문서 버전을 검증한다.
- 집중 설정은 `interventionLevel`, `notificationChannels`, `breakSuggestionEnabled`, `cameraAnalysisEnabled`를 전체 저장·반환한다.
- 분석 제외 도메인은 현재 사용자의 전체 목록을 교체한다. host는 소문자, IDNA ASCII, 후행 점 제거 기준으로 정규화하고, scheme·port·path·query·fragment·페이지 원문은 저장하지 않는다.
- 분석 제외 도메인은 사용자당 최대 50개다. 빈 배열은 전체 삭제이며, 조회·수정에는 `Authorization`과 `X-Device-Id`를 요구한다.
- `X-Device-Id`가 필요한 API는 `FocusOnPrincipal`의 기기와 헤더 값의 일치를 공통 검증기로 확인한다.
- 인증 필요 API의 기본 요청 제한은 `userId + deviceId` 기준 분당 60회이며, 초과 시 `429 TOO_MANY_REQUESTS`와 `Retry-After`를 반환한다.
- Swagger UI는 Bearer Access Token 입력을 지원하고, 인증·검증 오류는 공통 `ApiErrorResponse` 본문 스키마로 문서화한다.
- Entity를 응답으로 직접 반환하지 않고 요청·응답 DTO, Service, Repository 책임을 분리한다.

## 제외 범위

- `GET`, `PUT /api/v1/users/me/onboarding-readiness`: Extension pairing #58 완료 뒤 별도 이슈에서 구현한다.
- 목표 명확화와 목표 CRUD: 별도 목표 API 이슈에서 구현한다.
- Google 로그인, Refresh Token, 로그아웃, 기기 목록·폐기, 연결 코드: 인증·기기 담당 범위다.
- 세션, 분석·개입, 리포트·학습 정리: 별도 이슈 범위다.

## 구현 순서

1. 최신 Entity·Migration·인증 principal·공통 응답 구조를 확인하고 필요한 사용자 설정 저장 모델을 설계한다.
2. Flyway migration, Entity, Repository를 추가하고 DB 제약 조건을 정한다.
3. 프로필, 동의, 집중 설정 API를 요청·응답 DTO와 Service로 구현한다.
4. 분석 제외 도메인 정규화·전체 교체·기기 헤더 검증을 구현한다.
5. 소유권, 유효성, 중복 닉네임, 도메인 경계, 요청 제한을 테스트한다.
6. PostgreSQL과 HTTP QA를 수행하고 ERROR·REPORT 문서를 실제 결과로 갱신한다.

## 완료 조건

- [x] 8개 API의 경로, 요청·응답, 상태 코드, 필수 헤더를 구현했다.
- [x] 닉네임 형식·중복 오류를 공통 오류 형식으로 연결했다.
- [x] 동의·집중 설정·분석 제외 도메인을 사용자별로 분리해 저장한다.
- [x] 분석 제외 도메인의 정규화, 50개 제한, 빈 배열 전체 삭제를 구현했다.
- [x] `X-Device-Id`가 필요한 요청은 현재 인증 기기와 일치해야 한다.
- [x] 사용자 설정 API 전용 MockMvc 정상·권한·도메인 정규화 테스트를 통과했다.
- [x] Swagger Bearer 보안 설정과 공통 오류 응답 스키마를 OpenAPI JSON으로 검증했다.
- [ ] PostgreSQL·실행 서버 HTTP QA를 통과한다.
- [x] 기존 Backend 전체 테스트와 Checkstyle을 통과한다.
- [ ] `user-configuration-ERROR.md`, `user-configuration-REPORT.md`, `CONTEXT.md`, `NEXT_TASK.md`를 실제 상태로 갱신한다.

## 예상 변경 파일

```text
server/src/main/resources/db/migration/V{next}__user_configuration.sql
server/src/main/java/com/capstone/backend/user/...
server/src/test/java/com/capstone/backend/user/...
server/docs/features/user-configuration-PLAN.md
server/docs/features/user-configuration-ERROR.md
server/docs/features/user-configuration-REPORT.md
server/docs/CONTEXT.md
server/docs/NEXT_TASK.md
server/docs/DECISION_RECORD.md
docs/API_CONTRACT.md
```

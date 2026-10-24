# Backend 작업 컨텍스트

## 담당 영역

- Spring Boot REST API
- 인증, 사용자, 기기 연결, 목표, 세션, 분석 이벤트, 리포트
- Desktop·Extension·AI 연동
- PostgreSQL 스키마와 Flyway migration

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

### 다음 구현 범위

2026-10-25 최신 `dev`(`0b88bb8`)와 Desktop 인증·목표 파서를 대조했다. MVP 세션 null 거절, 목표 대화 필드·Backend 재시도, Desktop 알림 확인 책임을 로컬 계약에 반영했다. 로그인 PLAN에는 만료 JWT 구분과 후속 family 만료 검토를 보강했다. 실제 로그인 API 구현 전 상태는 유지한다.

- Google 로그인 교환·인증 사용자 조회 기획서 초안의 검토와 Issue 생성 승인을 받는다. 목표·세션 Entity는 후속 migration으로 분리한다.
- Desktop Authorization Code + PKCE 기반 Google 교환과 인증된 사용자·기기를 Access JWT 서비스에 연결한다.
- MVP API를 도메인 단위로 구현

### 미구현

- 도메인 HTTP Controller·Service와 요청 인증 필터. 인증 Entity·Repository 5개는 이미 구현돼 있다.
- 인증·사용자·기기 연결 API와 Refresh Token 수명주기
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

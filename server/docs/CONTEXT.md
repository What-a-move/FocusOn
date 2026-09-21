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
- Notion `FocusOn API 명세서` 39개 엔드포인트 및 공통 정책 확정
- 로컬 API 계약과 Backend 불변 규칙 최신화

### 다음 구현 범위

- 공통 응답·예외·requestId 처리
- 사용자·기기·목표·세션 Entity 및 V1 migration
- Google ID token 교환과 FocusOn token 발급
- MVP API를 도메인 단위로 구현

### 미구현

- Controller, Service, Repository, Entity
- 인증·사용자·기기 연결 API
- 목표·세션·분석·리포트 API
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

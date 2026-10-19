# 인증 저장 모델 기획서 (검토 초안)

> 현재 상태: 승인됨. Issue #32 기준으로 구현한다. 구현 전에는 이 문서와 Notion ERD를 다시 대조한다.

## 기본 정보

- 기능명: 로그인용 PostgreSQL 저장 모델
- 기능 ID: `auth-persistence`
- 작성일: 2026-10-19
- 우선순위: MVP - 핵심
- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32)
- 예정 Branch: `feat/#32-auth-persistence`
- 관련 Client·AI 문서: Desktop Google 로그인 Issue #22 (연동 대상, 이번 작업 범위 밖)

## 기능 목적과 범위

Google 로그인, 기기 식별, Refresh Token 회전을 구현할 수 있도록 인증 도메인의 영속 저장 기반을 만든다. PostgreSQL 17과 Flyway 설정은 이미 있으므로 새 DB 제품이나 별도 설치는 필요하지 않다.

이번 작업은 `V1__create_initial_schema.sql`과 이에 대응하는 JPA Entity·Repository까지만 다룬다. HTTP 엔드포인트, Google token endpoint 연동, Access JWT 필터, Refresh Token 발급·회전, 기기 연결 API는 후속 Issue에서 구현한다. 목표·세션·분석·리포트 테이블도 후속 migration으로 나눈다.

## API 정의

- 새 Endpoint: 없음
- 공개 요청·응답 변경: 없음
- 후속 구현이 사용할 정본 계약: `POST /api/v1/auth/google/exchange`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`
- 설계 판단: 저장 모델 작업이므로 API 메서드·권한·오류를 임의로 확정하거나 변경하지 않는다.

## 데이터 설계

| 테이블 | 이 작업에서 저장할 핵심 값 | 핵심 관계·제약 |
| --- | --- | --- |
| `users` | `id`, `nickname`, `onboarding_status`, `user_time_zone`, `created_at`, `updated_at`, `deleted_at` | `id BIGINT` PK; 닉네임은 값이 있을 때 유일; 온보딩 상태는 `INCOMPLETE`, `COMPLETED` |
| `oauth_identities` | `id`, `user_id`, `provider`, `provider_subject`, `linked_at`, `unlinked_at` | `users` FK; `(provider, provider_subject)` 유일; `provider=GOOGLE` |
| `user_devices` | `id`, `user_id`, `device_type`, `installation_id_hash`, `display_name`, `linked_desktop_device_id`, `status`, `last_used_at`, `revoked_at`, `created_at` | `users` FK; Desktop 연결 FK; `(user_id, device_type, installation_id_hash)` 유일; 기기 종류 `DESKTOP`, `CHROME_EXTENSION`; 상태 `ACTIVE`, `REVOKED`, `LOGGED_OUT` |
| `token_families` | `id`, `user_id`, `device_id`, `status`, `revoked_reason`, `expires_at`, `revoked_at`, `created_at` | 사용자·기기 FK; `ACTIVE`, `REVOKED`, `EXPIRED`; 종료 사유 `LOGOUT`, `UNLINK`, `TOKEN_REUSE`, `DEVICE_REVOKED` |
| `refresh_token_records` | `id`, `token_family_id`, `token_hash`, `parent_token_id`, `status`, `issued_at`, `expires_at`, `used_at`, `revoked_at` | family FK·선행 토큰 자기 FK; `token_hash` 유일; `ACTIVE`, `ROTATED`, `REVOKED`, `EXPIRED` |

- PK와 FK는 ERD 기준 `BIGINT`, 시간은 UTC 기준 `TIMESTAMPTZ`를 사용한다. Java 모델은 `Instant`로 매핑한다.
- `installation_id_hash`와 `token_hash`는 ERD 기준 `CHAR(64)` SHA-256 해시다. 원본 installation ID, Google token, Access·Refresh Token 평문을 저장하지 않는다.
- `nickname`, `unlinked_at`, `linked_desktop_device_id`, `last_used_at`, `revoked_at`, `parent_token_id`, `used_at` 등 선택 값의 nullability를 ERD와 맞춘다.
- 한 기기의 활성 token family는 최대 하나다. 동시 로그인 경합을 막기 위해 `device_id`의 `ACTIVE` 행에 부분 유니크 인덱스를 둔다. Refresh Token 회전 시에도 family당 활성 record를 하나로 제한한다.
- 활성 Desktop은 사용자당 최대 3대다. 이 제한과 Extension의 Desktop 소유·종류 검증, Desktop 해제 시 Extension 연쇄 해제는 후속 인증 서비스 트랜잭션에서 처리한다.
- `token_families`와 만료·폐기된 인증 기록은 정책상 30일 뒤 정리 대상이다. 실제 cleanup worker는 별도 Issue로 둔다.

## 처리 흐름

1. Flyway `V1__create_initial_schema.sql`에서 테이블을 FK 의존 순서로 생성한다.
2. 유니크 제약, 상태값 제약, 조회 인덱스를 ERD와 대조해 추가한다.
3. 동일 컬럼·관계를 표현하는 JPA Entity와 Repository를 작성한다.
4. 후속 로그인 Issue가 Repository를 이용해 OAuth 계정과 기기를 찾고 token family를 관리한다.

```text
후속 Auth Service → Repository → PostgreSQL 17
                         ↑
                    Flyway V1 schema
```

## 예외·연동 경계

| 상황 | 이번 작업 처리 |
| --- | --- |
| 동일 Google `provider_subject` 중복 | DB 유니크 제약으로 중복 생성 방지; API 응답은 후속 로그인 Issue에서 정의 |
| 동일 사용자·기기 설치 해시 중복 | DB 유니크 제약으로 중복 생성 방지 |
| 잘못된 FK·상태값 | DB 제약으로 저장 거부; 공통 HTTP 오류 매핑은 후속 API 작업 |
| 동시 로그인·Refresh 회전 | 활성 family·record의 제약과 잠금 전략을 함께 설계; 트랜잭션 구현은 후속 Issue |

## 예상 변경 파일

```text
server/src/main/resources/db/migration/V1__create_initial_schema.sql
server/src/main/java/com/capstone/backend/auth/... (Entity·Repository)
server/docs/features/auth-persistence-PLAN.md
server/docs/features/auth-persistence-ERROR.md
server/docs/features/auth-persistence-REPORT.md
server/docs/CONTEXT.md
server/docs/NEXT_TASK.md
```

`ERROR`와 `REPORT`는 Backend 템플릿 규칙에 따라 작업 시작 시 생성하되, 결과는 실제 구현 뒤 기록한다. 현재는 기획서 검토 단계다.

## 완료 조건

- [ ] Notion ERD의 인증 5개 테이블과 컬럼·상태·관계가 일치한다.
- [ ] Flyway V1과 Entity의 컬럼 타입·nullability·관계가 일치한다.
- [ ] 사용자·Google 계정·기기·token family·Refresh Token의 유일성 및 FK가 저장 계층에서 보장된다.
- [ ] 원본 인증 비밀값을 저장하거나 로그에 남기지 않는다.
- [ ] 빈 PostgreSQL에서 migration 적용 결과를 확인하고 결과를 `REPORT`에 기록한다.
- [ ] 실제 변경과 남은 후속 범위를 `CONTEXT.md`, `NEXT_TASK.md`에 반영한다.

## 정본 참고

- [Notion ERD](https://app.notion.com/p/ERD-3de0b6e5e64c80a4bf5ef7ff3ac01f17)
- [FocusOn API 명세서](https://app.notion.com/p/3e00b6e5e64c80e89eb1e64017029210)
- [인증 정책](https://app.notion.com/p/3de0b6e5e64c80c89c6cfe0b703fd964)

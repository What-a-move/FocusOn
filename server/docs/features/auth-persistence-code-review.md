# 인증 저장 모델 코드 리뷰

- 검토일: 2026-10-19
- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32)
- 기준: `origin/dev...feat/#32-auth-persistence` diff, 승인된 기획서, Backend 코드·개인정보 규칙
- 방식: 구현 맥락을 전달받지 않은 별도 에이전트의 정적 검토와 추가 외부 리뷰 지적을 대조했다. 실행 테스트는 하지 않음.

## 발견 사항

### 1. 🟠 High — Token Family의 사용자와 발급 기기 소유자가 달라도 저장됨

**문제**: `V1__create_initial_schema.sql:65-66`의 두 개별 FK는 각각 존재하는 사용자와 기기만 확인한다. 사용자 A의 `user_id`와 사용자 B의 `device_id`로 Token Family를 저장할 수 있어 이후 인증의 사용자·기기 관계가 모순된다.

**해결 방안**:
1. `user_devices(id, user_id)`에 유일 제약을 두고 `token_families(device_id, user_id)`의 복합 FK를 추가한다. 기존 ERD 컬럼과 Entity 관계를 유지하면서 DB에서 소유 관계를 강제한다. 인덱스와 FK가 하나씩 늘어난다. **이미 로컬 DB에 적용된 V1을 변경하지 않고 V2 SQL에 반영했다.**
2. `token_families.user_id`를 제거하고 기기에서만 사용자 ID를 파생한다. 중복 저장은 사라지지만 ERD 컬럼, Entity와 조회 경로를 모두 변경해야 한다.

### 2. 🟡 Medium — 해제된 Google identity가 로그인 조회에 포함됨

**문제**: 기존 `OauthIdentityRepository.java:8`의 `findByProviderAndProviderSubject`는 `unlinked_at`을 검사하지 않았다. 향후 연결 해제된 행이 있으면 후속 로그인 서비스가 이를 활성 연결로 오인할 수 있다.

**해결 방안**:
1. Repository 조회 조건에 `unlinked_at IS NULL`을 추가한다. 현재 ERD와 전체 유일성 제약을 유지하고 비활성 연결을 로그인에서 제외한다. **이번 변경에 적용했다.**
2. 호출 서비스마다 `unlinked_at`을 검사한다. 기존 메서드를 유지할 수 있지만 호출 경로마다 검사 누락 위험이 있다.

Google 계정의 `(provider, provider_subject)` 전체 유일성은 [Notion ERD](https://app.notion.com/p/ERD-3de0b6e5e64c80a4bf5ef7ff3ac01f17)에 명시되어 있다. 현재 MVP에는 Google 계정 연결 해제 API가 없으므로 활성 행만 유일하게 바꾸지는 않는다. 재연결 시 기존 행의 재활성화·계정 이전 정책은 해당 기능을 도입할 때 정한다.

### 3. 🟡 Medium — Refresh Token 기록의 발급 시각이 INSERT 때 누락됨

**문제**: `RefreshTokenRecordEntity.java:57`의 `issuedAt`은 `nullable = false`인데 생성 시각 설정이 없었다. 후속 로그인 서비스가 family·해시·만료 시각을 채워 신규 기록을 `save`하면 Hibernate가 `issued_at = NULL`을 INSERT에 포함할 수 있다. 이때 `V1__create_initial_schema.sql:93`의 DB 기본값은 적용되지 않고 `NOT NULL` 위반으로 저장이 실패한다.

**해결 방안**:
1. Entity에 `@CreationTimestamp`를 추가한다. 기존 `linkedAt`·`createdAt` 매핑과 같고 호출자가 설정을 빠뜨리지 않는다. Hibernate에 생성 시각 관리가 묶인다. **이번 변경에 적용했다.**
2. 생성자 또는 팩토리에서 `issuedAt`을 반드시 받는다. 발급 시각을 서비스가 명시적으로 제어할 수 있지만, 모든 생성 경로를 강제하는 모델 수정이 필요하다.

## 검토 결과

- 확인된 저장 계층 결함 3건의 대응을 반영했다. 전체 유일성 제약 변경 제안은 현재 정본과 MVP 범위에 맞지 않아 보류했다.
- 테이블·Entity 매핑, FK·유일성·상태 제약, 인증 원문 비저장 여부, 기획서 범위, 기존 계층 규칙을 정적으로 확인했다.
- Repository 수정 후 컴파일·Checkstyle을 통과했다. V2의 PostgreSQL 적용과 저장 제약은 [QA](auth-persistence-QA.md)에서 확인했다.

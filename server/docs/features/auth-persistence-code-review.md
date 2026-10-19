# 인증 저장 모델 코드 리뷰

- 검토일: 2026-10-19
- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32)
- 기준: `origin/dev...feat/#32-auth-persistence` diff, 승인된 기획서, Backend 코드·개인정보 규칙
- 방식: 구현 맥락을 전달받지 않은 별도 에이전트가 정적 검토. 실행 테스트는 하지 않음.

## 발견 사항

### 1. 🟡 Medium — Refresh Token 기록의 발급 시각이 INSERT 때 누락됨

**문제**: `RefreshTokenRecordEntity.java:57`의 `issuedAt`은 `nullable = false`인데 생성 시각 설정이 없었다. 후속 로그인 서비스가 family·해시·만료 시각을 채워 신규 기록을 `save`하면 Hibernate가 `issued_at = NULL`을 INSERT에 포함할 수 있다. 이때 `V1__create_initial_schema.sql:93`의 DB 기본값은 적용되지 않고 `NOT NULL` 위반으로 저장이 실패한다.

**해결 방안**:
1. Entity에 `@CreationTimestamp`를 추가한다. 기존 `linkedAt`·`createdAt` 매핑과 같고 호출자가 설정을 빠뜨리지 않는다. Hibernate에 생성 시각 관리가 묶인다. **이번 변경에 적용했다.**
2. 생성자 또는 팩토리에서 `issuedAt`을 반드시 받는다. 발급 시각을 서비스가 명시적으로 제어할 수 있지만, 모든 생성 경로를 강제하는 모델 수정이 필요하다.

## 검토 결과

- 위 1건을 수정했다. 추가로 확인된 Critical/High/Medium/Low 사항은 없다.
- 테이블·Entity 매핑, FK·유일성·상태 제약, 인증 원문 비저장 여부, 기획서 범위, 기존 계층 규칙을 정적으로 확인했다.
- 수정 후 `./gradlew compileJava checkstyleMain --no-daemon`이 성공했다. QA의 실행 테스트는 별도 단계로 남긴다.

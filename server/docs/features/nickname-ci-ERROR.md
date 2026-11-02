# 닉네임 동시성 CI 오류 기록

## 원인과 재현

- `concurrentFirstWritesAndNicknameConflicts`에서 기대값 `[200, 409]` 대신 `[200, 200]`이 발생했다.
- 기본 H2 테스트는 Flyway를 비활성화하고 Hibernate로 스키마를 생성한다. `users.nickname` 매핑에는 유일 제약이 없지만, PostgreSQL Flyway V1에는 `uq_users_nickname` 유일 인덱스가 있다. 사전 중복 조회가 겹치면 H2에서는 둘 다 저장될 수 있다.
- H2에 임시 유일 인덱스를 추가한 실험에서는 충돌 시 H2의 제약 이름 형식이 PostgreSQL과 달라 `[200, 500]`이 재현됐다. 그 임시 인덱스 추가는 최종 변경에 포함하지 않았다.

## 검증 환경 충돌

- 같은 빌드 디렉터리에서 Gradle 두 개를 동시에 실행해 테스트 산출물이 충돌했다. 해당 결과는 제품 결함으로 보지 않고, 검증은 순차 실행했다.

## 동시 조회 강제 검증

- 테스트용 감시에서 Spring Data `JpaRepositoryFactory`가 생성한 실제 `UserRepository.findByNickname`을 호출하고, 두 결과가 모두 비어 있는지 확인한 뒤 저장 단계로 진행하도록 동기화했다.
- PostgreSQL에서 `uq_users_nickname` 유일 제약 위반 로그가 발생하고 응답이 200·409이며 동일 닉네임은 한 건만 저장되는지 검증했다. 테스트용 Repository 인스턴스는 애플리케이션에 주입된 객체와는 별개다.

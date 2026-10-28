# 닉네임 동시성 CI 수정 계획

- Issue: [#93](https://github.com/What-a-move/FocusOn/issues/93)
- Branch: `fix/93-nickname-ci`, 최신 `origin/dev` 7470184 반영

## 범위

- H2 기본 테스트에서 동의·집중 설정의 동시 최초 저장 검증을 유지한다.
- 닉네임 동시 저장은 Flyway 실제 제약이 적용된 PostgreSQL에서 별도로 검증하고 CI 작업에 추가한다.
- 동일 닉네임 요청 결과 200·409와 DB에 한 건만 저장되는지 확인한다.
- 운영 로직과 기존 마이그레이션은 변경하지 않는다.

## 검증

- H2 전체 빌드·테스트·Checkstyle 및 PostgreSQL 동시성 테스트를 실행한다.
- PostgreSQL 테스트에서 두 사전 조회가 모두 빈 결과를 확인한 뒤에만 저장하도록 동기화하고, 유일 제약 충돌을 확인한다.
- CI 실제 실행 결과는 push 후 확인해야 한다.

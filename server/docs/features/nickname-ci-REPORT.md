# 닉네임 동시성 CI 수정 결과

- Issue: [#93](https://github.com/What-a-move/FocusOn/issues/93)
- Branch: `fix/93-nickname-ci`

## 변경

- 동시 최초 저장 테스트와 PostgreSQL 전용 닉네임 동시 충돌 테스트를 분리했다. H2에서는 PostgreSQL 전용 테스트가 건너뛰어진다.
- PostgreSQL 테스트에 동일 닉네임이 DB에 한 건만 남는지 확인하는 검증을 추가했다.
- Backend CI에 PostgreSQL 17 서비스와 Flyway 적용 후 해당 테스트를 실행하는 별도 작업을 추가했다.
- 운영 애플리케이션 코드와 migration은 변경하지 않았다.

## 검증

- H2 전체 `./gradlew build checkstyleMain checkstyleTest --no-daemon --rerun-tasks`: 성공. PostgreSQL 전용 테스트는 H2에서 건너뜀.
- PostgreSQL 17 임시 DB에서 Flyway 적용 후 동시성 테스트를 3회 순차 재실행해 모두 통과했다. 각 회차에서 응답 200·409와 저장된 닉네임 1건을 확인했다.
- GitHub Actions의 실제 CI 실행 상태는 push 후 확인해야 한다.
- 추가 검증에서 Spring Data가 생성한 실제 `UserRepository.findByNickname`으로 두 요청이 모두 빈 조회 결과를 확인한 뒤 저장하도록 강제했다. PostgreSQL `uq_users_nickname` 충돌, 200·409 응답과 DB 저장 1건을 확인했고 3회 재실행해 모두 통과했다. 테스트용 Repository는 애플리케이션에 주입된 객체와 별개로 생성했다.
- 최신 `dev` 7470184를 병합한 뒤 PostgreSQL 강제 동시성 테스트와 H2 전체 빌드·테스트, Main/Test Checkstyle을 재실행해 통과했다.
- 최신 `dev`에도 닉네임 동시 충돌 부분을 H2에서 건너뛰는 변경이 이미 포함되어 있었다. 이번 변경은 이를 분리하고 PostgreSQL CI 검사와 사전 조회 동기화를 추가한다.

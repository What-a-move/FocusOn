# Flyway 마이그레이션 규칙

FocusOn Server는 PostgreSQL(`server/docker-compose.yml`의 `postgres:17.11-alpine3.24`)과 Flyway(`server/build.gradle`의 `flyway-core`, `flyway-database-postgresql`)를 사용한다. `spring.flyway.locations=classpath:db/migration`(`application.properties`)에 따라 마이그레이션 파일은 `server/src/main/resources/db/migration/`에 둔다.

이 문서는 `server/src/main/resources/db/migration/README.md`(이미 존재)를 대체하지 않고, 그 내용을 팀 전체 규칙 수준으로 보강한다. 두 문서가 어긋나면 실제 폴더의 README.md를 우선 확인하고 둘 다 갱신한다.

## 파일명 규칙

현재 이 프로젝트는 순번 방식을 쓴다(기존 README.md 기준):

```text
V1__create_initial_schema.sql
V2__add_device_pairing.sql
```

- 아직 `V1`이 만들어지지 않은 이유는 API 응답 형식·ID 타입·인증 계정 구조가 문서마다 달라서다(`server/docs/DECISION_RECORD.md` 결정 003~009 확정 후 진행).
- 여러 브랜치가 동시에 마이그레이션을 추가하는 상황이 잦아지면 순번 충돌 위험이 커진다. 그 경우 타임스탬프 버전(`V{yyyyMMddHHmmss}__{설명}.sql`, KST 기준)으로 전환할지 팀 논의 후 이 문서를 갱신한다 — 지금은 순번 방식을 유지한다.

## 기본 원칙

- 이미 적용된(커밋되어 `dev`에 머지된) 마이그레이션 파일은 절대 수정하지 않는다. 스키마를 더 바꿔야 하면 새 버전 파일을 추가한다.
- 로컬(`application-local.properties`)과 Docker(`application-docker.properties`) 모두 같은 마이그레이션이 적용되는지 확인한다.
- PostgreSQL에서 직접 검증한 SQL만 추가한다(`docker compose up postgres`로 로컬 인스턴스를 띄운 뒤 검증).
- Entity 변경과 마이그레이션 파일 추가를 같은 작업(같은 Issue·커밋 단위)에서 함께 검토한다.
- 운영 데이터가 존재하는 이후 단계에서는, 컬럼 삭제·타입 변경처럼 기존 데이터를 잃을 수 있는 변경은 별도 승인을 받는다: 예) 컬럼을 바로 삭제하지 않고 먼저 nullable로 두는 단계적 마이그레이션을 검토한다.
- 마이그레이션 실패 시 복구 방법(직전 버전으로 롤백 가능한 SQL인지, 수동 개입이 필요한지)을 해당 파일 상단 주석에 남긴다.

## 로컬 실행

```bash
# Postgres만 띄우기
docker compose -f server/docker-compose.yml up -d postgres

# Server 실행(Flyway가 기동 시 자동으로 마이그레이션 적용)
./server/gradlew -p server bootRun --args='--spring.profiles.active=local'
```

- `.env`는 `server/.env.example`을 복사해서 만들고 Git에 올리지 않는다(`docs/ENVIRONMENT.md` 규칙).
- 테스트는 `server/src/test/resources/application-test.properties`(H2, `server/build.gradle`의 `testRuntimeOnly 'com.h2database:h2'`)를 사용하므로 PostgreSQL 없이도 돌아간다. 단, H2와 PostgreSQL의 SQL 방언 차이로 실제 동작이 다를 수 있는 마이그레이션(예: PostgreSQL 전용 타입)은 Docker Compose로 PostgreSQL을 띄워 별도 확인한다.

## 관련 문서

- `server/src/main/resources/db/migration/README.md`
- `docs/ENVIRONMENT.md`
- `server/docs/DEVELOPMENT_RULES.md`

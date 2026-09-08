# Flyway migration 안내

운영 스키마 변경 SQL을 이 폴더에서 순서대로 관리한다.

## 파일명

```text
V1__create_initial_schema.sql
V2__add_device_pairing.sql
```

- 한 번 적용된 migration 파일은 수정하지 않고 다음 버전 파일을 추가한다.
- PostgreSQL에서 직접 검증한 SQL만 추가한다.
- Entity 변경과 migration을 같은 작업 범위에서 함께 검토한다.
- 현재 API 응답 형식, ID 타입, 인증 계정 구조가 문서마다 충돌하므로 계약 확정 전에는 `V1`을 만들지 않는다.

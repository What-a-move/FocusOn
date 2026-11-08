-- Flyway 기본 migration별 transaction을 유지한다(group=false).
-- V11이 커밋돼 ACCESS EXCLUSIVE 잠금을 해제한 뒤 별도 transaction에서 스캔한다.
ALTER TABLE token_families VALIDATE CONSTRAINT ck_token_families_revoked_reason;

-- 기존 동의 기록의 출처는 알 수 없으므로 NULL로 유지한다.
-- 롤백 시 컬럼 삭제는 저장된 출처를 잃으므로 별도 승인 후 수행한다.
ALTER TABLE user_consents ADD COLUMN consent_source VARCHAR(32);

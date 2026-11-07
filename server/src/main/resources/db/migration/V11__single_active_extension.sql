-- 배포 전 PR 단계의 V11. 제약 추가 시 전체 스캔을 피하고 V12에서 별도 검증한다.
ALTER TABLE token_families DROP CONSTRAINT ck_token_families_revoked_reason;
ALTER TABLE token_families ADD CONSTRAINT ck_token_families_revoked_reason
    CHECK (revoked_reason IS NULL OR revoked_reason IN
        ('LOGOUT', 'UNLINK', 'TOKEN_REUSE', 'DEVICE_REVOKED', 'REPLACED')) NOT VALID;

-- TokenFamilyGuard와 같은 인증 가능 조건을 먼저 적용하고 그 안에서 최신 연결을 유지한다.
-- 모두 인증 불가이면 최신 연결 하나를 남기되 유효하지 않은 토큰을 되살리지는 않는다.
CREATE TEMP TABLE focuson_replaced_extensions ON COMMIT DROP AS
SELECT id FROM (
    SELECT d.id, row_number() OVER (
        PARTITION BY d.user_id
        ORDER BY CASE WHEN u.deleted_at IS NULL
            AND f.user_id = d.user_id AND f.expires_at > CURRENT_TIMESTAMP
            AND desktop.user_id = d.user_id AND desktop.device_type = 'DESKTOP'
            AND desktop.status = 'ACTIVE'
            AND parent.user_id = d.user_id AND parent.expires_at > CURRENT_TIMESTAMP
            THEN 0 ELSE 1 END,
            COALESCE(f.created_at, d.created_at) DESC, d.id DESC
    ) AS priority
    FROM user_devices d
    LEFT JOIN token_families f ON f.device_id = d.id AND f.status = 'ACTIVE'
    JOIN users u ON u.id = d.user_id
    LEFT JOIN user_devices desktop ON desktop.id = d.linked_desktop_device_id
    LEFT JOIN token_families parent ON parent.device_id = desktop.id AND parent.status = 'ACTIVE'
    WHERE d.device_type = 'CHROME_EXTENSION' AND d.status = 'ACTIVE'
) ranked WHERE priority > 1;

UPDATE refresh_token_records SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE' AND token_family_id IN (
    SELECT id FROM token_families WHERE status = 'ACTIVE'
        AND device_id IN (SELECT id FROM focuson_replaced_extensions)
);
UPDATE token_families SET status = 'REVOKED', revoked_reason = 'REPLACED',
    revoked_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE' AND device_id IN (SELECT id FROM focuson_replaced_extensions);
UPDATE user_devices SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT id FROM focuson_replaced_extensions);

CREATE UNIQUE INDEX uq_user_devices_single_active_extension ON user_devices (user_id)
    WHERE device_type = 'CHROME_EXTENSION' AND status = 'ACTIVE';

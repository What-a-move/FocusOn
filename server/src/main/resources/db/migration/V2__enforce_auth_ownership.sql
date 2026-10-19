-- Issue #32: a token family and its device must belong to the same user.
-- PostgreSQL rolls back this migration if existing rows violate the new FK.
-- Audit and correct such rows before retrying; never reassign an owner automatically.
-- The constraints can be dropped to roll back the schema change, but doing so
-- removes the ownership guarantee and must not be used as a data repair.

ALTER TABLE user_devices
    ADD CONSTRAINT uq_user_devices_id_user UNIQUE (id, user_id);

ALTER TABLE token_families
    ADD CONSTRAINT fk_token_families_device_owner
    FOREIGN KEY (device_id, user_id) REFERENCES user_devices (id, user_id);

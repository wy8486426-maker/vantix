/*
 * Runtime schema for the days model.
 *
 * This migration intentionally does not backfill legacy rows. The application is
 * still in development; the old columns remain nullable legacy storage so a
 * future migration can preserve historical facts without changing them.
 */
ALTER TABLE service_duration_config
    ADD COLUMN display_name VARCHAR(128) NULL AFTER spec_code,
    ADD COLUMN duration_days INT NULL AFTER service_type,
    ADD COLUMN code_silence_days INT NULL AFTER duration_days,
    ADD COLUMN account_silence_days INT NULL AFTER code_silence_days,
    MODIFY COLUMN duration_value INT NULL,
    MODIFY COLUMN duration_unit VARCHAR(16) NULL,
    MODIFY COLUMN code_silence_months INT NULL,
    DROP INDEX uk_duration,
    DROP INDEX uk_service_duration_value_unit,
    ADD UNIQUE KEY uk_service_duration_display_name (display_name);

DROP TRIGGER IF EXISTS trg_service_duration_identity_immutable;

CREATE TRIGGER trg_service_duration_identity_immutable
BEFORE UPDATE ON service_duration_config
FOR EACH ROW
BEGIN
    IF NOT (BINARY NEW.spec_code <=> BINARY OLD.spec_code)
       OR NOT (BINARY NEW.service_type <=> BINARY OLD.service_type)
       OR NOT (NEW.duration_days <=> OLD.duration_days) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'service duration identity is immutable';
    END IF;
END;

ALTER TABLE service_code
    ADD COLUMN spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER generate_batch_id,
    ADD COLUMN duration_days INT NULL AFTER service_type,
    ADD COLUMN code_silence_days INT NULL AFTER duration_days,
    MODIFY COLUMN duration_value INT NULL,
    MODIFY COLUMN duration_unit VARCHAR(16) NULL,
    MODIFY COLUMN code_silence_months INT NULL;

ALTER TABLE service_code_generate_batch
    ADD COLUMN display_name VARCHAR(128) NULL AFTER spec_code,
    ADD COLUMN service_type VARCHAR(64) NULL AFTER display_name,
    ADD COLUMN duration_days INT NULL AFTER service_type,
    ADD COLUMN code_silence_days INT NULL AFTER duration_days,
    MODIFY COLUMN duration_value INT NULL,
    MODIFY COLUMN duration_unit VARCHAR(16) NULL,
    MODIFY COLUMN code_silence_months INT NULL;

ALTER TABLE exchange_batch
    ADD COLUMN duration_days INT NULL AFTER service_type,
    ADD COLUMN account_silence_days INT NULL AFTER payload_hash,
    MODIFY COLUMN duration_value INT NULL,
    MODIFY COLUMN duration_unit VARCHAR(16) NULL,
    MODIFY COLUMN account_silence_months INT NULL;

ALTER TABLE service_account
    ADD COLUMN spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER exchange_detail_id,
    ADD COLUMN duration_days INT NULL AFTER service_type,
    ADD COLUMN account_silence_days INT NULL AFTER duration_days,
    MODIFY COLUMN duration_value INT NULL,
    MODIFY COLUMN duration_unit VARCHAR(16) NULL,
    MODIFY COLUMN account_silence_months INT NULL;

ALTER TABLE account_renewal
    ADD COLUMN spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER assigned_user_id,
    ADD COLUMN duration_days INT NULL AFTER service_type,
    ADD COLUMN code_silence_days INT NULL AFTER duration_days,
    MODIFY COLUMN duration_value INT NULL,
    MODIFY COLUMN duration_unit VARCHAR(16) NULL;

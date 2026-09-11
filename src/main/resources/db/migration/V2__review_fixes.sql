ALTER TABLE service_code_transfer
    DROP INDEX uk_transfer_no,
    ADD COLUMN reason VARCHAR(512) NULL AFTER transfer_type,
    ADD KEY idx_transfer_no (transfer_no),
    ADD UNIQUE KEY uk_transfer_code (transfer_no, service_code_id);

ALTER TABLE cors_operation
    ADD COLUMN biz_type VARCHAR(64) NULL AFTER operation_type,
    ADD COLUMN biz_id BIGINT NULL AFTER biz_type,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN next_retry_at DATETIME(3) NULL AFTER retry_count,
    ADD COLUMN claimed_at DATETIME(3) NULL AFTER next_retry_at,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER claimed_at,
    ADD COLUMN last_error_message VARCHAR(1024) NULL AFTER last_error_code,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER last_error_message;

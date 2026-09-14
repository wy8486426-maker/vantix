ALTER TABLE exchange_batch
    MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE exchange_detail
    MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE cors_operation
    MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE service_code
    DROP INDEX uk_service_code_processing_request,
    ADD KEY idx_service_code_processing_request (processing_request_id);

ALTER TABLE exchange_batch
    ADD COLUMN generation_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER owner_company_id,
    ADD COLUMN spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER generation_source,
    ADD COLUMN service_type VARCHAR(64) NOT NULL AFTER spec_code,
    ADD COLUMN duration_value INT NOT NULL AFTER service_type,
    ADD COLUMN duration_unit VARCHAR(16) NOT NULL AFTER duration_value,
    ADD COLUMN quantity INT NOT NULL AFTER duration_unit,
    ADD COLUMN account_prefix VARCHAR(64) NULL AFTER quantity,
    ADD COLUMN payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER account_prefix,
    ADD COLUMN account_silence_months INT NOT NULL AFTER payload_hash,
    ADD COLUMN operator_user_id BIGINT NULL AFTER status,
    ADD COLUMN operator_user_name VARCHAR(128) NULL AFTER operator_user_id,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER operator_user_name,
    ADD COLUMN last_error_message VARCHAR(1024) NULL AFTER last_error_code,

    ADD COLUMN completed_at DATETIME(3) NULL AFTER last_error_message;

ALTER TABLE exchange_detail
    DROP INDEX uk_exchange_detail_request,
    ADD COLUMN detail_index INT NOT NULL AFTER exchange_batch_id,
    ADD COLUMN service_code_snapshot JSON NOT NULL AFTER detail_index,
    ADD COLUMN cors_account_id VARCHAR(128) NULL AFTER service_code_snapshot,
    ADD COLUMN account VARCHAR(128) NULL AFTER cors_account_id,
    ADD COLUMN completed_at DATETIME(3) NULL AFTER account,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER completed_at,
    ADD COLUMN last_error_message VARCHAR(1024) NULL AFTER last_error_code,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at,
    ADD UNIQUE KEY uk_exchange_detail_batch_index (exchange_batch_id, detail_index);

ALTER TABLE service_account
    ADD COLUMN exchange_detail_id BIGINT NULL AFTER exchange_batch_id,
    ADD COLUMN cors_activation_status VARCHAR(32) NULL AFTER cors_status,
    ADD COLUMN cors_created_at DATETIME(3) NULL AFTER expire_at,
    ADD UNIQUE KEY uk_service_account_exchange_detail (exchange_detail_id);

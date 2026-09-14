ALTER TABLE account_renewal
    DROP INDEX uk_account_renewal_code,
    MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ADD COLUMN owner_company_id BIGINT NOT NULL AFTER service_code_id,
    ADD COLUMN assigned_user_id BIGINT NULL AFTER owner_company_id,
    ADD COLUMN service_type VARCHAR(64) NOT NULL AFTER assigned_user_id,
    ADD COLUMN duration_value INT NOT NULL AFTER service_type,
    ADD COLUMN duration_unit VARCHAR(16) NOT NULL AFTER duration_value,
    ADD COLUMN service_code_snapshot JSON NOT NULL AFTER duration_unit,
    ADD COLUMN operator_user_id BIGINT NULL AFTER service_code_snapshot,
    ADD COLUMN operator_user_name VARCHAR(128) NULL AFTER operator_user_id,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER operator_user_name,
    ADD COLUMN last_error_message VARCHAR(1024) NULL AFTER last_error_code,
    ADD COLUMN completed_at DATETIME(3) NULL AFTER last_error_message,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
    ADD COLUMN active_service_code_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('PROCESSING', 'COMPLETED', 'MANUAL_REVIEW') THEN service_code_id
            ELSE NULL
        END
    ) STORED,
    ADD COLUMN active_service_account_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('PROCESSING', 'MANUAL_REVIEW') THEN service_account_id
            ELSE NULL
        END
    ) STORED,
    ADD KEY idx_account_renewal_code (service_code_id),
    ADD UNIQUE KEY uk_account_renewal_active_code (active_service_code_id),
    ADD UNIQUE KEY uk_account_renewal_active_account (active_service_account_id),
    ADD KEY idx_account_renewal_account_created (service_account_id, created_at, id);

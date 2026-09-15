/*
 * Pre-release baseline schema for Vantix.
 *
 * This is intentionally a complete days-based schema. Development databases
 * created from V1 through V9 are not upgraded in place; they are recreated
 * before applying this baseline.
 */

CREATE TABLE dealer_company (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    company_name VARCHAR(128) NOT NULL,
    parent_company_id BIGINT NULL,
    company_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    company_synced_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dealer_company_company_id (company_id),
    KEY idx_dealer_company_parent (parent_company_id),
    KEY idx_dealer_company_name (company_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dealer_relation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    old_parent_company_id BIGINT NULL,
    new_parent_company_id BIGINT NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    reason VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_relation_log_company (company_id),
    KEY idx_relation_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE system_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(512) NOT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE service_duration_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_days INT NOT NULL,
    code_silence_days INT NOT NULL,
    account_silence_days INT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(512) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_duration_spec_code (spec_code),
    UNIQUE KEY uk_service_duration_display_name (display_name),
    KEY idx_duration_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE service_code_generate_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    generation_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_order_no VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_order_time DATETIME(3) NULL,
    owner_company_id BIGINT NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_count INT NOT NULL,
    total_quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_code_generate_order_request (request_id),
    UNIQUE KEY uk_service_code_generate_order_business
        (generation_source, owner_company_id, source_order_no),
    KEY idx_service_code_generate_order_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE service_code_generate_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    generation_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_order_no VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_order_time DATETIME(3) NULL,
    owner_company_id BIGINT NOT NULL,
    spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_days INT NOT NULL,
    code_silence_days INT NOT NULL,
    quantity INT NOT NULL,
    generated_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(512) NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    business_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    generate_order_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_code_generate_batch_no (batch_no),
    UNIQUE KEY uk_service_code_generate_request (request_id),
    UNIQUE KEY uk_service_code_generate_business
        (generation_source, owner_company_id, business_key_hash),
    UNIQUE KEY uk_service_code_generate_order_spec (generate_order_id, spec_code),
    KEY idx_service_code_generate_order (owner_company_id, source_order_no),
    KEY idx_service_code_generate_order_id (generate_order_id),
    KEY idx_service_code_generate_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE service_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    source_order_id BIGINT NULL,
    source_order_no VARCHAR(128) NULL,
    generate_batch_id BIGINT NULL,
    owner_company_id BIGINT NOT NULL,
    spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_days INT NOT NULL,
    code_silence_days INT NOT NULL,
    expire_at DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    processing_type VARCHAR(16) NULL,
    processing_request_id VARCHAR(128) NULL,
    consume_type VARCHAR(16) NULL,
    consumed_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_code_code (code),
    KEY idx_service_code_owner_status_expire (owner_company_id, status, expire_at),
    KEY idx_service_code_source_order (source_order_id),
    KEY idx_service_code_generate_batch (generate_batch_id),
    KEY idx_service_code_processing_request (processing_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TRIGGER trg_service_code_code_immutable
BEFORE UPDATE ON service_code
FOR EACH ROW SET NEW.code = OLD.code;

CREATE TABLE service_code_transfer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_no VARCHAR(64) NOT NULL,
    service_code_id BIGINT NOT NULL,
    service_code VARCHAR(128) NOT NULL,
    from_company_id BIGINT NOT NULL,
    to_company_id BIGINT NOT NULL,
    transfer_type VARCHAR(32) NOT NULL,
    reason VARCHAR(512) NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_transfer_no (transfer_no),
    UNIQUE KEY uk_transfer_code (transfer_no, service_code_id),
    KEY idx_transfer_service_code (service_code_id),
    KEY idx_transfer_from_company_created (from_company_id, created_at),
    KEY idx_transfer_to_company_created (to_company_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE exchange_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exchange_batch_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    owner_company_id BIGINT NOT NULL,
    assigned_user_id BIGINT NULL,
    generation_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_days INT NOT NULL,
    account_silence_days INT NOT NULL,
    quantity INT NOT NULL,
    account_prefix VARCHAR(64) NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1024) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_batch_no (exchange_batch_no),
    UNIQUE KEY uk_exchange_batch_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE exchange_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exchange_batch_id BIGINT NOT NULL,
    detail_index INT NOT NULL,
    service_code_id BIGINT NOT NULL,
    request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    service_code_snapshot JSON NOT NULL,
    cors_account_id VARCHAR(128) NULL,
    account VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    completed_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1024) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_service_code_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('PROCESSING', 'COMPLETED') THEN service_code_id
            ELSE NULL
        END
    ) STORED,
    PRIMARY KEY (id),
    KEY idx_exchange_detail_code (service_code_id),
    KEY idx_exchange_detail_request (request_id),
    UNIQUE KEY uk_exchange_detail_active_code (active_service_code_id),
    UNIQUE KEY uk_exchange_detail_batch_index (exchange_batch_id, detail_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE service_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cors_account_id VARCHAR(128) NULL,
    account VARCHAR(128) NULL,
    owner_company_id BIGINT NOT NULL,
    assigned_user_id BIGINT NULL,
    source_service_code_id BIGINT NOT NULL,
    exchange_batch_id BIGINT NULL,
    exchange_detail_id BIGINT NULL,
    spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_days INT NOT NULL,
    account_silence_days INT NOT NULL,
    exchange_at DATETIME(3) NULL,
    cors_status VARCHAR(32) NULL,
    cors_activation_status VARCHAR(32) NULL,
    activated_at DATETIME(3) NULL,
    expire_at DATETIME(3) NULL,
    cors_created_at DATETIME(3) NULL,
    cors_updated_at DATETIME(3) NULL,
    last_sync_at DATETIME(3) NULL,
    status_sync_next_at DATETIME(3) NULL,
    status_sync_last_attempt_at DATETIME(3) NULL,
    status_sync_failure_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_account_cors_id (cors_account_id),
    UNIQUE KEY uk_service_account_account (account),
    UNIQUE KEY uk_service_account_source_code (source_service_code_id),
    UNIQUE KEY uk_service_account_exchange_detail (exchange_detail_id),
    KEY idx_service_account_owner_assigned (owner_company_id, assigned_user_id),
    KEY idx_service_account_sync_next (status_sync_next_at, id),
    KEY idx_service_account_activation_sync (cors_activation_status, status_sync_next_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_renewal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_account_id BIGINT NOT NULL,
    service_code_id BIGINT NOT NULL,
    owner_company_id BIGINT NOT NULL,
    assigned_user_id BIGINT NULL,
    spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_days INT NOT NULL,
    code_silence_days INT NOT NULL,
    service_code_snapshot JSON NOT NULL,
    request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1024) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_service_code_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('PROCESSING', 'COMPLETED', 'MANUAL_REVIEW') THEN service_code_id
            ELSE NULL
        END
    ) STORED,
    active_service_account_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('PROCESSING', 'MANUAL_REVIEW') THEN service_account_id
            ELSE NULL
        END
    ) STORED,
    PRIMARY KEY (id),
    KEY idx_account_renewal_code (service_code_id),
    UNIQUE KEY uk_account_renewal_request (request_id),
    UNIQUE KEY uk_account_renewal_active_code (active_service_code_id),
    UNIQUE KEY uk_account_renewal_active_account (active_service_account_id),
    KEY idx_account_renewal_account_created (service_account_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cors_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    biz_type VARCHAR(64) NULL,
    biz_id BIGINT NULL,
    service_account_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    claimed_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1024) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cors_operation_request (request_id),
    UNIQUE KEY uk_cors_operation_biz (biz_type, biz_id),
    KEY idx_cors_operation_due
        (operation_type, biz_type, status, next_retry_at, created_at, id),
    KEY idx_cors_operation_claimed
        (operation_type, biz_type, status, claimed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cors_event_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cors_event_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_password_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    service_account_id BIGINT NOT NULL,
    owner_company_id BIGINT NOT NULL,
    assigned_user_id BIGINT NULL,
    cors_account_id VARCHAR(128) NOT NULL,
    account VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(512) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    active_reset_account_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN action_type = 'RESET' AND status IN ('PROCESSING', 'MANUAL_REVIEW')
            THEN service_account_id
            ELSE NULL
        END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_action_request (request_id),
    UNIQUE KEY uk_password_action_active_reset (active_reset_account_id),
    KEY idx_password_action_account_created (service_account_id, created_at, id),
    KEY idx_password_action_type_status (action_type, status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

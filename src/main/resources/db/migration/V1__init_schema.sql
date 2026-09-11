CREATE TABLE IF NOT EXISTS dealer_company (
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

CREATE TABLE IF NOT EXISTS dealer_relation_log (
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

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(512) NOT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_duration_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_type VARCHAR(64) NOT NULL,
    duration_value INT NOT NULL,
    duration_unit VARCHAR(16) NOT NULL,
    code_silence_months INT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(512) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_duration (service_type, duration_value, duration_unit),
    KEY idx_duration_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS account_config (
    id BIGINT NOT NULL,
    account_silence_months INT NOT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    source_order_id BIGINT NULL,
    source_order_no VARCHAR(128) NULL,
    owner_company_id BIGINT NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_value INT NOT NULL,
    duration_unit VARCHAR(16) NOT NULL,
    code_silence_months INT NOT NULL,
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
    UNIQUE KEY uk_service_code_processing_request (processing_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_code_transfer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_no VARCHAR(64) NOT NULL,
    service_code_id BIGINT NOT NULL,
    service_code VARCHAR(128) NOT NULL,
    from_company_id BIGINT NOT NULL,
    to_company_id BIGINT NOT NULL,
    transfer_type VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_transfer_service_code (service_code_id),
    KEY idx_transfer_from_company_created (from_company_id, created_at),
    KEY idx_transfer_to_company_created (to_company_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cors_account_id VARCHAR(128) NULL,
    account VARCHAR(128) NULL,
    owner_company_id BIGINT NOT NULL,
    assigned_user_id BIGINT NULL,
    source_service_code_id BIGINT NOT NULL,
    exchange_batch_id BIGINT NULL,
    service_type VARCHAR(64) NOT NULL,
    duration_value INT NOT NULL,
    duration_unit VARCHAR(16) NOT NULL,
    account_silence_months INT NOT NULL,
    exchange_at DATETIME(3) NULL,
    force_activate_at DATETIME(3) NULL,
    cors_status VARCHAR(32) NULL,
    activated_at DATETIME(3) NULL,
    expire_at DATETIME(3) NULL,
    cors_updated_at DATETIME(3) NULL,
    last_sync_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_account_cors_id (cors_account_id),
    UNIQUE KEY uk_service_account_account (account),
    UNIQUE KEY uk_service_account_source_code (source_service_code_id),
    KEY idx_service_account_owner_assigned (owner_company_id, assigned_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exchange_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exchange_batch_no VARCHAR(64) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    owner_company_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_batch_no (exchange_batch_no),
    UNIQUE KEY uk_exchange_batch_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exchange_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exchange_batch_id BIGINT NOT NULL,
    service_code_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_detail_code (service_code_id),
    UNIQUE KEY uk_exchange_detail_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS account_renewal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_account_id BIGINT NOT NULL,
    service_code_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_renewal_code (service_code_id),
    UNIQUE KEY uk_account_renewal_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cors_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(128) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    service_account_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    last_error VARCHAR(1024) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cors_operation_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cors_event_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cors_event_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO account_config (id, account_silence_months)
VALUES (1, 12)
ON DUPLICATE KEY UPDATE id = id;

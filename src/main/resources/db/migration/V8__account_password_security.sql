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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

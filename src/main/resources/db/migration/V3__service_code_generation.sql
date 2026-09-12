ALTER TABLE service_duration_config
    ADD COLUMN spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER id;

UPDATE service_duration_config
SET spec_code = CONCAT(
    CASE UPPER(duration_unit)
        WHEN 'DAY' THEN 'D'
        WHEN 'WEEK' THEN 'W'
        WHEN 'MONTH' THEN 'M'
        WHEN 'YEAR' THEN 'Y'
    END,
    duration_value
);

ALTER TABLE service_duration_config
    MODIFY COLUMN spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uk_service_duration_spec_code (spec_code),
    ADD UNIQUE KEY uk_service_duration_value_unit (duration_value, duration_unit);

CREATE TRIGGER trg_service_duration_identity_immutable
BEFORE UPDATE ON service_duration_config
FOR EACH ROW
BEGIN
    IF NOT (BINARY NEW.spec_code <=> BINARY OLD.spec_code)
       OR NOT (BINARY NEW.service_type <=> BINARY OLD.service_type)
       OR NOT (NEW.duration_value <=> OLD.duration_value)
       OR NOT (BINARY NEW.duration_unit <=> BINARY OLD.duration_unit) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'service duration identity is immutable';
    END IF;
END;

CREATE TABLE service_code_generate_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    generation_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_order_no VARCHAR(128) NOT NULL,
    source_order_time DATETIME(3) NULL,
    owner_company_id BIGINT NOT NULL,
    spec_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    duration_value INT NOT NULL,
    duration_unit VARCHAR(16) NOT NULL,
    code_silence_months INT NOT NULL,
    quantity INT NOT NULL,
    generated_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(512) NULL,
    operator_user_id BIGINT NULL,
    operator_user_name VARCHAR(128) NULL,
    business_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_code_generate_batch_no (batch_no),
    UNIQUE KEY uk_service_code_generate_request (request_id),
    UNIQUE KEY uk_service_code_generate_business
        (generation_source, owner_company_id, business_key_hash),
    KEY idx_service_code_generate_order (owner_company_id, source_order_no),
    KEY idx_service_code_generate_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE service_code
    ADD COLUMN generate_batch_id BIGINT NULL AFTER source_order_no,
    ADD KEY idx_service_code_generate_batch (generate_batch_id);

CREATE TRIGGER trg_service_code_code_immutable
BEFORE UPDATE ON service_code
FOR EACH ROW SET NEW.code = OLD.code;

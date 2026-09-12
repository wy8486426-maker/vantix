ALTER TABLE service_duration_config
    ADD COLUMN spec_code VARCHAR(700) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER id;

CREATE TEMPORARY TABLE tmp_duration_spec_counts AS
SELECT duration_value, UPPER(duration_unit) AS normalized_unit, COUNT(*) AS spec_count
FROM service_duration_config
GROUP BY duration_value, UPPER(duration_unit);

UPDATE service_duration_config config
JOIN tmp_duration_spec_counts counts
  ON counts.duration_value = config.duration_value
 AND counts.normalized_unit = UPPER(config.duration_unit)
SET config.spec_code = CASE
    WHEN counts.spec_count = 1 AND counts.normalized_unit = 'DAY' THEN CONCAT('D', config.duration_value)
    WHEN counts.spec_count = 1 AND counts.normalized_unit = 'WEEK' THEN CONCAT('W', config.duration_value)
    WHEN counts.spec_count = 1 AND counts.normalized_unit = 'MONTH' THEN CONCAT('M', config.duration_value)
    WHEN counts.spec_count = 1 AND counts.normalized_unit = 'YEAR' THEN CONCAT('Y', config.duration_value)
    WHEN counts.normalized_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')
        THEN CONCAT(CASE counts.normalized_unit
                        WHEN 'DAY' THEN 'D' WHEN 'WEEK' THEN 'W'
                        WHEN 'MONTH' THEN 'M' ELSE 'Y' END,
                    config.duration_value, '-S', HEX(CONVERT(config.service_type USING utf8mb4)))
    ELSE CONCAT('L-', HEX(CONVERT(config.service_type USING utf8mb4)), '-',
                config.duration_value, '-', HEX(CONVERT(config.duration_unit USING utf8mb4)))
END;

DROP TEMPORARY TABLE tmp_duration_spec_counts;

ALTER TABLE service_duration_config
    MODIFY COLUMN spec_code VARCHAR(700) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uk_service_duration_spec_code (spec_code);

CREATE TRIGGER trg_service_duration_spec_code_immutable
BEFORE UPDATE ON service_duration_config
FOR EACH ROW SET NEW.spec_code = OLD.spec_code;

CREATE TABLE service_code_generate_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    generation_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_order_no VARCHAR(128) NOT NULL,
    source_order_time DATETIME(3) NULL,
    owner_company_id BIGINT NOT NULL,
    spec_code VARCHAR(700) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
    ADD KEY idx_service_code_generate_batch (generate_batch_id),
    ADD CONSTRAINT fk_service_code_generate_batch
        FOREIGN KEY (generate_batch_id) REFERENCES service_code_generate_batch (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE TRIGGER trg_service_code_code_immutable
BEFORE UPDATE ON service_code
FOR EACH ROW SET NEW.code = OLD.code;
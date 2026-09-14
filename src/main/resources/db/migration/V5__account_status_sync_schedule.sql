ALTER TABLE service_account
    ADD COLUMN status_sync_next_at DATETIME(3) NULL,
    ADD COLUMN status_sync_last_attempt_at DATETIME(3) NULL,
    ADD COLUMN status_sync_failure_count INT NOT NULL DEFAULT 0,
    ADD KEY idx_service_account_sync_next (status_sync_next_at, id),
    ADD KEY idx_service_account_activation_sync (cors_activation_status, status_sync_next_at, id);

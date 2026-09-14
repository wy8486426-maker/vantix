ALTER TABLE service_account
    ADD KEY idx_service_account_force_activation
        (cors_activation_status, force_activate_at, status_sync_next_at, id);

package com.sinognss.cloud.vantix.application.cors.account;

public enum AccountStatusReconcileOutcome {
    UPDATED,
    STALE_IGNORED,
    IDEMPOTENT_NOOP,
    INCONSISTENT,
    CONCURRENT_MODIFICATION,
    NOT_FOUND,
    UNKNOWN,
    SKIPPED
}

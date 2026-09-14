package com.sinognss.cloud.vantix.application.cors.account;

public enum CorsAccountStateApplyOutcome {
    UPDATED,
    STALE_IGNORED,
    IDEMPOTENT_NOOP,
    INCONSISTENT,
    CONCURRENT_MODIFICATION
}

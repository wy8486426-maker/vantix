package com.sinognss.cloud.vantix.application.cors.account;

public enum CorsAccountRealtimeRefreshOutcome {
    UPDATED,
    IDEMPOTENT_NOOP,
    STALE_IGNORED,
    CONCURRENT_MODIFICATION,
    INCONSISTENT,
    LOCAL_NOT_FOUND,
    REMOTE_NOT_FOUND,
    REMOTE_UNAVAILABLE
}

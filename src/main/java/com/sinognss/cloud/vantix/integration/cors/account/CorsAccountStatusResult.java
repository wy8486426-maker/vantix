package com.sinognss.cloud.vantix.integration.cors.account;

import java.util.Objects;

public record CorsAccountStatusResult(
        CorsAccountQueryOutcome outcome,
        CorsAccountSnapshot snapshot,
        String errorCode,
        String errorMessage) {

    public CorsAccountStatusResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == CorsAccountQueryOutcome.SUCCESS && snapshot == null) {
            throw new IllegalArgumentException("SUCCESS requires a snapshot");
        }
        if (outcome != CorsAccountQueryOutcome.SUCCESS && snapshot != null) {
            throw new IllegalArgumentException("Only SUCCESS can contain a snapshot");
        }
    }

    public static CorsAccountStatusResult success(CorsAccountSnapshot snapshot) {
        return new CorsAccountStatusResult(CorsAccountQueryOutcome.SUCCESS,
                Objects.requireNonNull(snapshot, "snapshot must not be null"), null, null);
    }

    public static CorsAccountStatusResult notFound(String errorCode, String errorMessage) {
        return new CorsAccountStatusResult(CorsAccountQueryOutcome.NOT_FOUND, null, errorCode, errorMessage);
    }

    public static CorsAccountStatusResult unknown(String errorCode, String errorMessage) {
        return new CorsAccountStatusResult(CorsAccountQueryOutcome.UNKNOWN, null, errorCode, errorMessage);
    }
}

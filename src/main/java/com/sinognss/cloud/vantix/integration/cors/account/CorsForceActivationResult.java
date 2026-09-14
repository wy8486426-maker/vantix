package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;

import java.util.Objects;

public record CorsForceActivationResult(
        CorsOutcome outcome,
        String requestId,
        CorsAccountSnapshot account,
        String errorCode,
        String errorMessage) {

    public CorsForceActivationResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public static CorsForceActivationResult success(String requestId, CorsAccountSnapshot account) {
        return new CorsForceActivationResult(CorsOutcome.SUCCESS, requestId, account, null, null);
    }

    public static CorsForceActivationResult outcome(CorsOutcome outcome, String errorCode, String errorMessage) {
        return new CorsForceActivationResult(outcome, null, null, errorCode, errorMessage);
    }
}

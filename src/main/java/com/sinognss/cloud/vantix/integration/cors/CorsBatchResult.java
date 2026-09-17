package com.sinognss.cloud.vantix.integration.cors;

public record CorsBatchResult(CorsOutcome outcome, String requestId,
                              CorsAddAccountData data,
                              String errorCode, String errorMessage) {
    public static CorsBatchResult outcome(CorsOutcome outcome, String requestId,
                                          String errorCode, String errorMessage) {
        return new CorsBatchResult(outcome, requestId, null, errorCode, errorMessage);
    }

    public static CorsBatchResult success(String requestId, CorsAddAccountData data) {
        return new CorsBatchResult(CorsOutcome.SUCCESS, requestId, data, null, null);
    }
}

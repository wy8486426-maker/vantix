package com.sinognss.cloud.vantix.integration.cors;

import java.util.List;

public record CorsBatchResult(CorsOutcome outcome, String requestId,
                              List<CorsCreatedAccount> accounts,
                              String errorCode, String errorMessage) {
    public CorsBatchResult {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    public static CorsBatchResult outcome(CorsOutcome outcome, String requestId,
                                          String errorCode, String errorMessage) {
        return new CorsBatchResult(outcome, requestId, List.of(), errorCode, errorMessage);
    }
}

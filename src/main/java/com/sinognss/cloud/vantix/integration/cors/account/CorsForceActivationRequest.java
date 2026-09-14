package com.sinognss.cloud.vantix.integration.cors.account;

public record CorsForceActivationRequest(String requestId, String accountId) {
    public CorsForceActivationRequest {
        requireText(requestId, "requestId");
        requireText(accountId, "accountId");
        if (requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must be at most 128 characters");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

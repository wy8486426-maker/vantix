package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;

import java.util.Objects;

/** The renewal input sent to CORS; duration is taken from Vantix's immutable code snapshot. */
public record CorsAccountRenewalRequest(
        String requestId,
        String accountId,
        int durationValue,
        DurationUnit durationUnit) {

    public CorsAccountRenewalRequest {
        requireText(requestId, "requestId");
        requireText(accountId, "accountId");
        if (requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must be at most 128 characters");
        }
        if (hasControl(requestId) || hasControl(accountId)) {
            throw new IllegalArgumentException("requestId and accountId must not contain control characters");
        }
        if (durationValue <= 0) {
            throw new IllegalArgumentException("durationValue must be positive");
        }
        Objects.requireNonNull(durationUnit, "durationUnit must not be null");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}

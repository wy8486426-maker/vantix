package com.sinognss.cloud.vantix.integration.cors.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;

/** The renewal input sent to CORS; duration is taken from the immutable code snapshot. */
public record CorsAccountRenewalRequest(String requestId, String accountId, int durationDays) {
    public CorsAccountRenewalRequest {
        requireText(requestId, "requestId");
        requireText(accountId, "accountId");
        if (requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must be at most 128 characters");
        }
        if (hasControl(requestId) || hasControl(accountId)) {
            throw new IllegalArgumentException("requestId and accountId must not contain control characters");
        }
        if (durationDays <= 0) {
            throw new IllegalArgumentException("durationDays must be positive");
        }
    }

    @Deprecated
    @JsonIgnore
    public int durationValue() { return durationDays; }

    @Deprecated
    @JsonIgnore
    public DurationUnit durationUnit() { return DurationUnit.DAY; }

    /** Compatibility constructor for source callers using the retired unit contract. */
    @Deprecated
    public CorsAccountRenewalRequest(String requestId, String accountId, int durationValue,
                                     DurationUnit durationUnit) {
        this(requestId, accountId, LegacyDurationCompatibility.toDays(durationValue, durationUnit));
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

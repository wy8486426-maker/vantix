package com.sinognss.cloud.vantix.integration.cors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;

/** Day-based CORS batch-create request built from the frozen exchange snapshot. */
public record CorsBatchCreateRequest(String requestId, int durationDays, int silenceDays,
                                     int quantity, String accountPrefix) {
    public CorsBatchCreateRequest {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128
                || requestId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("requestId must be non-blank and at most 128 characters");
        }
        if (durationDays <= 0) {
            throw new IllegalArgumentException("durationDays must be positive");
        }
        if (silenceDays < 0) {
            throw new IllegalArgumentException("silenceDays must not be negative");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (accountPrefix != null && (accountPrefix.length() > 64
                || accountPrefix.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("accountPrefix is invalid");
        }
    }

    @Deprecated
    @JsonIgnore
    public int durationValue() { return durationDays; }

    @Deprecated
    @JsonIgnore
    public com.sinognss.cloud.vantix.domain.config.DurationUnit durationUnit() {
        return com.sinognss.cloud.vantix.domain.config.DurationUnit.DAY;
    }

    /** Compatibility constructor for source callers using the retired unit contract. */
    @Deprecated
    public CorsBatchCreateRequest(String requestId, int durationValue,
                                  com.sinognss.cloud.vantix.domain.config.DurationUnit durationUnit,
                                  int quantity, String accountPrefix) {
        this(requestId, com.sinognss.cloud.vantix.common.LegacyDurationCompatibility
                        .toDays(durationValue, Objects.requireNonNull(durationUnit)),
                0, quantity, accountPrefix);
    }
}

package com.sinognss.cloud.vantix.integration.cors;

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

}

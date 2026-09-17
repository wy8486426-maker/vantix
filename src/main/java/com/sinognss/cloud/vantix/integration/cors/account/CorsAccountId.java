package com.sinognss.cloud.vantix.integration.cors.account;

/** Validates and converts the CORS account identifier stored by Vantix. */
public final class CorsAccountId {
    private CorsAccountId() {
    }

    public static long parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("corsAccountId must not be blank");
        }
        String normalized = value.trim();
        try {
            long id = Long.parseLong(normalized);
            if (id <= 0) {
                throw new IllegalArgumentException("corsAccountId must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("corsAccountId must be a valid Long", exception);
        }
    }
}

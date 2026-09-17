package com.sinognss.cloud.vantix.integration.cors.account;

/** Request body for POST /BaseUser/userInfo/batch/renewal. */
public record CorsAccountRenewalRequest(java.util.List<Long> ids, int dayType, String requestId) {
    public CorsAccountRenewalRequest {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("ids must contain unique positive account IDs");
        }
        ids = java.util.List.copyOf(ids);
        if (dayType <= 0) {
            throw new IllegalArgumentException("dayType must be positive");
        }
        requireText(requestId, "requestId");
        if (requestId.length() > 128 || hasControl(requestId)) {
            throw new IllegalArgumentException("requestId must be at most 128 characters");
        }
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

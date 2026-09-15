package com.sinognss.cloud.vantix.integration.cors.account;

import java.time.OffsetDateTime;
import java.util.Objects;

public record CorsAccountSnapshot(
        String accountId,
        String account,
        String accountStatus,
        String activationStatus,
        OffsetDateTime activatedAt,
        OffsetDateTime expireAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public CorsAccountSnapshot {
        requireText(accountId, "accountId");
        requireText(account, "account");
        requireText(accountStatus, "accountStatus");
        requireText(activationStatus, "activationStatus");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

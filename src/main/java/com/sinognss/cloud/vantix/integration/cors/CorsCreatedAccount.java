package com.sinognss.cloud.vantix.integration.cors;

import java.time.OffsetDateTime;

public record CorsCreatedAccount(int index, String accountId, String account,
                                 String accountStatus, String activationStatus,
                                 OffsetDateTime activatedAt, OffsetDateTime expireAt,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}

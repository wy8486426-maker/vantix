package com.sinognss.cloud.vantix.application.renewal;

import java.time.LocalDateTime;

public record AccountRenewalView(
        Long renewalId,
        String requestId,
        Long serviceAccountId,
        Long serviceCodeId,
        String serviceType,
        Integer durationValue,
        String durationUnit,
        String status,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime accountExpireAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {
}

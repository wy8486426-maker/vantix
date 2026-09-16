package com.sinognss.cloud.vantix.application.renewal;

import java.time.LocalDateTime;

public record AccountRenewalView(
        Long renewalId,
        String requestId,
        Long serviceAccountId,
        Long serviceCodeId,
        String specCode,
        String serviceType,
        Integer durationDays,
        Integer codeSilenceDays,
        String status,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime accountExpireAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        String accountName,
        String corsAccountId,
        Long ownerCompanyId,
        String ownerCompanyName,
        Long assignedUserId,
        String serviceCode,
        String displayName) {
}

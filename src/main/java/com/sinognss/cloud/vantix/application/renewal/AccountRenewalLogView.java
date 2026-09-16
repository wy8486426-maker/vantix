package com.sinognss.cloud.vantix.application.renewal;

import java.time.LocalDateTime;

public record AccountRenewalLogView(
        Long renewalId,
        String requestId,
        Long serviceAccountId,
        String accountName,
        String corsAccountId,
        Long ownerCompanyId,
        String ownerCompanyName,
        Long assignedUserId,
        Long serviceCodeId,
        String serviceCode,
        String specCode,
        String displayName,
        String serviceType,
        Integer durationDays,
        String status,
        LocalDateTime currentAccountExpireAt,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {

    public static AccountRenewalLogView from(AccountRenewalLogQueryRow row) {
        return new AccountRenewalLogView(row.getRenewalId(), row.getRequestId(), row.getServiceAccountId(),
                row.getAccountName(), row.getCorsAccountId(), row.getOwnerCompanyId(), row.getOwnerCompanyName(),
                row.getAssignedUserId(), row.getServiceCodeId(), row.getServiceCode(), row.getSpecCode(),
                row.getDisplayName(), row.getServiceType(), row.getDurationDays(), row.getStatus(),
                row.getCurrentAccountExpireAt(), row.getLastErrorCode(), row.getLastErrorMessage(),
                row.getCreatedAt(), row.getUpdatedAt(), row.getCompletedAt());
    }
}

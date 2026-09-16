package com.sinognss.cloud.vantix.application.account;

import java.time.LocalDateTime;

public record ServiceAccountView(
        Long id,
        String corsAccountId,
        String accountName,
        String accountStatus,
        String activationStatus,
        String status,
        String specCode,
        String displayName,
        String serviceType,
        Integer durationDays,
        Long ownerCompanyId,
        String ownerCompanyName,
        Long assignedUserId,
        Long sourceServiceCodeId,
        String sourceServiceCode,
        Long exchangeBatchId,
        String exchangeBatchNo,
        String exchangeRequestId,
        LocalDateTime activatedAt,
        LocalDateTime expireAt,
        LocalDateTime corsCreatedAt,
        LocalDateTime corsUpdatedAt,
        LocalDateTime lastSyncAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ServiceAccountView from(ServiceAccountQueryRow row) {
        return new ServiceAccountView(row.getId(), row.getCorsAccountId(), row.getAccountName(),
                row.getAccountStatus(), row.getActivationStatus(), row.getStatus(), row.getSpecCode(),
                row.getDisplayName(), row.getServiceType(), row.getDurationDays(), row.getOwnerCompanyId(),
                row.getOwnerCompanyName(), row.getAssignedUserId(), row.getSourceServiceCodeId(),
                row.getSourceServiceCode(), row.getExchangeBatchId(), row.getExchangeBatchNo(),
                row.getExchangeRequestId(), row.getActivatedAt(), row.getExpireAt(), row.getCorsCreatedAt(),
                row.getCorsUpdatedAt(), row.getLastSyncAt(), row.getCreatedAt(), row.getUpdatedAt());
    }
}

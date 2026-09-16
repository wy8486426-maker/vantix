package com.sinognss.cloud.vantix.application.exchange;

import java.time.LocalDateTime;

public record ExchangeLogView(String requestId, Long batchId, String batchNo, Long ownerCompanyId,
                              String ownerCompanyName, String specCode, String displayName,
                              String serviceType, Integer durationDays, Integer quantity,
                              Long successQuantity, String status, String accountPrefix,
                              String generationSource, LocalDateTime createdAt,
                              LocalDateTime completedAt, String lastErrorCode, String lastErrorMessage) {
    public static ExchangeLogView from(ExchangeLogQueryRow row) {
        return new ExchangeLogView(row.getRequestId(), row.getBatchId(), row.getBatchNo(),
                row.getOwnerCompanyId(), row.getOwnerCompanyName(), row.getSpecCode(), row.getDisplayName(),
                row.getServiceType(), row.getDurationDays(), row.getQuantity(), row.getSuccessQuantity(),
                row.getStatus(), row.getAccountPrefix(), row.getGenerationSource(), row.getCreatedAt(),
                row.getCompletedAt(), row.getLastErrorCode(), row.getLastErrorMessage());
    }
}

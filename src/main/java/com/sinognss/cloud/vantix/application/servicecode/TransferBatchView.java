package com.sinognss.cloud.vantix.application.servicecode;

import java.time.LocalDateTime;

public record TransferBatchView(String transferNo, Long fromCompanyId, String fromCompanyName,
                                Long toCompanyId, String toCompanyName, String transferType,
                                Long quantity, String specCode, String serviceType,
                                Integer durationDays, String reason, Long operatorUserId,
                                String operatorUserName, LocalDateTime createdAt) {
    public static TransferBatchView from(TransferBatchQueryRow row) {
        return new TransferBatchView(row.getTransferNo(), row.getFromCompanyId(), row.getFromCompanyName(),
                row.getToCompanyId(), row.getToCompanyName(), row.getTransferType(), row.getQuantity(),
                row.getSpecCode(), row.getServiceType(), row.getDurationDays(), row.getReason(),
                row.getOperatorUserId(), row.getOperatorUserName(), row.getCreatedAt());
    }
}

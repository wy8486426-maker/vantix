package com.sinognss.cloud.vantix.application.servicecode;

import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeTransfer;
import com.sinognss.cloud.vantix.domain.servicecode.TransferType;

import java.time.LocalDateTime;

public record TransferView(Long id, String transferNo, Long serviceCodeId, String serviceCode,
                           Long fromCompanyId, Long toCompanyId, TransferType transferType,
                           String reason, Long operatorUserId, String operatorUserName, LocalDateTime createdAt) {
    public static TransferView from(ServiceCodeTransfer transfer) {
        return new TransferView(transfer.getId(), transfer.getTransferNo(), transfer.getServiceCodeId(),
                transfer.getServiceCode(), transfer.getFromCompanyId(), transfer.getToCompanyId(), transfer.getTransferType(),
                transfer.getReason(), transfer.getOperatorUserId(), transfer.getOperatorUserName(), transfer.getCreatedAt());
    }
}

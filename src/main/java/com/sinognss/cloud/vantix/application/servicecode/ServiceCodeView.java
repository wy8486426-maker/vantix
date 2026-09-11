package com.sinognss.cloud.vantix.application.servicecode;

import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;

import java.time.LocalDateTime;

public record ServiceCodeView(Long id, String code, Long sourceOrderId, String sourceOrderNo,
                              Long ownerCompanyId, String serviceType, Integer durationValue,
                              String durationUnit, Integer codeSilenceMonths, LocalDateTime expireAt,
                              ServiceCodeStatus status, DisplayStatus displayStatus,
                              String processingType, String processingRequestId, String consumeType,
                              LocalDateTime consumedAt, Long version, LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
    public static ServiceCodeView from(ServiceCode serviceCode, DisplayStatus displayStatus) {
        return new ServiceCodeView(serviceCode.getId(), serviceCode.getCode(), serviceCode.getSourceOrderId(),
                serviceCode.getSourceOrderNo(), serviceCode.getOwnerCompanyId(), serviceCode.getServiceType(),
                serviceCode.getDurationValue(), serviceCode.getDurationUnit(), serviceCode.getCodeSilenceMonths(),
                serviceCode.getExpireAt(), serviceCode.getStatus(), displayStatus,
                serviceCode.getProcessingType() == null ? null : serviceCode.getProcessingType().name(),
                serviceCode.getProcessingRequestId(),
                serviceCode.getConsumeType() == null ? null : serviceCode.getConsumeType().name(),
                serviceCode.getConsumedAt(), serviceCode.getVersion(), serviceCode.getCreatedAt(), serviceCode.getUpdatedAt());
    }
}

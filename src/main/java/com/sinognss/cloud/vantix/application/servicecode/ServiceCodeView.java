package com.sinognss.cloud.vantix.application.servicecode;

import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeBatchView;
import com.sinognss.cloud.vantix.common.DurationDisplayFormatter;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;

import java.time.LocalDateTime;

public record ServiceCodeView(Long id, String code, Long sourceOrderId, String sourceOrderNo,
                              Long ownerCompanyId, String serviceType, Integer durationValue,
                              String durationUnit, Integer codeSilenceMonths, LocalDateTime expireAt,
                              ServiceCodeStatus status, DisplayStatus displayStatus,
                              String processingType, String processingRequestId, String consumeType,
                              LocalDateTime consumedAt, Long version, LocalDateTime createdAt,
                              LocalDateTime updatedAt, Long generateBatchId, String batchNo,
                              String specCode, String displayName) {
    public static ServiceCodeView from(ServiceCode serviceCode, DisplayStatus displayStatus) {
        return from(serviceCode, displayStatus, null);
    }

    public static ServiceCodeView from(ServiceCode serviceCode, DisplayStatus displayStatus,
                                       ServiceCodeGenerateBatch batch) {
        String displayName = DurationDisplayFormatter.format(serviceCode.getDurationValue(),
                DurationDisplayFormatter.parseUnit(serviceCode.getDurationUnit()));
        String specCode = batch == null ? null : batch.getSpecCode();
        String batchNo = batch == null ? null : batch.getBatchNo();
        return new ServiceCodeView(serviceCode.getId(), serviceCode.getCode(), serviceCode.getSourceOrderId(),
                serviceCode.getSourceOrderNo(), serviceCode.getOwnerCompanyId(), serviceCode.getServiceType(),
                serviceCode.getDurationValue(), serviceCode.getDurationUnit(), serviceCode.getCodeSilenceMonths(),
                serviceCode.getExpireAt(), serviceCode.getStatus(), displayStatus,
                serviceCode.getProcessingType() == null ? null : serviceCode.getProcessingType().name(),
                serviceCode.getProcessingRequestId(),
                serviceCode.getConsumeType() == null ? null : serviceCode.getConsumeType().name(),
                serviceCode.getConsumedAt(), serviceCode.getVersion(), serviceCode.getCreatedAt(),
                serviceCode.getUpdatedAt(), serviceCode.getGenerateBatchId(), batchNo, specCode, displayName);
    }
}
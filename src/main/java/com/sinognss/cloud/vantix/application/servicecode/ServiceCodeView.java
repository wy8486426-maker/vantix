package com.sinognss.cloud.vantix.application.servicecode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;

import java.time.LocalDateTime;

public record ServiceCodeView(Long id, String code, Long sourceOrderId, String sourceOrderNo,
                              Long ownerCompanyId, String specCode, String serviceType,
                              Integer durationDays, Integer codeSilenceDays, LocalDateTime expireAt,
                              ServiceCodeStatus status, DisplayStatus displayStatus,
                              String processingType, String processingRequestId, String consumeType,
                              LocalDateTime consumedAt, Long version, LocalDateTime createdAt,
                              LocalDateTime updatedAt, Long generateBatchId, String batchNo,
                              String displayName) {
    public static ServiceCodeView from(ServiceCode serviceCode, DisplayStatus displayStatus) {
        return from(serviceCode, displayStatus, null);
    }

    public static ServiceCodeView from(ServiceCode serviceCode, DisplayStatus displayStatus,
                                       ServiceCodeGenerateBatch batch) {
        String specCode = serviceCode.getSpecCode() != null ? serviceCode.getSpecCode()
                : batch == null ? null : batch.getSpecCode();
        String displayName = batch == null ? null : batch.getDisplayName();
        return new ServiceCodeView(serviceCode.getId(), serviceCode.getCode(), serviceCode.getSourceOrderId(),
                serviceCode.getSourceOrderNo(), serviceCode.getOwnerCompanyId(), specCode,
                serviceCode.getServiceType(), serviceCode.getDurationDays(), serviceCode.getCodeSilenceDays(),
                serviceCode.getExpireAt(), serviceCode.getStatus(), displayStatus,
                serviceCode.getProcessingType() == null ? null : serviceCode.getProcessingType().name(),
                serviceCode.getProcessingRequestId(),
                serviceCode.getConsumeType() == null ? null : serviceCode.getConsumeType().name(),
                serviceCode.getConsumedAt(), serviceCode.getVersion(), serviceCode.getCreatedAt(),
                serviceCode.getUpdatedAt(), serviceCode.getGenerateBatchId(), batchNo(batch), displayName);
    }

    private static String batchNo(ServiceCodeGenerateBatch batch) {
        return batch == null ? null : batch.getBatchNo();
    }

    /** Legacy aliases are retained only for source compatibility and not serialized. */
    @Deprecated
    @JsonIgnore
    public Integer durationValue() { return durationDays; }

    @Deprecated
    @JsonIgnore
    public String durationUnit() { return durationDays == null ? null : "DAY"; }

    @Deprecated
    @JsonIgnore
    public Integer codeSilenceMonths() { return codeSilenceDays == null ? null : codeSilenceDays / 30; }
}

package com.sinognss.cloud.vantix.application.servicecode;

import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;

import java.time.LocalDateTime;

/** Database projection used by the service-code frontend query endpoints. */
public class ServiceCodeQueryRow {
    private Long id;
    private String code;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Long generateBatchId;
    private Long ownerCompanyId;
    private String ownerCompanyName;
    private String specCode;
    private String serviceType;
    private Integer durationDays;
    private Integer codeSilenceDays;
    private LocalDateTime expireAt;
    private ServiceCodeStatus status;
    private String processingType;
    private String processingRequestId;
    private String consumeType;
    private LocalDateTime consumedAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String batchNo;
    private String displayName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getSourceOrderId() { return sourceOrderId; }
    public void setSourceOrderId(Long sourceOrderId) { this.sourceOrderId = sourceOrderId; }
    public String getSourceOrderNo() { return sourceOrderNo; }
    public void setSourceOrderNo(String sourceOrderNo) { this.sourceOrderNo = sourceOrderNo; }
    public Long getGenerateBatchId() { return generateBatchId; }
    public void setGenerateBatchId(Long generateBatchId) { this.generateBatchId = generateBatchId; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public String getOwnerCompanyName() { return ownerCompanyName; }
    public void setOwnerCompanyName(String ownerCompanyName) { this.ownerCompanyName = ownerCompanyName; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getCodeSilenceDays() { return codeSilenceDays; }
    public void setCodeSilenceDays(Integer codeSilenceDays) { this.codeSilenceDays = codeSilenceDays; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public ServiceCodeStatus getStatus() { return status; }
    public void setStatus(ServiceCodeStatus status) { this.status = status; }
    public String getProcessingType() { return processingType; }
    public void setProcessingType(String processingType) { this.processingType = processingType; }
    public String getProcessingRequestId() { return processingRequestId; }
    public void setProcessingRequestId(String processingRequestId) { this.processingRequestId = processingRequestId; }
    public String getConsumeType() { return consumeType; }
    public void setConsumeType(String consumeType) { this.consumeType = consumeType; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}

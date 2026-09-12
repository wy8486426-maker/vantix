package com.sinognss.cloud.vantix.domain.servicecode;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("service_code")
public class ServiceCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Long generateBatchId;
    private Long ownerCompanyId;
    private String serviceType;
    private Integer durationValue;
    private String durationUnit;
    private Integer codeSilenceMonths;
    private LocalDateTime expireAt;
    private ServiceCodeStatus status;
    private ProcessingType processingType;
    private String processingRequestId;
    private ConsumeType consumeType;
    private LocalDateTime consumedAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getSourceOrderId() { return sourceOrderId; }
    public void setSourceOrderId(Long sourceOrderId) { this.sourceOrderId = sourceOrderId; }
    public String getSourceOrderNo() { return sourceOrderNo; }
    public Long getGenerateBatchId() { return generateBatchId; }
    public void setGenerateBatchId(Long generateBatchId) { this.generateBatchId = generateBatchId; }
    public void setSourceOrderNo(String sourceOrderNo) { this.sourceOrderNo = sourceOrderNo; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
    public Integer getCodeSilenceMonths() { return codeSilenceMonths; }
    public void setCodeSilenceMonths(Integer codeSilenceMonths) { this.codeSilenceMonths = codeSilenceMonths; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public ServiceCodeStatus getStatus() { return status; }
    public void setStatus(ServiceCodeStatus status) { this.status = status; }
    public ProcessingType getProcessingType() { return processingType; }
    public void setProcessingType(ProcessingType processingType) { this.processingType = processingType; }
    public String getProcessingRequestId() { return processingRequestId; }
    public void setProcessingRequestId(String processingRequestId) { this.processingRequestId = processingRequestId; }
    public ConsumeType getConsumeType() { return consumeType; }
    public void setConsumeType(ConsumeType consumeType) { this.consumeType = consumeType; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.sinognss.cloud.vantix.application.exchange;

import java.time.LocalDateTime;

public class ExchangeLogQueryRow {
    private Long batchId;
    private String batchNo;
    private String requestId;
    private Long ownerCompanyId;
    private String ownerCompanyName;
    private String specCode;
    private String displayName;
    private String serviceType;
    private Integer durationDays;
    private Integer quantity;
    private Long successQuantity;
    private String status;
    private String accountPrefix;
    private String generationSource;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String lastErrorCode;
    private String lastErrorMessage;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public String getOwnerCompanyName() { return ownerCompanyName; }
    public void setOwnerCompanyName(String ownerCompanyName) { this.ownerCompanyName = ownerCompanyName; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getSuccessQuantity() { return successQuantity; }
    public void setSuccessQuantity(Long successQuantity) { this.successQuantity = successQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAccountPrefix() { return accountPrefix; }
    public void setAccountPrefix(String accountPrefix) { this.accountPrefix = accountPrefix; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
}

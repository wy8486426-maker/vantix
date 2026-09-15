package com.sinognss.cloud.vantix.application.servicecode;

import java.time.LocalDateTime;

public class TransferBatchQueryRow {
    private String transferNo;
    private Long fromCompanyId;
    private String fromCompanyName;
    private Long toCompanyId;
    private String toCompanyName;
    private String transferType;
    private Long quantity;
    private String specCode;
    private String serviceType;
    private Integer durationDays;
    private String reason;
    private Long operatorUserId;
    private String operatorUserName;
    private LocalDateTime createdAt;

    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
    public Long getFromCompanyId() { return fromCompanyId; }
    public void setFromCompanyId(Long fromCompanyId) { this.fromCompanyId = fromCompanyId; }
    public String getFromCompanyName() { return fromCompanyName; }
    public void setFromCompanyName(String fromCompanyName) { this.fromCompanyName = fromCompanyName; }
    public Long getToCompanyId() { return toCompanyId; }
    public void setToCompanyId(Long toCompanyId) { this.toCompanyId = toCompanyId; }
    public String getToCompanyName() { return toCompanyName; }
    public void setToCompanyName(String toCompanyName) { this.toCompanyName = toCompanyName; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }
    public Long getQuantity() { return quantity; }
    public void setQuantity(Long quantity) { this.quantity = quantity; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

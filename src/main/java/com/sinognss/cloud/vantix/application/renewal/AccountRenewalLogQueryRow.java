package com.sinognss.cloud.vantix.application.renewal;

import java.time.LocalDateTime;

public class AccountRenewalLogQueryRow {
    private Long renewalId;
    private String requestId;
    private Long serviceAccountId;
    private String accountName;
    private String corsAccountId;
    private Long ownerCompanyId;
    private String ownerCompanyName;
    private Long assignedUserId;
    private Long serviceCodeId;
    private String serviceCode;
    private String specCode;
    private String displayName;
    private String serviceType;
    private Integer durationDays;
    private Integer codeSilenceDays;
    private String status;
    private LocalDateTime currentAccountExpireAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public Long getRenewalId() { return renewalId; }
    public void setRenewalId(Long renewalId) { this.renewalId = renewalId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getServiceAccountId() { return serviceAccountId; }
    public void setServiceAccountId(Long serviceAccountId) { this.serviceAccountId = serviceAccountId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getCorsAccountId() { return corsAccountId; }
    public void setCorsAccountId(String corsAccountId) { this.corsAccountId = corsAccountId; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public String getOwnerCompanyName() { return ownerCompanyName; }
    public void setOwnerCompanyName(String ownerCompanyName) { this.ownerCompanyName = ownerCompanyName; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public Long getServiceCodeId() { return serviceCodeId; }
    public void setServiceCodeId(Long serviceCodeId) { this.serviceCodeId = serviceCodeId; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getCodeSilenceDays() { return codeSilenceDays; }
    public void setCodeSilenceDays(Integer codeSilenceDays) { this.codeSilenceDays = codeSilenceDays; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCurrentAccountExpireAt() { return currentAccountExpireAt; }
    public void setCurrentAccountExpireAt(LocalDateTime currentAccountExpireAt) {
        this.currentAccountExpireAt = currentAccountExpireAt;
    }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}

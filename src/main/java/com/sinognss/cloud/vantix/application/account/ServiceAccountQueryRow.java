package com.sinognss.cloud.vantix.application.account;

import com.sinognss.cloud.vantix.domain.account.AccountSource;
import java.time.LocalDateTime;

public class ServiceAccountQueryRow {
    private Long id;
    private String corsAccountId;
    private String accountName;
    private String accountStatus;
    private String activationStatus;
    private String status;
    private String specCode;
    private String displayName;
    private String serviceType;
    private Integer durationDays;
    private Long ownerCompanyId;
    private String ownerCompanyName;
    private Long assignedUserId;
    private AccountSource accountSource;
    private Long sourceServiceCodeId;
    private String sourceServiceCode;
    private Long exchangeBatchId;
    private String exchangeBatchNo;
    private String exchangeRequestId;
    private LocalDateTime exchangeAt;
    private LocalDateTime activatedAt;
    private LocalDateTime expireAt;
    private LocalDateTime corsCreatedAt;
    private LocalDateTime corsUpdatedAt;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCorsAccountId() { return corsAccountId; }
    public void setCorsAccountId(String corsAccountId) { this.corsAccountId = corsAccountId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    public String getActivationStatus() { return activationStatus; }
    public void setActivationStatus(String activationStatus) { this.activationStatus = activationStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public String getOwnerCompanyName() { return ownerCompanyName; }
    public void setOwnerCompanyName(String ownerCompanyName) { this.ownerCompanyName = ownerCompanyName; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public AccountSource getAccountSource() { return accountSource; }
    public void setAccountSource(AccountSource accountSource) { this.accountSource = accountSource; }
    public Long getSourceServiceCodeId() { return sourceServiceCodeId; }
    public void setSourceServiceCodeId(Long sourceServiceCodeId) { this.sourceServiceCodeId = sourceServiceCodeId; }
    public String getSourceServiceCode() { return sourceServiceCode; }
    public void setSourceServiceCode(String sourceServiceCode) { this.sourceServiceCode = sourceServiceCode; }
    public Long getExchangeBatchId() { return exchangeBatchId; }
    public void setExchangeBatchId(Long exchangeBatchId) { this.exchangeBatchId = exchangeBatchId; }
    public String getExchangeBatchNo() { return exchangeBatchNo; }
    public void setExchangeBatchNo(String exchangeBatchNo) { this.exchangeBatchNo = exchangeBatchNo; }
    public String getExchangeRequestId() { return exchangeRequestId; }
    public void setExchangeRequestId(String exchangeRequestId) { this.exchangeRequestId = exchangeRequestId; }
    public LocalDateTime getExchangeAt() { return exchangeAt; }
    public void setExchangeAt(LocalDateTime exchangeAt) { this.exchangeAt = exchangeAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getCorsCreatedAt() { return corsCreatedAt; }
    public void setCorsCreatedAt(LocalDateTime corsCreatedAt) { this.corsCreatedAt = corsCreatedAt; }
    public LocalDateTime getCorsUpdatedAt() { return corsUpdatedAt; }
    public void setCorsUpdatedAt(LocalDateTime corsUpdatedAt) { this.corsUpdatedAt = corsUpdatedAt; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

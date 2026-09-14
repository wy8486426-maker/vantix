package com.sinognss.cloud.vantix.domain.account;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Local account asset skeleton. Passwords are intentionally never persisted here. */
@TableName("service_account")
public class ServiceAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String corsAccountId;
    private String account;
    private Long ownerCompanyId;
    private Long assignedUserId;
    private Long sourceServiceCodeId;
    private Long exchangeBatchId;
    private Long exchangeDetailId;
    private String serviceType;
    private Integer durationValue;
    private String durationUnit;
    private Integer accountSilenceMonths;
    private LocalDateTime exchangeAt;
    private LocalDateTime forceActivateAt;
    private String corsStatus;
    private String corsActivationStatus;
    private LocalDateTime activatedAt;
    private LocalDateTime expireAt;
    private LocalDateTime corsCreatedAt;
    private LocalDateTime corsUpdatedAt;
    private LocalDateTime lastSyncAt;
    private LocalDateTime statusSyncNextAt;
    private LocalDateTime statusSyncLastAttemptAt;
    private Integer statusSyncFailureCount;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCorsAccountId() { return corsAccountId; }
    public void setCorsAccountId(String corsAccountId) { this.corsAccountId = corsAccountId; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public Long getSourceServiceCodeId() { return sourceServiceCodeId; }
    public void setSourceServiceCodeId(Long sourceServiceCodeId) { this.sourceServiceCodeId = sourceServiceCodeId; }
    public Long getExchangeBatchId() { return exchangeBatchId; }
    public void setExchangeBatchId(Long exchangeBatchId) { this.exchangeBatchId = exchangeBatchId; }
    public Long getExchangeDetailId() { return exchangeDetailId; }
    public void setExchangeDetailId(Long exchangeDetailId) { this.exchangeDetailId = exchangeDetailId; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
    public Integer getAccountSilenceMonths() { return accountSilenceMonths; }
    public void setAccountSilenceMonths(Integer accountSilenceMonths) { this.accountSilenceMonths = accountSilenceMonths; }
    public LocalDateTime getExchangeAt() { return exchangeAt; }
    public void setExchangeAt(LocalDateTime exchangeAt) { this.exchangeAt = exchangeAt; }
    public LocalDateTime getForceActivateAt() { return forceActivateAt; }
    public void setForceActivateAt(LocalDateTime forceActivateAt) { this.forceActivateAt = forceActivateAt; }
    public String getCorsStatus() { return corsStatus; }
    public void setCorsStatus(String corsStatus) { this.corsStatus = corsStatus; }
    public String getCorsActivationStatus() { return corsActivationStatus; }
    public void setCorsActivationStatus(String corsActivationStatus) { this.corsActivationStatus = corsActivationStatus; }
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
    public LocalDateTime getStatusSyncNextAt() { return statusSyncNextAt; }
    public void setStatusSyncNextAt(LocalDateTime statusSyncNextAt) { this.statusSyncNextAt = statusSyncNextAt; }
    public LocalDateTime getStatusSyncLastAttemptAt() { return statusSyncLastAttemptAt; }
    public void setStatusSyncLastAttemptAt(LocalDateTime statusSyncLastAttemptAt) { this.statusSyncLastAttemptAt = statusSyncLastAttemptAt; }
    public Integer getStatusSyncFailureCount() { return statusSyncFailureCount; }
    public void setStatusSyncFailureCount(Integer statusSyncFailureCount) { this.statusSyncFailureCount = statusSyncFailureCount; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

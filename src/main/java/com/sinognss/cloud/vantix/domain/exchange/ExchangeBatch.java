package com.sinognss.cloud.vantix.domain.exchange;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;

import java.time.LocalDateTime;

@TableName("exchange_batch")
public class ExchangeBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String exchangeBatchNo;
    private String requestId;
    private Long ownerCompanyId;
    private Long assignedUserId;
    private String generationSource;
    private String specCode;
    private String serviceType;
    private Integer durationDays;
    private Integer accountSilenceDays;
    /** Legacy columns retained for compatibility reads only. */
    private Integer durationValue;
    private String durationUnit;
    private Integer quantity;
    private String accountPrefix;
    private String payloadHash;
    private Integer accountSilenceMonths;
    private ExchangeStatus status;
    private Long operatorUserId;
    private String operatorUserName;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExchangeBatchNo() { return exchangeBatchNo; }
    public void setExchangeBatchNo(String exchangeBatchNo) { this.exchangeBatchNo = exchangeBatchNo; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    /** New rows always have this value; the fallback only keeps pre-V9 reads usable. */
    public Integer getDurationDays() {
        return durationDays != null ? durationDays : LegacyDurationCompatibility.toDays(durationValue, durationUnit);
    }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    /** New rows always have this value; the fallback only keeps pre-V9 reads usable. */
    public Integer getAccountSilenceDays() {
        return accountSilenceDays != null ? accountSilenceDays
                : LegacyDurationCompatibility.monthsToDays(accountSilenceMonths);
    }
    public void setAccountSilenceDays(Integer accountSilenceDays) { this.accountSilenceDays = accountSilenceDays; }
    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getAccountPrefix() { return accountPrefix; }
    public void setAccountPrefix(String accountPrefix) { this.accountPrefix = accountPrefix; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public Integer getAccountSilenceMonths() { return accountSilenceMonths; }
    public void setAccountSilenceMonths(Integer accountSilenceMonths) { this.accountSilenceMonths = accountSilenceMonths; }
    public ExchangeStatus getStatus() { return status; }
    public void setStatus(ExchangeStatus status) { this.status = status; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

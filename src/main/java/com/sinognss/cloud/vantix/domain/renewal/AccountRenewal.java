package com.sinognss.cloud.vantix.domain.renewal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;

import java.time.LocalDateTime;

@TableName("account_renewal")
public class AccountRenewal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long serviceAccountId;
    private Long serviceCodeId;
    private Long ownerCompanyId;
    private Long assignedUserId;
    private String specCode;
    private String serviceType;
    private Integer durationDays;
    private Integer codeSilenceDays;
    /** Legacy columns retained for compatibility reads only. */
    private Integer durationValue;
    private String durationUnit;
    private String serviceCodeSnapshot;
    private String requestId;
    private String status;
    private Long operatorUserId;
    private String operatorUserName;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServiceAccountId() { return serviceAccountId; }
    public void setServiceAccountId(Long serviceAccountId) { this.serviceAccountId = serviceAccountId; }
    public Long getServiceCodeId() { return serviceCodeId; }
    public void setServiceCodeId(Long serviceCodeId) { this.serviceCodeId = serviceCodeId; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    /** New rows always have this value; the fallback only keeps pre-V9 reads usable. */
    public Integer getDurationDays() {
        return durationDays != null ? durationDays : LegacyDurationCompatibility.toDays(durationValue, durationUnit);
    }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getCodeSilenceDays() { return codeSilenceDays; }
    public void setCodeSilenceDays(Integer codeSilenceDays) { this.codeSilenceDays = codeSilenceDays; }
    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
    public String getServiceCodeSnapshot() { return serviceCodeSnapshot; }
    public void setServiceCodeSnapshot(String serviceCodeSnapshot) { this.serviceCodeSnapshot = serviceCodeSnapshot; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
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
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

package com.sinognss.cloud.vantix.domain.password;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Audit metadata for password operations. This entity deliberately has no credential fields. */
@TableName("account_password_action")
public class AccountPasswordAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String actionType;
    private Long serviceAccountId;
    private Long ownerCompanyId;
    private Long assignedUserId;
    private String corsAccountId;
    private String account;
    private String status;
    private Long operatorUserId;
    private String operatorUserName;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
    private Long activeResetAccountId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Long getServiceAccountId() { return serviceAccountId; }
    public void setServiceAccountId(Long serviceAccountId) { this.serviceAccountId = serviceAccountId; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public String getCorsAccountId() { return corsAccountId; }
    public void setCorsAccountId(String corsAccountId) { this.corsAccountId = corsAccountId; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
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
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Long getActiveResetAccountId() { return activeResetAccountId; }
    public void setActiveResetAccountId(Long activeResetAccountId) { this.activeResetAccountId = activeResetAccountId; }
}

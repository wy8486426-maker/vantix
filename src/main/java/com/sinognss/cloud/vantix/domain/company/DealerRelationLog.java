package com.sinognss.cloud.vantix.domain.company;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("dealer_relation_log")
public class DealerRelationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Long oldParentCompanyId;
    private Long newParentCompanyId;
    private Long operatorUserId;
    private String operatorUserName;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getOldParentCompanyId() { return oldParentCompanyId; }
    public void setOldParentCompanyId(Long oldParentCompanyId) { this.oldParentCompanyId = oldParentCompanyId; }
    public Long getNewParentCompanyId() { return newParentCompanyId; }
    public void setNewParentCompanyId(Long newParentCompanyId) { this.newParentCompanyId = newParentCompanyId; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

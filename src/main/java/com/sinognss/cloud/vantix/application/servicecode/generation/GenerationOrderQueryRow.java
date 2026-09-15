package com.sinognss.cloud.vantix.application.servicecode.generation;

import java.time.LocalDateTime;

public class GenerationOrderQueryRow {
    private Long id;
    private String requestId;
    private String generationSource;
    private String sourceOrderNo;
    private LocalDateTime sourceOrderTime;
    private Long ownerCompanyId;
    private String ownerCompanyName;
    private Integer itemCount;
    private Integer totalQuantity;
    private String status;
    private Long operatorUserId;
    private String operatorUserName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
    public String getSourceOrderNo() { return sourceOrderNo; }
    public void setSourceOrderNo(String sourceOrderNo) { this.sourceOrderNo = sourceOrderNo; }
    public LocalDateTime getSourceOrderTime() { return sourceOrderTime; }
    public void setSourceOrderTime(LocalDateTime sourceOrderTime) { this.sourceOrderTime = sourceOrderTime; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public String getOwnerCompanyName() { return ownerCompanyName; }
    public void setOwnerCompanyName(String ownerCompanyName) { this.ownerCompanyName = ownerCompanyName; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

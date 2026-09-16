package com.sinognss.cloud.vantix.domain.exchange;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("exchange_detail")
public class ExchangeDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long exchangeBatchId;
    private Integer detailIndex;
    private String serviceCodeSnapshot;
    private Long serviceCodeId;
    private Long activeServiceCodeId;
    private String requestId;
    private String corsAccountId;
    private String account;
    private ExchangeStatus status;
    private LocalDateTime completedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExchangeBatchId() { return exchangeBatchId; }
    public void setExchangeBatchId(Long exchangeBatchId) { this.exchangeBatchId = exchangeBatchId; }
    public Integer getDetailIndex() { return detailIndex; }
    public void setDetailIndex(Integer detailIndex) { this.detailIndex = detailIndex; }
    public String getServiceCodeSnapshot() { return serviceCodeSnapshot; }
    public void setServiceCodeSnapshot(String serviceCodeSnapshot) { this.serviceCodeSnapshot = serviceCodeSnapshot; }
    public Long getServiceCodeId() { return serviceCodeId; }
    public void setServiceCodeId(Long serviceCodeId) { this.serviceCodeId = serviceCodeId; }
    public Long getActiveServiceCodeId() { return activeServiceCodeId; }
    public void setActiveServiceCodeId(Long activeServiceCodeId) { this.activeServiceCodeId = activeServiceCodeId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getCorsAccountId() { return corsAccountId; }
    public void setCorsAccountId(String corsAccountId) { this.corsAccountId = corsAccountId; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public ExchangeStatus getStatus() { return status; }
    public void setStatus(ExchangeStatus status) { this.status = status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

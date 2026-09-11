package com.sinognss.cloud.vantix.domain.servicecode;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("service_code_transfer")
public class ServiceCodeTransfer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transferNo;
    private Long serviceCodeId;
    private String serviceCode;
    private Long fromCompanyId;
    private Long toCompanyId;
    private TransferType transferType;
    private String reason;
    private Long operatorUserId;
    private String operatorUserName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
    public Long getServiceCodeId() { return serviceCodeId; }
    public void setServiceCodeId(Long serviceCodeId) { this.serviceCodeId = serviceCodeId; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public Long getFromCompanyId() { return fromCompanyId; }
    public void setFromCompanyId(Long fromCompanyId) { this.fromCompanyId = fromCompanyId; }
    public Long getToCompanyId() { return toCompanyId; }
    public void setToCompanyId(Long toCompanyId) { this.toCompanyId = toCompanyId; }
    public TransferType getTransferType() { return transferType; }
    public void setTransferType(TransferType transferType) { this.transferType = transferType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

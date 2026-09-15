package com.sinognss.cloud.vantix.domain.servicecode;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;

import java.time.LocalDateTime;

@TableName("service_code_generate_batch")
public class ServiceCodeGenerateBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String requestId;
    private GenerationSource generationSource;
    private String sourceOrderNo;
    private LocalDateTime sourceOrderTime;
    private Long ownerCompanyId;
    private Long generateOrderId;
    private String specCode;
    private String displayName;
    private String serviceType;
    private Integer durationDays;
    private Integer codeSilenceDays;
    /** Legacy columns retained for compatibility reads only. */
    private Integer durationValue;
    private String durationUnit;
    private Integer codeSilenceMonths;
    private Integer quantity;
    private Integer generatedCount;
    private String status;
    private String remark;
    private Long operatorUserId;
    private String operatorUserName;
    private String businessKeyHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public GenerationSource getGenerationSource() { return generationSource; }
    public void setGenerationSource(GenerationSource generationSource) { this.generationSource = generationSource; }
    public String getSourceOrderNo() { return sourceOrderNo; }
    public void setSourceOrderNo(String sourceOrderNo) { this.sourceOrderNo = sourceOrderNo; }
    public LocalDateTime getSourceOrderTime() { return sourceOrderTime; }
    public void setSourceOrderTime(LocalDateTime sourceOrderTime) { this.sourceOrderTime = sourceOrderTime; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public void setOwnerCompanyId(Long ownerCompanyId) { this.ownerCompanyId = ownerCompanyId; }
    public Long getGenerateOrderId() { return generateOrderId; }
    public void setGenerateOrderId(Long generateOrderId) { this.generateOrderId = generateOrderId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    /** New rows always have this value; the fallback only keeps pre-V9 reads usable. */
    public Integer getDurationDays() {
        return durationDays != null ? durationDays : LegacyDurationCompatibility.toDays(durationValue, durationUnit);
    }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    /** New rows always have this value; the fallback only keeps pre-V9 reads usable. */
    public Integer getCodeSilenceDays() {
        return codeSilenceDays != null ? codeSilenceDays : LegacyDurationCompatibility.monthsToDays(codeSilenceMonths);
    }
    public void setCodeSilenceDays(Integer codeSilenceDays) { this.codeSilenceDays = codeSilenceDays; }
    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
    public Integer getCodeSilenceMonths() { return codeSilenceMonths; }
    public void setCodeSilenceMonths(Integer codeSilenceMonths) { this.codeSilenceMonths = codeSilenceMonths; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(Integer generatedCount) { this.generatedCount = generatedCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
    public String getBusinessKeyHash() { return businessKeyHash; }
    public void setBusinessKeyHash(String businessKeyHash) { this.businessKeyHash = businessKeyHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

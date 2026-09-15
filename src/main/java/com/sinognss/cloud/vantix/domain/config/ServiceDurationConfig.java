package com.sinognss.cloud.vantix.domain.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;

import java.time.LocalDateTime;

@TableName("service_duration_config")
public class ServiceDurationConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String specCode;
    private String displayName;
    private String serviceType;
    private Integer durationDays;
    private Integer codeSilenceDays;
    private Integer accountSilenceDays;
    /** Legacy columns retained for compatibility reads only. */
    private Integer durationValue;
    private DurationUnit durationUnit;
    private Integer codeSilenceMonths;
    private Boolean enabled;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public void setId(Long id) { this.id = id; }
    /** New rows always have this value; the fallback only keeps pre-V9 reads usable. */
    public String getDisplayName() {
        return displayName != null ? displayName : LegacyDurationCompatibility.displayName(durationValue, durationUnit);
    }
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
    public Integer getAccountSilenceDays() { return accountSilenceDays; }
    public void setAccountSilenceDays(Integer accountSilenceDays) { this.accountSilenceDays = accountSilenceDays; }
    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
    public DurationUnit getDurationUnit() { return durationUnit; }
    public void setDurationUnit(DurationUnit durationUnit) { this.durationUnit = durationUnit; }
    public Integer getCodeSilenceMonths() { return codeSilenceMonths; }
    public void setCodeSilenceMonths(Integer codeSilenceMonths) { this.codeSilenceMonths = codeSilenceMonths; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

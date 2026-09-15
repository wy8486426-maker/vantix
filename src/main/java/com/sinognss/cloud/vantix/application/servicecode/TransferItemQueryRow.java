package com.sinognss.cloud.vantix.application.servicecode;

import java.time.LocalDateTime;

public class TransferItemQueryRow {
    private Long serviceCodeId;
    private String serviceCode;
    private String specCode;
    private String displayName;
    private String serviceType;
    private Integer durationDays;
    private LocalDateTime expireAt;

    public Long getServiceCodeId() { return serviceCodeId; }
    public void setServiceCodeId(Long serviceCodeId) { this.serviceCodeId = serviceCodeId; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
}

package com.sinognss.cloud.vantix.application.exchange;

public class ExchangeLogItemQueryRow {
    private Long serviceCodeId;
    private String serviceCode;
    private Long serviceAccountId;
    private String corsAccountId;
    private String accountName;
    private String status;

    public Long getServiceCodeId() { return serviceCodeId; }
    public void setServiceCodeId(Long serviceCodeId) { this.serviceCodeId = serviceCodeId; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public Long getServiceAccountId() { return serviceAccountId; }
    public void setServiceAccountId(Long serviceAccountId) { this.serviceAccountId = serviceAccountId; }
    public String getCorsAccountId() { return corsAccountId; }
    public void setCorsAccountId(String corsAccountId) { this.corsAccountId = corsAccountId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

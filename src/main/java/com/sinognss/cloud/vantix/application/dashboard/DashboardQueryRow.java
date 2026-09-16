package com.sinognss.cloud.vantix.application.dashboard;

public class DashboardQueryRow {
    private Long serviceCodeTotal;
    private Long serviceCodeWaiting;
    private Long serviceCodeExpiring;
    private Long serviceCodeExpired;
    private Long serviceCodeProcessing;
    private Long serviceCodeConsumed;
    private Long accountTotal;
    private Long accountWaiting;
    private Long accountActive;
    private Long accountExpired;
    private Long accountDisabled;
    private Long generationOrderTotal;
    private Long exchangeTotal;
    private Long renewalTotal;
    private Long transferTotal;

    public Long getServiceCodeTotal() { return serviceCodeTotal; }
    public void setServiceCodeTotal(Long value) { serviceCodeTotal = value; }
    public Long getServiceCodeWaiting() { return serviceCodeWaiting; }
    public void setServiceCodeWaiting(Long value) { serviceCodeWaiting = value; }
    public Long getServiceCodeExpiring() { return serviceCodeExpiring; }
    public void setServiceCodeExpiring(Long value) { serviceCodeExpiring = value; }
    public Long getServiceCodeExpired() { return serviceCodeExpired; }
    public void setServiceCodeExpired(Long value) { serviceCodeExpired = value; }
    public Long getServiceCodeProcessing() { return serviceCodeProcessing; }
    public void setServiceCodeProcessing(Long value) { serviceCodeProcessing = value; }
    public Long getServiceCodeConsumed() { return serviceCodeConsumed; }
    public void setServiceCodeConsumed(Long value) { serviceCodeConsumed = value; }
    public Long getAccountTotal() { return accountTotal; }
    public void setAccountTotal(Long value) { accountTotal = value; }
    public Long getAccountWaiting() { return accountWaiting; }
    public void setAccountWaiting(Long value) { accountWaiting = value; }
    public Long getAccountActive() { return accountActive; }
    public void setAccountActive(Long value) { accountActive = value; }
    public Long getAccountExpired() { return accountExpired; }
    public void setAccountExpired(Long value) { accountExpired = value; }
    public Long getAccountDisabled() { return accountDisabled; }
    public void setAccountDisabled(Long value) { accountDisabled = value; }
    public Long getGenerationOrderTotal() { return generationOrderTotal; }
    public void setGenerationOrderTotal(Long value) { generationOrderTotal = value; }
    public Long getExchangeTotal() { return exchangeTotal; }
    public void setExchangeTotal(Long value) { exchangeTotal = value; }
    public Long getRenewalTotal() { return renewalTotal; }
    public void setRenewalTotal(Long value) { renewalTotal = value; }
    public Long getTransferTotal() { return transferTotal; }
    public void setTransferTotal(Long value) { transferTotal = value; }
}

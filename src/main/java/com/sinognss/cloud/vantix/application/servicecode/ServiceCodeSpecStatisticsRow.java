package com.sinognss.cloud.vantix.application.servicecode;

/** Conditional-aggregation result grouped by service-code specification. */
public class ServiceCodeSpecStatisticsRow {
    private String specCode;
    private String displayName;
    private Integer durationDays;
    private Long total;
    private Long waiting;
    private Long expiring;
    private Long processing;
    private Long consumed;
    private Long expired;

    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Long getWaiting() { return waiting; }
    public void setWaiting(Long waiting) { this.waiting = waiting; }
    public Long getExpiring() { return expiring; }
    public void setExpiring(Long expiring) { this.expiring = expiring; }
    public Long getProcessing() { return processing; }
    public void setProcessing(Long processing) { this.processing = processing; }
    public Long getConsumed() { return consumed; }
    public void setConsumed(Long consumed) { this.consumed = consumed; }
    public Long getExpired() { return expired; }
    public void setExpired(Long expired) { this.expired = expired; }
}

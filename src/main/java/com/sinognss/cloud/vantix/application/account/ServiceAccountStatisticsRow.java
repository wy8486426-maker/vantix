package com.sinognss.cloud.vantix.application.account;

public class ServiceAccountStatisticsRow {
    private Long total;
    private Long waiting;
    private Long active;
    private Long expired;
    private Long disabled;

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Long getWaiting() { return waiting; }
    public void setWaiting(Long waiting) { this.waiting = waiting; }
    public Long getActive() { return active; }
    public void setActive(Long active) { this.active = active; }
    public Long getExpired() { return expired; }
    public void setExpired(Long expired) { this.expired = expired; }
    public Long getDisabled() { return disabled; }
    public void setDisabled(Long disabled) { this.disabled = disabled; }
}

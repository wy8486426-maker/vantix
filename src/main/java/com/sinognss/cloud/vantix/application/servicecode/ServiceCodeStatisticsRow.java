package com.sinognss.cloud.vantix.application.servicecode;

/** Conditional-aggregation result for service-code statistics. */
public class ServiceCodeStatisticsRow {
    private Long total;
    private Long waiting;
    private Long expiring;
    private Long expired;
    private Long processing;
    private Long consumed;

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Long getWaiting() { return waiting; }
    public void setWaiting(Long waiting) { this.waiting = waiting; }
    public Long getExpiring() { return expiring; }
    public void setExpiring(Long expiring) { this.expiring = expiring; }
    public Long getExpired() { return expired; }
    public void setExpired(Long expired) { this.expired = expired; }
    public Long getProcessing() { return processing; }
    public void setProcessing(Long processing) { this.processing = processing; }
    public Long getConsumed() { return consumed; }
    public void setConsumed(Long consumed) { this.consumed = consumed; }
}

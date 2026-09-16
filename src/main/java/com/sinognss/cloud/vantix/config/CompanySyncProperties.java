package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vantix.company-sync")
public class CompanySyncProperties {
    private boolean enabled = true;
    private String cron = "0 0 */6 * * ?";
    private String zone = "Asia/Shanghai";
    private int pageSize = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("company-sync cron must not be blank");
        }
        this.cron = cron.trim();
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("company-sync zone must not be blank");
        }
        this.zone = zone.trim();
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("company-sync pageSize must be between 1 and 1000");
        }
        this.pageSize = pageSize;
    }
}

package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vantix.cors.account-status-sync")
public class CorsAccountStatusSyncProperties {
    private boolean enabled;
    private Duration fixedDelay = Duration.ofMinutes(1);
    private Duration staleAfter = Duration.ofMinutes(10);
    private int batchSize = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = requirePositive(fixedDelay, "fixedDelay");
    }

    public Duration getStaleAfter() { return staleAfter; }
    public void setStaleAfter(Duration staleAfter) {
        this.staleAfter = requirePositive(staleAfter, "staleAfter");
    }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("batchSize must be between 1 and 500");
        }
        this.batchSize = batchSize;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

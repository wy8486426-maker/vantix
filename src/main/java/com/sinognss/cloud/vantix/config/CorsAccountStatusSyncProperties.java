package com.sinognss.cloud.vantix.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "vantix.cors.account-status-sync")
public class CorsAccountStatusSyncProperties {
    private boolean enabled;
    private Duration fixedDelay = Duration.ofMinutes(1);
    private Duration staleAfter = Duration.ofMinutes(10);
    private int batchSize = 100;
    private Duration retryBaseDelay = Duration.ofMinutes(1);
    private Duration retryMaxDelay = Duration.ofMinutes(30);

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

    public Duration getRetryBaseDelay() { return retryBaseDelay; }
    public void setRetryBaseDelay(Duration retryBaseDelay) {
        this.retryBaseDelay = requirePositive(retryBaseDelay, "retryBaseDelay");
    }

    public Duration getRetryMaxDelay() { return retryMaxDelay; }
    public void setRetryMaxDelay(Duration retryMaxDelay) {
        this.retryMaxDelay = requirePositive(retryMaxDelay, "retryMaxDelay");
    }

    @AssertTrue(message = "retryMaxDelay must be greater than or equal to retryBaseDelay")
    public boolean isRetryDelayRangeValid() {
        return retryBaseDelay != null && retryMaxDelay != null
                && retryMaxDelay.compareTo(retryBaseDelay) >= 0;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

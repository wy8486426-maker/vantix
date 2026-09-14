package com.sinognss.cloud.vantix.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "vantix.cors.force-activation")
public class CorsForceActivationProperties {
    private boolean enabled;
    private Duration scanInterval = Duration.ofMinutes(1);
    private int candidateBatchSize = 100;
    private Duration pollInterval = Duration.ofSeconds(5);
    private int workerBatchSize = 20;
    private int maxRetries = 10;
    private Duration retryBaseDelay = Duration.ofSeconds(30);
    private Duration retryMaxDelay = Duration.ofHours(1);
    private Duration claimTimeout = Duration.ofMinutes(2);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getScanInterval() { return scanInterval; }
    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = requirePositive(scanInterval, "scanInterval");
    }

    public int getCandidateBatchSize() { return candidateBatchSize; }
    public void setCandidateBatchSize(int candidateBatchSize) {
        this.candidateBatchSize = requireBatchSize(candidateBatchSize, "candidateBatchSize");
    }

    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = requirePositive(pollInterval, "pollInterval");
    }

    public int getWorkerBatchSize() { return workerBatchSize; }
    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = requireBatchSize(workerBatchSize, "workerBatchSize");
    }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        this.maxRetries = maxRetries;
    }

    public Duration getRetryBaseDelay() { return retryBaseDelay; }
    public void setRetryBaseDelay(Duration retryBaseDelay) {
        this.retryBaseDelay = requirePositive(retryBaseDelay, "retryBaseDelay");
    }

    public Duration getRetryMaxDelay() { return retryMaxDelay; }
    public void setRetryMaxDelay(Duration retryMaxDelay) {
        this.retryMaxDelay = requirePositive(retryMaxDelay, "retryMaxDelay");
    }

    public Duration getClaimTimeout() { return claimTimeout; }
    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = requirePositive(claimTimeout, "claimTimeout");
    }

    @AssertTrue(message = "retryMaxDelay must be greater than or equal to retryBaseDelay")
    public boolean isRetryDelayRangeValid() {
        return retryBaseDelay != null && retryMaxDelay != null
                && retryMaxDelay.compareTo(retryBaseDelay) >= 0;
    }

    private static int requireBatchSize(int value, String name) {
        if (value < 1 || value > 500) {
            throw new IllegalArgumentException(name + " must be between 1 and 500");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

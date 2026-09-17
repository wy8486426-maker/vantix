package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vantix.cors-operation")
public class CorsOperationProperties {
    private int maxRetries = 10;
    private Duration retryBaseDelay = Duration.ofSeconds(30);
    private Duration claimTimeout = Duration.ofMinutes(2);
    private Duration resultWindow = Duration.ofMinutes(2);
    private int workerBatchSize = 20;

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must not be negative");
        this.maxRetries = maxRetries;
    }

    public Duration getRetryBaseDelay() { return retryBaseDelay; }
    public void setRetryBaseDelay(Duration retryBaseDelay) {
        this.retryBaseDelay = requirePositive(retryBaseDelay, "retryBaseDelay");
    }

    public Duration getClaimTimeout() { return claimTimeout; }
    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = requirePositive(claimTimeout, "claimTimeout");
    }

    public Duration getResultWindow() { return resultWindow; }
    public void setResultWindow(Duration resultWindow) {
        this.resultWindow = requirePositive(resultWindow, "resultWindow");
    }

    public int getWorkerBatchSize() { return workerBatchSize; }
    public void setWorkerBatchSize(int workerBatchSize) {
        if (workerBatchSize < 1) throw new IllegalArgumentException("workerBatchSize must be positive");
        this.workerBatchSize = workerBatchSize;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

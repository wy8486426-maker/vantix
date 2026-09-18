package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vantix.service-code.transfer")
public class ServiceCodeTransferProperties {
    private int maxBatchSize = 500;

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        if (maxBatchSize < 1 || maxBatchSize > 500) {
            throw new IllegalArgumentException("maxBatchSize must be between 1 and 500");
        }
        this.maxBatchSize = maxBatchSize;
    }
}

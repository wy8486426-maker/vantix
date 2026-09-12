package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vantix.service-code-generation")
public class GenerationProperties {
    private int maxQuantityPerRequest = 5000;
    private int maxItemsPerOrder = 50;
    private int maxTotalQuantityPerOrder = 5000;

    public int getMaxQuantityPerRequest() { return maxQuantityPerRequest; }
    public void setMaxQuantityPerRequest(int value) {
        if (value < 1) throw new IllegalArgumentException("maxQuantityPerRequest must be positive");
        maxQuantityPerRequest = value;
    }
    public int getMaxItemsPerOrder() { return maxItemsPerOrder; }
    public void setMaxItemsPerOrder(int value) {
        if (value < 1) throw new IllegalArgumentException("maxItemsPerOrder must be positive");
        maxItemsPerOrder = value;
    }
    public int getMaxTotalQuantityPerOrder() { return maxTotalQuantityPerOrder; }
    public void setMaxTotalQuantityPerOrder(int value) {
        if (value < 1) throw new IllegalArgumentException("maxTotalQuantityPerOrder must be positive");
        maxTotalQuantityPerOrder = value;
    }
}
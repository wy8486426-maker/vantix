package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vantix.service-code-generation")
public class GenerationProperties {
    private int maxQuantityPerRequest = 5000;

    public int getMaxQuantityPerRequest() { return maxQuantityPerRequest; }
    public void setMaxQuantityPerRequest(int value) {
        if (value < 1) throw new IllegalArgumentException("maxQuantityPerRequest must be positive");
        maxQuantityPerRequest = value;
    }
}
package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vantix.offline-import")
public class OfflineImportProperties {
    private long maxFileSizeBytes = 10L * 1024 * 1024;
    private int maxRows = 500;
    private int maxTotalCodes = 5000;
    private int maxErrors = 100;

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long value) {
        if (value < 1) throw new IllegalArgumentException("maxFileSizeBytes must be positive");
        maxFileSizeBytes = value;
    }
    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int value) {
        if (value < 1) throw new IllegalArgumentException("maxRows must be positive");
        maxRows = value;
    }
    public int getMaxTotalCodes() { return maxTotalCodes; }
    public void setMaxTotalCodes(int value) {
        if (value < 1) throw new IllegalArgumentException("maxTotalCodes must be positive");
        maxTotalCodes = value;
    }
    public int getMaxErrors() { return maxErrors; }
    public void setMaxErrors(int value) {
        if (value < 1) throw new IllegalArgumentException("maxErrors must be positive");
        maxErrors = value;
    }
}
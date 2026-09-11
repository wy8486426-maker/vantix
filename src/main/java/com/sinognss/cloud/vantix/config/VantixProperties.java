package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vantix.service-code")
public class VantixProperties {
    private int upcomingDays = 30;

    public int getUpcomingDays() {
        return upcomingDays;
    }

    public void setUpcomingDays(int upcomingDays) {
        if (upcomingDays < 0) {
            throw new IllegalArgumentException("upcomingDays must not be negative");
        }
        this.upcomingDays = upcomingDays;
    }
}

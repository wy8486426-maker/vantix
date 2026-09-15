package com.sinognss.cloud.vantix.application.config;

public record ServiceDurationConfigCommand(String displayName, String serviceType,
                                           Integer durationDays, Integer codeSilenceDays,
                                           Integer accountSilenceDays, Boolean enabled,
                                           String remark) {
}

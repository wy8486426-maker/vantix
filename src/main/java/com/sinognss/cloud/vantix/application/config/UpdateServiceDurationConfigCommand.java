package com.sinognss.cloud.vantix.application.config;

public record UpdateServiceDurationConfigCommand(String displayName, Integer codeSilenceDays,
                                                  Integer accountSilenceDays, Boolean enabled,
                                                  String remark) {
}

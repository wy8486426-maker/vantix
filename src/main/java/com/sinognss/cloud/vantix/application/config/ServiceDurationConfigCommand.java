package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;

public record ServiceDurationConfigCommand(String serviceType, Integer durationValue,
                                           DurationUnit durationUnit, Integer codeSilenceMonths,
                                           Boolean enabled, String remark) {
}

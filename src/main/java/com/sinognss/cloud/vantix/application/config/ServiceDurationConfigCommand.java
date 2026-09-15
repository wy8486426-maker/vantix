package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.common.DurationDisplayFormatter;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;

public record ServiceDurationConfigCommand(String displayName, String serviceType,
                                           Integer durationDays, Integer codeSilenceDays,
                                           Integer accountSilenceDays, Boolean enabled,
                                           String remark) {
    /** Compatibility constructor for callers still submitting the retired contract. */
    @Deprecated
    public ServiceDurationConfigCommand(String serviceType, Integer durationValue,
                                        DurationUnit durationUnit, Integer codeSilenceMonths,
                                        Boolean enabled, String remark) {
        this(DurationDisplayFormatter.format(durationValue, durationUnit), serviceType,
                LegacyDurationCompatibility.toDays(durationValue, durationUnit),
                LegacyDurationCompatibility.monthsToDays(codeSilenceMonths), 0, enabled, remark);
    }
}

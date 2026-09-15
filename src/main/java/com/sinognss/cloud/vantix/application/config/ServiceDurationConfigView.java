package com.sinognss.cloud.vantix.application.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sinognss.cloud.vantix.common.DurationDisplayFormatter;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;

import java.time.LocalDateTime;

public record ServiceDurationConfigView(Long id, String specCode, String displayName, String serviceType,
                                        Integer durationDays, Integer codeSilenceDays,
                                        Integer accountSilenceDays, Boolean enabled, String remark,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ServiceDurationConfigView from(ServiceDurationConfig config) {
        Integer days = config.getDurationDays() != null
                ? config.getDurationDays()
                : LegacyDurationCompatibility.toDays(config.getDurationValue(), config.getDurationUnit());
        String name = config.getDisplayName();
        if (name == null && config.getDurationValue() != null && config.getDurationUnit() != null) {
            name = DurationDisplayFormatter.format(config.getDurationValue(), config.getDurationUnit());
        }
        Integer silenceDays = config.getCodeSilenceDays() != null
                ? config.getCodeSilenceDays()
                : LegacyDurationCompatibility.monthsToDays(config.getCodeSilenceMonths());
        return new ServiceDurationConfigView(config.getId(), config.getSpecCode(), name,
                config.getServiceType(), days, silenceDays, config.getAccountSilenceDays(),
                config.getEnabled(), config.getRemark(), config.getCreatedAt(), config.getUpdatedAt());
    }

    /** Legacy read aliases; excluded from the active JSON contract. */
    @Deprecated
    @JsonIgnore
    public Integer durationValue() { return durationDays; }

    @Deprecated
    @JsonIgnore
    public com.sinognss.cloud.vantix.domain.config.DurationUnit durationUnit() {
        return durationDays == null ? null : com.sinognss.cloud.vantix.domain.config.DurationUnit.DAY;
    }

    @Deprecated
    @JsonIgnore
    public Integer codeSilenceMonths() { return codeSilenceDays == null ? null : codeSilenceDays / 30; }
}

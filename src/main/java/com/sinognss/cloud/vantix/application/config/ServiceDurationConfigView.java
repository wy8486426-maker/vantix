package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;

import java.time.LocalDateTime;

public record ServiceDurationConfigView(Long id, String serviceType, Integer durationValue,
                                        DurationUnit durationUnit, Integer codeSilenceMonths,
                                        Boolean enabled, String remark, LocalDateTime createdAt,
                                        LocalDateTime updatedAt) {
    public static ServiceDurationConfigView from(ServiceDurationConfig config) {
        return new ServiceDurationConfigView(config.getId(), config.getServiceType(), config.getDurationValue(),
                config.getDurationUnit(), config.getCodeSilenceMonths(), config.getEnabled(), config.getRemark(),
                config.getCreatedAt(), config.getUpdatedAt());
    }
}

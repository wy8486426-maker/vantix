package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;

import java.time.LocalDateTime;

public record ServiceDurationConfigView(Long id, String specCode, String displayName, String serviceType,
                                        Integer durationDays, Integer codeSilenceDays,
                                        Integer accountSilenceDays, Boolean enabled, String remark,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ServiceDurationConfigView from(ServiceDurationConfig config) {
        return new ServiceDurationConfigView(config.getId(), config.getSpecCode(), config.getDisplayName(),
                config.getServiceType(), config.getDurationDays(), config.getCodeSilenceDays(),
                config.getAccountSilenceDays(),
                config.getEnabled(), config.getRemark(), config.getCreatedAt(), config.getUpdatedAt());
    }
}

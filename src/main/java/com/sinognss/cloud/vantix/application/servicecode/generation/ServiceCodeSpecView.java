package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;

public record ServiceCodeSpecView(String specCode, String displayName, Integer durationDays) {
    public static ServiceCodeSpecView from(ServiceDurationConfig config) {
        return new ServiceCodeSpecView(config.getSpecCode(), config.getDisplayName(), config.getDurationDays());
    }
}

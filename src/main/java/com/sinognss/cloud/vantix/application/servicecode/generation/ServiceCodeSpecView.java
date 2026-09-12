package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.DurationDisplayFormatter;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;

public record ServiceCodeSpecView(String specCode, String displayName, Integer durationValue,
                                  String durationUnit) {
    public static ServiceCodeSpecView from(ServiceDurationConfig config) {
        return new ServiceCodeSpecView(config.getSpecCode(),
                DurationDisplayFormatter.format(config.getDurationValue(), config.getDurationUnit()),
                config.getDurationValue(), config.getDurationUnit().name());
    }
}
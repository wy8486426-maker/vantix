package com.sinognss.cloud.vantix.integration.cors;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;

public record CorsBatchCreateRequest(String requestId, int durationValue, DurationUnit durationUnit,
                                     int quantity, String accountPrefix) {
}

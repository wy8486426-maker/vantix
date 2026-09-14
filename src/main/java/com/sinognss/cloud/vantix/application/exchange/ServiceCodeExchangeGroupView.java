package com.sinognss.cloud.vantix.application.exchange;

import java.time.LocalDateTime;

public record ServiceCodeExchangeGroupView(String specCode, String displayName, String serviceType,
                                           String generationSource, Long availableCount,
                                           LocalDateTime earliestExpireAt) {
}

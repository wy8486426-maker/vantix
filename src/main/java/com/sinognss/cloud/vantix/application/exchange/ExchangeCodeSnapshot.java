package com.sinognss.cloud.vantix.application.exchange;

import java.time.LocalDateTime;

public record ExchangeCodeSnapshot(Long serviceCodeId, String serviceCode,
                                   Long ownerCompanyId, Long assignedUserId,
                                   String serviceType, Integer durationValue,
                                   String durationUnit, Integer codeSilenceMonths,
                                   LocalDateTime expireAt) {
}

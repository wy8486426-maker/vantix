package com.sinognss.cloud.vantix.domain.renewal;

import java.time.LocalDateTime;

public record AccountRenewalCodeSnapshot(
        Long serviceCodeId,
        String code,
        Long ownerCompanyId,
        String serviceType,
        Integer durationValue,
        String durationUnit,
        Integer codeSilenceMonths,
        LocalDateTime expireAt) {
}

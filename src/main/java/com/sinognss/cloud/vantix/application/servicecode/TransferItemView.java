package com.sinognss.cloud.vantix.application.servicecode;

import java.time.LocalDateTime;

public record TransferItemView(Long serviceCodeId, String serviceCode, String specCode,
                               String displayName, String serviceType, Integer durationDays,
                               LocalDateTime expireAt) {
    public static TransferItemView from(TransferItemQueryRow row) {
        return new TransferItemView(row.getServiceCodeId(), row.getServiceCode(), row.getSpecCode(),
                row.getDisplayName(), row.getServiceType(), row.getDurationDays(), row.getExpireAt());
    }
}

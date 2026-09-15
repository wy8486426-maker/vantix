package com.sinognss.cloud.vantix.application.renewal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public record AccountRenewalView(
        Long renewalId,
        String requestId,
        Long serviceAccountId,
        Long serviceCodeId,
        String specCode,
        String serviceType,
        Integer durationDays,
        Integer codeSilenceDays,
        String status,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime accountExpireAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {
    @Deprecated
    @JsonIgnore
    public Integer durationValue() { return durationDays; }

    @Deprecated
    @JsonIgnore
    public String durationUnit() { return durationDays == null ? null : "DAY"; }
}

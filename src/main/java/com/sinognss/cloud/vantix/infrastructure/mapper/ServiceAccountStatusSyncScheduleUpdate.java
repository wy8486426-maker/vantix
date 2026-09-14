package com.sinognss.cloud.vantix.infrastructure.mapper;

import java.time.LocalDateTime;

public record ServiceAccountStatusSyncScheduleUpdate(
        Long id,
        Long expectedVersion,
        LocalDateTime lastSyncAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime nextAt,
        int failureCount,
        LocalDateTime updatedAt) {
}

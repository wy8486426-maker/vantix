package com.sinognss.cloud.vantix.infrastructure.mapper;

import java.time.LocalDateTime;

public record ServiceAccountCorsSnapshotUpdate(
        Long id,
        Long expectedVersion,
        String corsStatus,
        String corsActivationStatus,
        LocalDateTime activatedAt,
        LocalDateTime expireAt,
        LocalDateTime corsCreatedAt,
        LocalDateTime corsUpdatedAt,
        LocalDateTime lastSyncAt,
        LocalDateTime updatedAt) {
}

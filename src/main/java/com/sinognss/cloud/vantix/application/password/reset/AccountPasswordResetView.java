package com.sinognss.cloud.vantix.application.password.reset;

import java.time.LocalDateTime;

public record AccountPasswordResetView(
        String requestId,
        Long serviceAccountId,
        String account,
        String status,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {
}

package com.sinognss.cloud.vantix.application.cors.account;

import java.time.LocalDateTime;

public record AccountStatusSyncSuccessSchedule(LocalDateTime syncedAt, LocalDateTime nextAt) {
}

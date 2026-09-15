package com.sinognss.cloud.vantix.integration.cors.account;

import java.time.LocalDateTime;

public record CorsUserInfoStatusRow(
        Long id,
        String name,
        Integer activeStatus,
        Integer accountStatus,
        LocalDateTime activeTime,
        LocalDateTime expireDate,
        LocalDateTime lastUpdateTime) {
}

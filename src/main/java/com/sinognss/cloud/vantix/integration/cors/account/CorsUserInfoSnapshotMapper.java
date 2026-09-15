package com.sinognss.cloud.vantix.integration.cors.account;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class CorsUserInfoSnapshotMapper {
    private static final ZoneId CORS_ZONE = ZoneId.of("Asia/Shanghai");

    public CorsAccountSnapshot map(CorsUserInfoStatusRow row) {
        if (row == null || row.id() == null || row.id() <= 0 || row.name() == null || row.name().isBlank()
                || row.lastUpdateTime() == null) {
            throw new IllegalArgumentException("CORS userinfo status row is incomplete");
        }
        return new CorsAccountSnapshot(String.valueOf(row.id()), row.name(),
                mapAccountStatus(row.accountStatus()), mapActivationStatus(row.activeStatus()),
                toOffset(row.activeTime()), toOffset(row.expireDate()), null, toOffset(row.lastUpdateTime()));
    }

    private static String mapActivationStatus(Integer value) {
        return switch (value == null ? -1 : value) {
            case 0 -> "ACTIVE";
            case 1 -> "WAITING_ACTIVATION";
            case 2 -> "EXPIRED";
            default -> throw new IllegalArgumentException("Invalid CORS active_status");
        };
    }

    private static String mapAccountStatus(Integer value) {
        return switch (value == null ? -1 : value) {
            case 0 -> "ENABLED";
            case 1 -> "DISABLED";
            default -> throw new IllegalArgumentException("Invalid CORS account_status");
        };
    }

    private static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(CORS_ZONE).toOffsetDateTime();
    }
}

package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoSnapshotMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoStatusRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsUserInfoSnapshotMapperTest {
    private final CorsUserInfoSnapshotMapper mapper = new CorsUserInfoSnapshotMapper();

    @Test
    void mapsConfirmedStatusValuesAndShanghaiTimes() {
        CorsAccountSnapshot snapshot = mapper.map(new CorsUserInfoStatusRow(7L, "cors-user",
                0, 0, LocalDateTime.of(2026, 9, 15, 8, 0),
                LocalDateTime.of(2026, 10, 15, 8, 0), LocalDateTime.of(2026, 9, 15, 9, 0)));

        assertEquals("7", snapshot.accountId());
        assertEquals("cors-user", snapshot.account());
        assertEquals("ACTIVE", snapshot.activationStatus());
        assertEquals("ENABLED", snapshot.accountStatus());
        assertEquals("+08:00", snapshot.updatedAt().getOffset().toString());
        assertEquals(LocalDateTime.of(2026, 9, 15, 9, 0), snapshot.updatedAt().toLocalDateTime());
        assertEquals(null, snapshot.createdAt());
    }

    @Test
    void mapsExpiredAndRejectsUnknownOrMissingStatuses() {
        CorsUserInfoStatusRow expired = new CorsUserInfoStatusRow(7L, "user", 2, 1,
                null, null, LocalDateTime.now());
        assertEquals("EXPIRED", mapper.map(expired).activationStatus());
        assertEquals("DISABLED", mapper.map(expired).accountStatus());
        assertThrows(IllegalArgumentException.class, () -> mapper.map(
                new CorsUserInfoStatusRow(7L, "user", 3, 0, null, null, LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(
                new CorsUserInfoStatusRow(7L, "user", 0, null, null, null, LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(
                new CorsUserInfoStatusRow(7L, "user", 0, 0, null, null, null)));
    }

    @Test
    void keepsTheRepositoryProjectionMinimal() {
        String sql = com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoRepository.FIND_BY_IDS_SQL;
        assertEquals(false, sql.toUpperCase().contains("SELECT *"));
        assertEquals(false, sql.toLowerCase().matches(".*\\b(password|secret|phone|email|currentip|contact|address)\\b.*"));
        for (String column : new String[]{"ID", "name", "active_status", "account_status",
                "active_time", "expiredate", "lastupdatetime"}) {
            assertEquals(true, sql.contains(column));
        }
    }
}

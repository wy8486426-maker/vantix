package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlV7AccountRenewalMigrationIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v7_account_renewal");

    @Test
    void appliesV1ThroughV7AndEnforcesAccountRenewalUniquenessRules() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("7").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class));
        assertColumnsExist(jdbc, List.of(
                "owner_company_id", "assigned_user_id", "service_type", "duration_value", "duration_unit",
                "service_code_snapshot", "operator_user_id", "operator_user_name", "last_error_code",
                "last_error_message", "completed_at", "updated_at", "version", "active_service_code_id",
                "active_service_account_id"));
        assertEquals("utf8mb4_bin", jdbc.queryForObject(
                "SELECT collation_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_renewal' "
                        + "AND column_name = 'request_id'", String.class));
        assertGeneratedStored(jdbc, "active_service_code_id");
        assertGeneratedStored(jdbc, "active_service_account_id");

        assertIndex(jdbc, "uk_account_renewal_request", 0);
        assertIndexColumns(jdbc, "uk_account_renewal_request", List.of("request_id"));
        assertIndexAbsent(jdbc, "uk_account_renewal_code");
        assertIndex(jdbc, "idx_account_renewal_code", 1);
        assertIndexColumns(jdbc, "idx_account_renewal_code", List.of("service_code_id"));
        assertIndex(jdbc, "uk_account_renewal_active_code", 0);
        assertIndexColumns(jdbc, "uk_account_renewal_active_code", List.of("active_service_code_id"));
        assertIndex(jdbc, "uk_account_renewal_active_account", 0);
        assertIndexColumns(jdbc, "uk_account_renewal_active_account", List.of("active_service_account_id"));
        assertIndex(jdbc, "idx_account_renewal_account_created", 1);
        assertIndexColumns(jdbc, "idx_account_renewal_account_created",
                List.of("service_account_id", "created_at", "id"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_renewal' "
                        + "AND referenced_table_name IS NOT NULL", Integer.class));

        assertCaseSensitiveRequestIds(jdbc);
        assertFailedRenewalAllowsServiceCodeReuse(jdbc);
        assertCompletedRenewalRetainsServiceCode(jdbc);
        assertManualReviewRetainsServiceCode(jdbc);
        assertOnlyOneActiveRenewalPerAccount(jdbc);
        assertCompletedAccountCanBeRenewedAgain(jdbc);
    }

    private void assertCaseSensitiveRequestIds(JdbcTemplate jdbc) {
        insertRenewal(jdbc, 10_001L, 20_001L, "Renewal-Case", "FAILED");
        insertRenewal(jdbc, 10_002L, 20_002L, "renewal-case", "FAILED");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_renewal WHERE request_id IN ('Renewal-Case', 'renewal-case')",
                Integer.class));
        assertThrows(DuplicateKeyException.class, () ->
                insertRenewal(jdbc, 10_003L, 20_003L, "Renewal-Case", "FAILED"));
    }

    private void assertFailedRenewalAllowsServiceCodeReuse(JdbcTemplate jdbc) {
        insertRenewal(jdbc, 11_001L, 21_001L, "FAILED-CODE-HISTORY", "FAILED");
        assertNull(activeCode(jdbc, "FAILED-CODE-HISTORY"));

        insertRenewal(jdbc, 11_002L, 21_001L, "FAILED-CODE-REUSE", "PROCESSING");
        assertEquals(21_001L, activeCode(jdbc, "FAILED-CODE-REUSE"));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_renewal WHERE service_code_id = 21001", Integer.class));
    }

    private void assertCompletedRenewalRetainsServiceCode(JdbcTemplate jdbc) {
        insertRenewal(jdbc, 12_001L, 22_001L, "COMPLETED-CODE-HISTORY", "COMPLETED");
        assertThrows(DuplicateKeyException.class, () ->
                insertRenewal(jdbc, 12_002L, 22_001L, "COMPLETED-CODE-REUSE", "PROCESSING"));
    }

    private void assertManualReviewRetainsServiceCode(JdbcTemplate jdbc) {
        insertRenewal(jdbc, 13_001L, 23_001L, "MANUAL-REVIEW-CODE-HISTORY", "MANUAL_REVIEW");
        assertEquals(23_001L, activeCode(jdbc, "MANUAL-REVIEW-CODE-HISTORY"));
        assertThrows(DuplicateKeyException.class, () ->
                insertRenewal(jdbc, 13_002L, 23_001L, "MANUAL-REVIEW-CODE-REUSE", "PROCESSING"));
    }

    private void assertOnlyOneActiveRenewalPerAccount(JdbcTemplate jdbc) {
        insertRenewal(jdbc, 14_001L, 24_001L, "ACTIVE-ACCOUNT-FIRST", "PROCESSING");
        assertEquals(14_001L, activeAccount(jdbc, "ACTIVE-ACCOUNT-FIRST"));
        assertThrows(DuplicateKeyException.class, () ->
                insertRenewal(jdbc, 14_001L, 24_002L, "ACTIVE-ACCOUNT-SECOND", "PROCESSING"));
    }

    private void assertCompletedAccountCanBeRenewedAgain(JdbcTemplate jdbc) {
        insertRenewal(jdbc, 15_001L, 25_001L, "COMPLETED-ACCOUNT-HISTORY", "COMPLETED");
        assertNull(activeAccount(jdbc, "COMPLETED-ACCOUNT-HISTORY"));

        insertRenewal(jdbc, 15_001L, 25_002L, "COMPLETED-ACCOUNT-RENEWAL", "PROCESSING");
        assertEquals(15_001L, activeAccount(jdbc, "COMPLETED-ACCOUNT-RENEWAL"));
    }

    private void insertRenewal(JdbcTemplate jdbc, long serviceAccountId, long serviceCodeId,
                               String requestId, String status) {
        jdbc.update("INSERT INTO account_renewal (service_account_id, service_code_id, owner_company_id, "
                        + "service_type, duration_value, duration_unit, service_code_snapshot, request_id, status) "
                        + "VALUES (?, ?, 100, 'CORS', 1, 'MONTH', CAST('{}' AS JSON), ?, ?)",
                serviceAccountId, serviceCodeId, requestId, status);
    }

    private Long activeCode(JdbcTemplate jdbc, String requestId) {
        return jdbc.queryForObject("SELECT active_service_code_id FROM account_renewal WHERE request_id = ?",
                Long.class, requestId);
    }

    private Long activeAccount(JdbcTemplate jdbc, String requestId) {
        return jdbc.queryForObject("SELECT active_service_account_id FROM account_renewal WHERE request_id = ?",
                Long.class, requestId);
    }

    private void assertColumnsExist(JdbcTemplate jdbc, List<String> columns) {
        for (String column : columns) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'account_renewal' "
                            + "AND column_name = ?", Integer.class, column), column);
        }
    }

    private void assertGeneratedStored(JdbcTemplate jdbc, String column) {
        assertTrue(jdbc.queryForObject(
                "SELECT extra FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_renewal' "
                        + "AND column_name = ?", String.class, column)
                .toUpperCase().contains("STORED GENERATED"), column);
    }

    private void assertIndex(JdbcTemplate jdbc, String indexName, int nonUnique) {
        assertEquals(nonUnique, jdbc.queryForObject(
                "SELECT non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_renewal' "
                        + "AND index_name = ? LIMIT 1", Integer.class, indexName), indexName);
    }

    private void assertIndexColumns(JdbcTemplate jdbc, String indexName, List<String> expectedColumns) {
        assertEquals(expectedColumns, jdbc.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_renewal' "
                        + "AND index_name = ? ORDER BY seq_in_index", String.class, indexName), indexName);
    }

    private void assertIndexAbsent(JdbcTemplate jdbc, String indexName) {
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_renewal' "
                        + "AND index_name = ?", Integer.class, indexName), indexName);
    }
}

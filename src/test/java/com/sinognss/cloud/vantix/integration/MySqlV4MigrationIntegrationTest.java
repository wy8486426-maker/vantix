package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlV4MigrationIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v4_migration");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Test
    void appliesV4ExchangeSchemaAndIndexesWithoutForeignKeys() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("4")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
        assertColumnExists(jdbc, "exchange_batch", "generation_source");
        assertColumnExists(jdbc, "exchange_batch", "payload_hash");
        assertColumnExists(jdbc, "exchange_batch", "account_silence_months");
        assertColumnExists(jdbc, "exchange_batch", "assigned_user_id");
        assertColumnExists(jdbc, "exchange_batch", "last_error_message");
        assertColumnExists(jdbc, "exchange_batch", "completed_at");
        assertColumnExists(jdbc, "exchange_detail", "detail_index");
        assertColumnExists(jdbc, "exchange_detail", "service_code_snapshot");
        assertColumnExists(jdbc, "exchange_detail", "active_service_code_id");
        assertColumnExists(jdbc, "exchange_detail", "cors_account_id");
        assertColumnExists(jdbc, "exchange_detail", "last_error_message");
        assertColumnExists(jdbc, "exchange_detail", "updated_at");
        assertTrue(jdbc.queryForObject(
                "SELECT extra FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'exchange_detail' "
                        + "AND column_name = 'active_service_code_id'", String.class)
                .toUpperCase().contains("STORED GENERATED"));
        assertColumnExists(jdbc, "service_account", "exchange_detail_id");
        assertColumnExists(jdbc, "service_account", "cors_activation_status");
        assertColumnExists(jdbc, "service_account", "cors_created_at");

        assertIndex(jdbc, "service_code", "idx_service_code_processing_request", 1);
        assertIndexAbsent(jdbc, "service_code", "uk_service_code_processing_request");
        assertIndex(jdbc, "exchange_batch", "uk_exchange_batch_request", 0);
        assertEquals("utf8mb4_bin", jdbc.queryForObject(
                "SELECT collation_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'exchange_batch' "
                        + "AND column_name = 'request_id'", String.class));
        assertEquals("utf8mb4_bin", jdbc.queryForObject(
                "SELECT collation_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'exchange_detail' "
                        + "AND column_name = 'request_id'", String.class));
        assertEquals("utf8mb4_bin", jdbc.queryForObject(
                "SELECT collation_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'cors_operation' "
                        + "AND column_name = 'request_id'", String.class));
        assertIndexAbsent(jdbc, "exchange_detail", "uk_exchange_detail_code");
        assertIndex(jdbc, "exchange_detail", "idx_exchange_detail_code", 1);
        assertIndexColumns(jdbc, "exchange_detail", "idx_exchange_detail_code", List.of("service_code_id"));
        assertIndex(jdbc, "exchange_detail", "uk_exchange_detail_active_code", 0);
        assertIndexColumns(jdbc, "exchange_detail", "uk_exchange_detail_active_code",
                List.of("active_service_code_id"));
        assertIndex(jdbc, "exchange_detail", "uk_exchange_detail_batch_index", 0);
        assertIndexAbsent(jdbc, "exchange_detail", "uk_exchange_detail_request");
        assertIndex(jdbc, "service_account", "uk_service_account_exchange_detail", 0);

        assertIndex(jdbc, "cors_operation", "idx_cors_operation_due", 1);
        assertIndexColumns(jdbc, "cors_operation", "idx_cors_operation_due",
                List.of("operation_type", "biz_type", "status", "next_retry_at", "created_at", "id"));
        assertIndex(jdbc, "cors_operation", "idx_cors_operation_claimed", 1);
        assertIndexColumns(jdbc, "cors_operation", "idx_cors_operation_claimed",
                List.of("operation_type", "biz_type", "status", "claimed_at", "id"));
        assertIndex(jdbc, "cors_operation", "uk_cors_operation_biz", 0);
        assertIndexColumns(jdbc, "cors_operation", "uk_cors_operation_biz", List.of("biz_type", "biz_id"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('service_code', 'exchange_batch', 'exchange_detail', "
                        + "'service_account', 'cors_operation') "
                        + "AND referenced_table_name IS NOT NULL", Integer.class));

        assertFailedDetailDoesNotBlockASecondActiveDetail(jdbc);
        assertCompletedDetailBlocksASecondActiveDetail(jdbc);
        assertCorsBusinessKeyIsUnique(jdbc);
    }

    private void assertFailedDetailDoesNotBlockASecondActiveDetail(JdbcTemplate jdbc) {
        long failedBatchId = insertExchangeBatch(jdbc, "FAILED-HISTORY-A");
        insertExchangeDetail(jdbc, failedBatchId, 1, 900001L, "FAILED-HISTORY-A", "FAILED");
        assertNull(jdbc.queryForObject(
                "SELECT active_service_code_id FROM exchange_detail WHERE request_id = 'FAILED-HISTORY-A'",
                Long.class));

        long activeBatchId = insertExchangeBatch(jdbc, "FAILED-HISTORY-B");
        insertExchangeDetail(jdbc, activeBatchId, 1, 900001L, "FAILED-HISTORY-B", "PROCESSING");
        assertEquals(900001L, jdbc.queryForObject(
                "SELECT active_service_code_id FROM exchange_detail WHERE request_id = 'FAILED-HISTORY-B'",
                Long.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM exchange_detail WHERE service_code_id = 900001", Integer.class));
    }

    private void assertCompletedDetailBlocksASecondActiveDetail(JdbcTemplate jdbc) {
        long completedBatchId = insertExchangeBatch(jdbc, "COMPLETED-LOCK-A");
        insertExchangeDetail(jdbc, completedBatchId, 1, 900002L, "COMPLETED-LOCK-A", "COMPLETED");
        assertThrows(DuplicateKeyException.class, () -> {
            long processingBatchId = insertExchangeBatch(jdbc, "COMPLETED-LOCK-B");
            insertExchangeDetail(jdbc, processingBatchId, 1, 900002L, "COMPLETED-LOCK-B", "PROCESSING");
        });
    }

    private void assertCorsBusinessKeyIsUnique(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO cors_operation (request_id, operation_type, biz_type, biz_id, status) "
                        + "VALUES ('BIZ-UNIQUE-A', 'BATCH_CREATE_ACCOUNT', 'EXCHANGE_BATCH', 900003, 'PENDING')");
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO cors_operation (request_id, operation_type, biz_type, biz_id, status) "
                        + "VALUES ('BIZ-UNIQUE-B', 'BATCH_CREATE_ACCOUNT', 'EXCHANGE_BATCH', 900003, 'PENDING')"));
    }

    private long insertExchangeBatch(JdbcTemplate jdbc, String suffix) {
        String requestId = "V4-" + suffix;
        jdbc.update("INSERT INTO exchange_batch (exchange_batch_no, request_id, owner_company_id, "
                        + "generation_source, spec_code, service_type, duration_value, duration_unit, quantity, "
                        + "payload_hash, account_silence_months, status) "
                        + "VALUES (?, ?, 100, 'B2B', 'M1', 'CORS', 1, 'MONTH', 1, REPEAT('0', 64), 12, 'PROCESSING')",
                "V4-BATCH-" + suffix, requestId);
        return jdbc.queryForObject("SELECT id FROM exchange_batch WHERE request_id = ?", Long.class, requestId);
    }

    private void insertExchangeDetail(JdbcTemplate jdbc, long batchId, int detailIndex,
                                      long serviceCodeId, String requestId, String status) {
        jdbc.update("INSERT INTO exchange_detail (exchange_batch_id, detail_index, service_code_id, "
                        + "service_code_snapshot, request_id, status) "
                        + "VALUES (?, ?, ?, CAST('{}' AS JSON), ?, ?)",
                batchId, detailIndex, serviceCodeId, requestId, status);
    }

    private void assertColumnExists(JdbcTemplate jdbc, String tableName, String columnName) {
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName), tableName + "." + columnName);
    }

    private void assertIndex(JdbcTemplate jdbc, String tableName, String indexName, int nonUnique) {
        assertEquals(nonUnique, jdbc.queryForObject(
                "SELECT non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1",
                Integer.class, tableName, indexName), tableName + "." + indexName);
    }

    private void assertIndexColumns(JdbcTemplate jdbc, String tableName, String indexName,
                                   List<String> expectedColumns) {
        assertEquals(expectedColumns, jdbc.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? "
                        + "ORDER BY seq_in_index", String.class, tableName, indexName), tableName + "." + indexName);
    }

    private void assertIndexAbsent(JdbcTemplate jdbc, String tableName, String indexName) {
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, tableName, indexName), tableName + "." + indexName);
    }
}

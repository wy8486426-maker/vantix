package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class MySqlV4MigrationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("vantix_v4_migration")
            .withUsername("root")
            .withPassword("test");

    @Test
    void appliesV4ExchangeSchemaAndIndexesWithoutForeignKeys() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
        assertColumnExists(jdbc, "exchange_batch", "generation_source");
        assertColumnExists(jdbc, "exchange_batch", "payload_hash");
        assertColumnExists(jdbc, "exchange_batch", "account_silence_months");
        assertColumnExists(jdbc, "exchange_batch", "last_error_message");
        assertColumnExists(jdbc, "exchange_batch", "completed_at");
        assertColumnExists(jdbc, "exchange_detail", "detail_index");
        assertColumnExists(jdbc, "exchange_detail", "service_code_snapshot");
        assertColumnExists(jdbc, "exchange_detail", "cors_account_id");
        assertColumnExists(jdbc, "exchange_detail", "last_error_message");
        assertColumnExists(jdbc, "exchange_detail", "updated_at");
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
        assertIndex(jdbc, "exchange_detail", "uk_exchange_detail_code", 0);
        assertIndex(jdbc, "exchange_detail", "uk_exchange_detail_batch_index", 0);
        assertIndexAbsent(jdbc, "exchange_detail", "uk_exchange_detail_request");
        assertIndex(jdbc, "service_account", "uk_service_account_exchange_detail", 0);

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('service_code', 'exchange_batch', 'exchange_detail', 'service_account') "
                        + "AND referenced_table_name IS NOT NULL", Integer.class));
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

    private void assertIndexAbsent(JdbcTemplate jdbc, String tableName, String indexName) {
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, tableName, indexName), tableName + "." + indexName);
    }
}

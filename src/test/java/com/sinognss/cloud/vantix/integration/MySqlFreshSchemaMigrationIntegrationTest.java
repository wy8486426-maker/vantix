package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlFreshSchemaMigrationIntegrationTest {
    private static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_fresh_schema");

    @AfterAll
    static void closeDatabase() {
        MYSQL.close();
    }

    @Test
    void migratesFreshMySql57WithOnlyTheCompleteV1Baseline() {
        JdbcTemplate jdbc = jdbc();

        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertEquals(List.of("1"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = DATABASE()",
                Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = 'account_config'", Integer.class));

        assertTablesExist(jdbc, List.of(
                "dealer_company", "dealer_relation_log", "system_config", "service_duration_config",
                "company_exchange_config",
                "service_code_generate_order", "service_code_generate_batch", "service_code",
                "service_code_transfer", "exchange_batch", "exchange_detail", "service_account",
                "account_renewal", "cors_operation", "cors_event_record", "account_password_action"));
        assertNoLegacyColumns(jdbc);
        assertColumns(jdbc, "service_duration_config", List.of(
                "spec_code", "display_name", "service_type", "duration_days",
                "code_silence_days", "account_silence_days"));
        assertColumns(jdbc, "service_code", List.of(
                "spec_code", "service_type", "duration_days", "code_silence_days"));
        assertColumns(jdbc, "service_account", List.of(
                "spec_code", "service_type", "duration_days", "account_silence_days",
                "cors_status", "cors_activation_status", "activated_at", "expire_at",
                "cors_created_at", "cors_updated_at", "last_sync_at", "status_sync_next_at",
                "status_sync_last_attempt_at", "status_sync_failure_count"));
        assertColumns(jdbc, "exchange_batch", List.of(
                "exchange_batch_no", "request_id", "owner_company_id", "assigned_user_id",
                "generation_source", "spec_code", "display_name", "service_type", "duration_days",
                "account_silence_days", "quantity", "account_prefix", "payload_hash", "status",
                "operator_user_id", "operator_user_name", "completed_at"));
        assertColumnNotNull(jdbc, "exchange_batch", "display_name");
        assertColumns(jdbc, "account_renewal", List.of(
                "service_account_id", "service_code_id", "owner_company_id", "assigned_user_id",
                "spec_code", "service_type", "duration_days", "code_silence_days",
                "service_code_snapshot", "request_id", "status", "version",
                "active_service_code_id", "active_service_account_id"));
        assertColumns(jdbc, "cors_operation", List.of(
                "request_id", "operation_type", "biz_type", "biz_id", "service_account_id",
                "status", "retry_count", "next_retry_at", "claimed_at", "last_error_code",
                "last_error_message", "version"));
        assertColumns(jdbc, "account_password_action", List.of(
                "request_id", "action_type", "service_account_id", "owner_company_id",
                "assigned_user_id", "cors_account_id", "account", "status", "version",
                "active_reset_account_id"));
        assertPlainNullableColumn(jdbc, "exchange_detail", "active_service_code_id");
        assertPlainNullableColumn(jdbc, "account_renewal", "active_service_code_id");
        assertPlainNullableColumn(jdbc, "account_renewal", "active_service_account_id");
        assertPlainNullableColumn(jdbc, "account_password_action", "active_reset_account_id");
        assertColumnType(jdbc, "exchange_detail", "service_code_snapshot", "json");
        assertColumnType(jdbc, "account_renewal", "service_code_snapshot", "json");
        assertPasswordSchemaDoesNotPersistCredentials(jdbc);

        assertIndexColumns(jdbc, "service_duration_config", "uk_service_duration_spec_code",
                List.of("spec_code"));
        assertIndexColumns(jdbc, "service_duration_config", "uk_service_duration_display_name",
                List.of("display_name"));
        assertIndexColumns(jdbc, "company_exchange_config", "uk_company_exchange_config_company",
                List.of("company_id"));
        assertIndexColumns(jdbc, "exchange_batch", "idx_exchange_batch_owner_created",
                List.of("owner_company_id", "created_at", "id"));
        assertIndexColumns(jdbc, "exchange_batch", "idx_exchange_batch_created",
                List.of("created_at", "id"));
        assertIndexColumns(jdbc, "account_renewal", "idx_account_renewal_owner_created",
                List.of("owner_company_id", "created_at", "id"));
        assertIndexColumns(jdbc, "account_renewal", "idx_account_renewal_owner_assigned_created",
                List.of("owner_company_id", "assigned_user_id", "created_at", "id"));
        assertIndexColumns(jdbc, "account_renewal", "idx_account_renewal_created",
                List.of("created_at", "id"));
        assertIndexColumns(jdbc, "exchange_detail", "uk_exchange_detail_active_code",
                List.of("active_service_code_id"));
        assertIndexColumns(jdbc, "account_renewal", "uk_account_renewal_active_code",
                List.of("active_service_code_id"));
        assertIndexColumns(jdbc, "account_renewal", "uk_account_renewal_active_account",
                List.of("active_service_account_id"));
        assertIndexColumns(jdbc, "account_password_action", "uk_password_action_active_reset",
                List.of("active_reset_account_id"));
        insertSpec(jdbc, "SC90A", "90天标准版", 90);
        insertSpec(jdbc, "SC90B", "90天体验版", 90);
        assertThrows(DuplicateKeyException.class,
                () -> insertSpec(jdbc, "SC90C", "90天标准版", 90));
        jdbc.update("INSERT INTO service_duration_config "
                        + "(spec_code, display_name, service_type, duration_days, code_silence_days, "
                        + "account_silence_days, enabled, remark) VALUES (?, ?, ?, ?, ?, ?, 1, ?)",
                "SC45", "45天体验版", "CORS", 45, 20, 60, "custom");
        assertEquals(45, jdbc.queryForObject(
                "SELECT duration_days FROM service_duration_config WHERE spec_code = 'SC45'", Integer.class));
        assertEquals(60, jdbc.queryForObject(
                "SELECT account_silence_days FROM service_duration_config WHERE spec_code = 'SC45'", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_duration_config WHERE duration_days = 90", Integer.class));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    private void insertSpec(JdbcTemplate jdbc, String specCode, String displayName, int durationDays) {
        jdbc.update("INSERT INTO service_duration_config "
                        + "(spec_code, display_name, service_type, duration_days, code_silence_days, "
                        + "account_silence_days, enabled) VALUES (?, ?, 'CORS', ?, 20, 60, 1)",
                specCode, displayName, durationDays);
    }

    private void assertTablesExist(JdbcTemplate jdbc, List<String> tables) {
        for (String table : tables) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                            + "AND table_name = ?", Integer.class, table), table);
        }
    }

    private void assertColumns(JdbcTemplate jdbc, String table, List<String> columns) {
        for (String column : columns) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = ? AND column_name = ?", Integer.class, table, column),
                    table + "." + column);
        }
    }

    private void assertColumnNotNull(JdbcTemplate jdbc, String table, String column) {
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND column_name = ? AND is_nullable = 'NO'",
                Integer.class, table, column), table + "." + column + " must be NOT NULL");
    }

    private void assertPlainNullableColumn(JdbcTemplate jdbc, String table, String column) {
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND column_name = ? AND is_nullable = 'YES' "
                        + "AND UPPER(extra) NOT LIKE '%GENERATED%'",
                Integer.class, table, column), table + "." + column + " must be a plain nullable column");
    }

    private void assertColumnType(JdbcTemplate jdbc, String table, String column, String dataType) {
        assertEquals(dataType, jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND column_name = ?", String.class, table, column));
    }

    private void assertNoLegacyColumns(JdbcTemplate jdbc) {
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND column_name IN ('duration_value', 'duration_unit', 'code_silence_months', "
                        + "'account_silence_months', 'force_activate_at')", Integer.class));
    }

    private void assertPasswordSchemaDoesNotPersistCredentials(JdbcTemplate jdbc) {
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'account_password_action' "
                        + "AND (LOWER(column_name) LIKE '%password%' OR LOWER(column_name) LIKE '%secret%')",
                Integer.class));
        assertEquals("utf8mb4_bin", jdbc.queryForObject(
                "SELECT collation_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'account_password_action' AND column_name = 'request_id'",
                String.class));
    }

    private void assertIndexColumns(JdbcTemplate jdbc, String table, String index, List<String> columns) {
        assertEquals(columns, jdbc.queryForList(
                "SELECT column_name FROM information_schema.statistics WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND index_name = ? ORDER BY seq_in_index", String.class,
                table, index));
    }
}

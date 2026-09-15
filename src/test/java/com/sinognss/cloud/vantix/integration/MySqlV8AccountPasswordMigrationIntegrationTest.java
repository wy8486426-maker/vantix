package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlV8AccountPasswordMigrationIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v8_account_password");

    @Test
    void appliesV1ThroughV8AndEnforcesPasswordActionStorageRules() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class));
        assertColumnsExist(jdbc, List.of(
                "id", "request_id", "action_type", "service_account_id", "owner_company_id",
                "assigned_user_id", "cors_account_id", "account", "status", "operator_user_id",
                "operator_user_name", "last_error_code", "last_error_message", "completed_at",
                "created_at", "updated_at", "version", "active_reset_account_id"));
        assertEquals("utf8mb4_bin", jdbc.queryForObject(
                "SELECT collation_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_password_action' "
                        + "AND column_name = 'request_id'", String.class));
        assertGeneratedStored(jdbc, "active_reset_account_id");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_password_action' "
                        + "AND (LOWER(column_name) LIKE '%password%' OR LOWER(column_name) LIKE '%secret%')",
                Integer.class));

        assertIndex(jdbc, "uk_password_action_request", 0);
        assertIndexColumns(jdbc, "uk_password_action_request", List.of("request_id"));
        assertIndex(jdbc, "uk_password_action_active_reset", 0);
        assertIndexColumns(jdbc, "uk_password_action_active_reset", List.of("active_reset_account_id"));
        assertIndex(jdbc, "idx_password_action_account_created", 1);
        assertIndexColumns(jdbc, "idx_password_action_account_created",
                List.of("service_account_id", "created_at", "id"));
        assertIndex(jdbc, "idx_password_action_type_status", 1);
        assertIndexColumns(jdbc, "idx_password_action_type_status",
                List.of("action_type", "status", "created_at", "id"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_password_action' "
                        + "AND referenced_table_name IS NOT NULL", Integer.class));

        assertCaseSensitiveRequestIds(jdbc);
        assertActiveResetBlocksSameAccount(jdbc, "PROCESSING");
        assertActiveResetBlocksSameAccount(jdbc, "MANUAL_REVIEW");
        assertFinishedResetAllowsAccountReuse(jdbc, "SUCCEEDED");
        assertFinishedResetAllowsAccountReuse(jdbc, "FAILED");
        assertRepeatedRevealIsAllowed(jdbc);
        assertConcurrentActiveResetIsUnique(jdbc);
    }

    private void assertCaseSensitiveRequestIds(JdbcTemplate jdbc) {
        insertAction(jdbc, 31_001L, "Password-Case", "REVEAL", "SUCCEEDED");
        insertAction(jdbc, 31_002L, "password-case", "REVEAL", "SUCCEEDED");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_password_action "
                        + "WHERE request_id IN ('Password-Case', 'password-case')", Integer.class));
        assertThrows(DuplicateKeyException.class, () ->
                insertAction(jdbc, 31_003L, "Password-Case", "REVEAL", "SUCCEEDED"));
    }

    private void assertActiveResetBlocksSameAccount(JdbcTemplate jdbc, String status) {
        long accountId = status.equals("PROCESSING") ? 32_001L : 32_002L;
        String requestId = "ACTIVE-RESET-" + status;
        insertAction(jdbc, accountId, requestId, "RESET", status);
        assertEquals(accountId, activeResetAccount(jdbc, requestId));
        assertThrows(DuplicateKeyException.class, () ->
                insertAction(jdbc, accountId, requestId + "-SECOND", "RESET", "PROCESSING"));
    }

    private void assertFinishedResetAllowsAccountReuse(JdbcTemplate jdbc, String status) {
        long accountId = status.equals("SUCCEEDED") ? 33_001L : 33_002L;
        String requestId = "FINISHED-RESET-" + status;
        insertAction(jdbc, accountId, requestId, "RESET", status);
        assertNull(activeResetAccount(jdbc, requestId));

        insertAction(jdbc, accountId, requestId + "-RETRY", "RESET", "PROCESSING");
        assertEquals(accountId, activeResetAccount(jdbc, requestId + "-RETRY"));
    }

    private void assertRepeatedRevealIsAllowed(JdbcTemplate jdbc) {
        insertAction(jdbc, 34_001L, "REVEAL-FIRST", "REVEAL", "SUCCEEDED");
        insertAction(jdbc, 34_001L, "REVEAL-SECOND", "REVEAL", "SUCCEEDED");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_password_action "
                        + "WHERE service_account_id = 34001 AND action_type = 'REVEAL'", Integer.class));
        assertNull(activeResetAccount(jdbc, "REVEAL-FIRST"));
        assertNull(activeResetAccount(jdbc, "REVEAL-SECOND"));
    }

    private void assertConcurrentActiveResetIsUnique(JdbcTemplate jdbc) throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = workers.submit(() -> tryInsertConcurrentReset(
                    dataSource, ready, start, "CONCURRENT-RESET-FIRST"));
            Future<Boolean> second = workers.submit(() -> tryInsertConcurrentReset(
                    dataSource, ready, start, "CONCURRENT-RESET-SECOND"));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both reset workers should be ready");
            start.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS),
                    "exactly one active reset reservation should commit");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM account_password_action WHERE active_reset_account_id = 35001",
                    Integer.class));
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private boolean tryInsertConcurrentReset(DataSource dataSource, CountDownLatch ready,
                                             CountDownLatch start, String requestId) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS), "reset workers should start together");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO account_password_action (request_id, action_type, service_account_id, "
                            + "owner_company_id, cors_account_id, account, status) "
                            + "VALUES (?, 'RESET', 35001, 100, 'cors-35001', 'account-35001', 'PROCESSING')")) {
                statement.setString(1, requestId);
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                if (exception.getErrorCode() == 1062) {
                    return false;
                }
                throw exception;
            }
        }
    }

    private void insertAction(JdbcTemplate jdbc, long serviceAccountId, String requestId,
                              String actionType, String status) {
        jdbc.update("INSERT INTO account_password_action (request_id, action_type, service_account_id, "
                        + "owner_company_id, cors_account_id, account, status) "
                        + "VALUES (?, ?, ?, 100, ?, ?, ?)",
                requestId, actionType, serviceAccountId, "cors-" + serviceAccountId,
                "account-" + serviceAccountId, status);
    }

    private Long activeResetAccount(JdbcTemplate jdbc, String requestId) {
        return jdbc.queryForObject(
                "SELECT active_reset_account_id FROM account_password_action WHERE request_id = ?",
                Long.class, requestId);
    }

    private void assertColumnsExist(JdbcTemplate jdbc, List<String> columns) {
        for (String column : columns) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'account_password_action' "
                            + "AND column_name = ?", Integer.class, column), column);
        }
    }

    private void assertGeneratedStored(JdbcTemplate jdbc, String column) {
        assertTrue(jdbc.queryForObject(
                "SELECT extra FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_password_action' "
                        + "AND column_name = ?", String.class, column)
                .toUpperCase().contains("STORED GENERATED"), column);
    }

    private void assertIndex(JdbcTemplate jdbc, String indexName, int nonUnique) {
        assertEquals(nonUnique, jdbc.queryForObject(
                "SELECT non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_password_action' "
                        + "AND index_name = ? LIMIT 1", Integer.class, indexName), indexName);
    }

    private void assertIndexColumns(JdbcTemplate jdbc, String indexName, List<String> expectedColumns) {
        assertEquals(expectedColumns, jdbc.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'account_password_action' "
                        + "AND index_name = ? ORDER BY seq_in_index", String.class, indexName), indexName);
    }
}

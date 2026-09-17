package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlV7AccountRenewalConcurrencyIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v7_account_renewal_concurrency");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Test
    void databaseUniquenessAndReserveRollbackHoldUnderConcurrentConnections() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertConcurrentUniqueConflict(dataSource,
                new RenewalInsert(31_001L, 41_001L, "RACE-SAME-CODE-A", "PROCESSING"),
                new RenewalInsert(31_002L, 41_001L, "RACE-SAME-CODE-B", "PROCESSING"));
        assertEquals(1, countByServiceCode(jdbc, 41_001L));

        assertConcurrentUniqueConflict(dataSource,
                new RenewalInsert(32_001L, 42_001L, "RACE-SAME-ACCOUNT-A", "PROCESSING"),
                new RenewalInsert(32_001L, 42_002L, "RACE-SAME-ACCOUNT-B", "PROCESSING"));
        assertEquals(1, countByAccount(jdbc, 32_001L));

        assertConcurrentUniqueConflict(dataSource,
                new RenewalInsert(33_001L, 43_001L, "RACE-SAME-REQUEST", "PROCESSING"),
                new RenewalInsert(33_002L, 43_002L, "RACE-SAME-REQUEST", "PROCESSING"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_renewal WHERE request_id = 'RACE-SAME-REQUEST'", Integer.class));

        assertFailedCodeCanBeReused(jdbc);
        assertProcessingAndManualReviewKeepTheirLocks(jdbc);
        assertReserveRollbackWhenOperationInsertFails(dataSource, jdbc);
        assertReserveDoesNotDeadlockFinalizingRenewal(dataSource, jdbc);
        assertFinalizeFailureRollsBackAndKeepsCodeReserved(dataSource, jdbc);
    }

    private void assertConcurrentUniqueConflict(DataSource dataSource,
                                                RenewalInsert first,
                                                RenewalInsert second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch connectionsReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<InsertOutcome> firstResult = executor.submit(
                () -> concurrentInsert(dataSource, first, connectionsReady, start));
        Future<InsertOutcome> secondResult = executor.submit(
                () -> concurrentInsert(dataSource, second, connectionsReady, start));

        try {
            assertTrue(connectionsReady.await(10, TimeUnit.SECONDS), "both MySQL connections should be ready");
            start.countDown();
            List<InsertOutcome> outcomes = List.of(
                    firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(outcome -> outcome == InsertOutcome.INSERTED).count());
            assertEquals(1, outcomes.stream().filter(outcome -> outcome == InsertOutcome.DUPLICATE_KEY).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "insert workers should terminate");
        }
    }

    private InsertOutcome concurrentInsert(DataSource dataSource, RenewalInsert renewal,
                                          CountDownLatch connectionsReady, CountDownLatch start) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            connectionsReady.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent insert start");
            }
            try {
                insertRenewal(connection, renewal);
                return InsertOutcome.INSERTED;
            } catch (SQLException exception) {
                if (exception.getErrorCode() == 1062) {
                    return InsertOutcome.DUPLICATE_KEY;
                }
                throw exception;
            }
        }
    }

    private void assertFailedCodeCanBeReused(JdbcTemplate jdbc) {
        insertRenewal(jdbc, new RenewalInsert(34_001L, 44_001L, "FAILED-CODE-REUSE-OLD", "FAILED"));
        assertNull(jdbc.queryForObject(
                "SELECT active_service_code_id FROM account_renewal WHERE request_id = 'FAILED-CODE-REUSE-OLD'",
                Long.class));
        assertNull(jdbc.queryForObject(
                "SELECT active_service_account_id FROM account_renewal WHERE request_id = 'FAILED-CODE-REUSE-OLD'",
                Long.class));
        insertRenewal(jdbc, new RenewalInsert(34_002L, 44_001L, "FAILED-CODE-REUSE-NEW", "PROCESSING"));
        assertEquals(2, countByServiceCode(jdbc, 44_001L));
        assertEquals(44_001L, jdbc.queryForObject(
                "SELECT active_service_code_id FROM account_renewal WHERE request_id = 'FAILED-CODE-REUSE-NEW'",
                Long.class));
    }

    private void assertProcessingAndManualReviewKeepTheirLocks(JdbcTemplate jdbc) {
        insertRenewal(jdbc, new RenewalInsert(35_001L, 45_001L, "PROCESSING-CODE-LOCK", "PROCESSING"));
        assertEquals(45_001L, jdbc.queryForObject(
                "SELECT active_service_code_id FROM account_renewal WHERE request_id = 'PROCESSING-CODE-LOCK'",
                Long.class));
        assertEquals(35_001L, jdbc.queryForObject(
                "SELECT active_service_account_id FROM account_renewal WHERE request_id = 'PROCESSING-CODE-LOCK'",
                Long.class));
        assertDuplicateRenewal(jdbc,
                new RenewalInsert(35_002L, 45_001L, "PROCESSING-CODE-LOCK-RETRY", "PROCESSING"));

        insertRenewal(jdbc, new RenewalInsert(35_003L, 45_002L, "MANUAL-CODE-LOCK", "MANUAL_REVIEW"));
        assertEquals(45_002L, jdbc.queryForObject(
                "SELECT active_service_code_id FROM account_renewal WHERE request_id = 'MANUAL-CODE-LOCK'",
                Long.class));
        assertEquals(35_003L, jdbc.queryForObject(
                "SELECT active_service_account_id FROM account_renewal WHERE request_id = 'MANUAL-CODE-LOCK'",
                Long.class));
        assertDuplicateRenewal(jdbc,
                new RenewalInsert(35_004L, 45_002L, "MANUAL-CODE-LOCK-RETRY", "PROCESSING"));

        insertRenewal(jdbc, new RenewalInsert(35_005L, 45_003L, "MANUAL-ACCOUNT-LOCK", "MANUAL_REVIEW"));
        assertDuplicateRenewal(jdbc,
                new RenewalInsert(35_005L, 45_004L, "MANUAL-ACCOUNT-LOCK-RETRY", "PROCESSING"));
    }

    private void assertReserveRollbackWhenOperationInsertFails(DataSource dataSource, JdbcTemplate jdbc)
            throws SQLException {
        long serviceCodeId = insertPendingServiceCode(jdbc, "RENEWAL-ROLLBACK-CODE");
        long serviceAccountId = 36_001L;
        String requestId = "RENEWAL-ROLLBACK-REQUEST";

        jdbc.update("INSERT INTO cors_operation (request_id, operation_type, biz_type, biz_id, status) "
                        + "VALUES (?, 'BATCH_CREATE_ACCOUNT', 'EXCHANGE_BATCH', ?, 'PENDING')",
                requestId,
                serviceAccountId);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertEquals(1, reserveServiceCode(connection, serviceCodeId, requestId));
                insertRenewal(connection,
                        new RenewalInsert(serviceAccountId, serviceCodeId, requestId, "PROCESSING"));

                SQLException duplicateOperation = assertThrows(SQLException.class,
                        () -> insertRenewalOperation(connection, requestId, serviceAccountId));
                assertEquals(1062, duplicateOperation.getErrorCode(),
                        "operation insert should fail on the existing requestId unique index");
            } finally {
                connection.rollback();
            }
        }

        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM service_code WHERE id = ?", String.class, serviceCodeId));
        assertNull(jdbc.queryForObject(
                "SELECT processing_type FROM service_code WHERE id = ?", String.class, serviceCodeId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_renewal WHERE request_id = ?", Integer.class, requestId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cors_operation WHERE request_id = ?", Integer.class, requestId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT version FROM service_code WHERE id = ?", Long.class, serviceCodeId));
        assertNull(jdbc.queryForObject(
                "SELECT processing_request_id FROM service_code WHERE id = ?", String.class, serviceCodeId));
    }

    private void assertReserveDoesNotDeadlockFinalizingRenewal(DataSource dataSource, JdbcTemplate jdbc)
            throws Exception {
        long serviceAccountId = 37_001L;
        String requestId = "RENEWAL-LOCK-ORDER";
        long serviceCodeId = insertPendingServiceCode(jdbc, "RENEWAL-LOCK-CODE");
        jdbc.update("INSERT INTO service_account (id, owner_company_id, source_service_code_id, service_type, "
                        + "spec_code, display_name, duration_days, account_silence_days) "
                        + "VALUES (?, 100, ?, 'CORS', 'RENEWAL', '续期规格', 30, 180)",
                serviceAccountId, serviceCodeId);
        insertRenewal(jdbc, new RenewalInsert(serviceAccountId, serviceCodeId, requestId, "PROCESSING"));
        SqlSessionFactory sqlSessionFactory = accountRenewalSqlSessionFactory(dataSource);
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM account_renewal WHERE request_id = ?", String.class, requestId));
        try (SqlSession probe = sqlSessionFactory.openSession()) {
            AccountRenewal active = probe.getMapper(AccountRenewalMapper.class)
                    .selectActiveByAccount(serviceAccountId);
            assertTrue(active != null, "MyBatis should see the committed active renewal fixture");
            assertEquals("PROCESSING", active.getStatus());
        }

        CountDownLatch renewalLocked = new CountDownLatch(1);
        CountDownLatch accountLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> finalizeLike = executor.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM account_renewal WHERE request_id = ? FOR UPDATE")) {
                    statement.setString(1, requestId);
                    statement.executeQuery().close();
                }
                renewalLocked.countDown();
                if (!accountLocked.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("reserve did not acquire the account lock");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM service_account WHERE id = ? FOR UPDATE")) {
                    statement.setLong(1, serviceAccountId);
                    statement.executeQuery().close();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE account_renewal SET status = 'COMPLETED', "
                                + "active_service_code_id = service_code_id, active_service_account_id = NULL "
                                + "WHERE request_id = ?")) {
                    statement.setString(1, requestId);
                    statement.executeUpdate();
                }
                connection.commit();
                return null;
            }
        });
        Future<String> reserveLike = executor.submit(() -> {
            if (!renewalLocked.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("finalize did not acquire the renewal lock");
            }
            try (SqlSession session = sqlSessionFactory.openSession()) {
                Connection connection = session.getConnection();
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM service_account WHERE id = ? FOR UPDATE")) {
                    statement.setLong(1, serviceAccountId);
                    statement.executeQuery().close();
                }
                accountLocked.countDown();
                AccountRenewal active = session.getMapper(AccountRenewalMapper.class)
                        .selectActiveByAccount(serviceAccountId);
                session.commit();
                return active == null ? null : active.getStatus();
            }
        });

        try {
            assertEquals("PROCESSING", reserveLike.get(10, TimeUnit.SECONDS),
                    "the account lock serializes reserve while the active-renewal read remains non-locking");
            finalizeLike.get(10, TimeUnit.SECONDS);
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "SELECT status FROM account_renewal WHERE request_id = ?", String.class, requestId));
        } finally {
            renewalLocked.countDown();
            accountLocked.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "lock-order workers should terminate");
        }
    }

    private SqlSessionFactory accountRenewalSqlSessionFactory(DataSource dataSource) throws Exception {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AccountRenewalMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    private void assertFinalizeFailureRollsBackAndKeepsCodeReserved(DataSource dataSource, JdbcTemplate jdbc)
            throws SQLException {
        long serviceAccountId = 38_001L;
        String requestId = "RENEWAL-FINALIZE-ROLLBACK";
        String originalExpiry = "2030-01-01 00:00:00.000000";
        jdbc.update("INSERT INTO service_account (id, cors_account_id, account, owner_company_id, "
                        + "source_service_code_id, spec_code, display_name, service_type, duration_days, account_silence_days, "
                        + "cors_status, cors_activation_status, activated_at, expire_at, cors_updated_at) "
                        + "VALUES (?, ?, ?, 100, ?, 'RENEWAL', '续期规格', 'CORS', 30, 180, 'ACTIVE', 'ACTIVE', "
                        + "'2029-01-01 00:00:00.000', ?, '2029-01-01 00:00:00.000')",
                serviceAccountId, "cors-" + serviceAccountId, "account-" + serviceAccountId,
                serviceAccountId, originalExpiry);
        long serviceCodeId = insertPendingServiceCode(jdbc, "RENEWAL-FINALIZE-ROLLBACK-CODE");
        jdbc.update("UPDATE service_code SET status = 'PROCESSING', processing_type = 'RENEWAL', "
                        + "processing_request_id = ?, version = version + 1 WHERE id = ?",
                requestId, serviceCodeId);
        insertRenewal(jdbc, new RenewalInsert(serviceAccountId, serviceCodeId, requestId, "PROCESSING"));
        Long renewalId = jdbc.queryForObject(
                "SELECT id FROM account_renewal WHERE request_id = ?", Long.class, requestId);
        jdbc.update("INSERT INTO cors_operation (request_id, operation_type, biz_type, biz_id, "
                        + "service_account_id, status, retry_count, version) "
                        + "VALUES (?, 'RENEW_ACCOUNT', 'ACCOUNT_RENEWAL', ?, ?, 'CLAIMED', 0, 1)",
                requestId, renewalId, serviceAccountId);

        String triggerName = "trg_test_renewal_finalize_failure";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER " + triggerName + " BEFORE UPDATE ON cors_operation "
                    + "FOR EACH ROW BEGIN IF NEW.status = 'SUCCEEDED' THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected finalize failure'; "
                    + "END IF; END");
        }

        try {
            boolean failedAtOperationUpdate = false;
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE service_account SET expire_at = '2031-01-01 00:00:00.000' WHERE id = ?")) {
                        statement.setLong(1, serviceAccountId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE service_code SET status = 'CONSUMED', consume_type = 'RENEWAL', "
                                    + "consumed_at = CURRENT_TIMESTAMP(3), processing_type = NULL, "
                                    + "processing_request_id = NULL WHERE id = ? AND status = 'PROCESSING'")) {
                        statement.setLong(1, serviceCodeId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE account_renewal SET status = 'COMPLETED', "
                                    + "active_service_code_id = service_code_id, active_service_account_id = NULL "
                                    + "WHERE request_id = ?")) {
                        statement.setString(1, requestId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE cors_operation SET status = 'SUCCEEDED' WHERE request_id = ?")) {
                        statement.setString(1, requestId);
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException exception) {
                    failedAtOperationUpdate = exception.getErrorCode() == 1644;
                    connection.rollback();
                }
            }
            assertTrue(failedAtOperationUpdate, "the injected operation update should fail finalize");
            assertEquals(originalExpiry, jdbc.queryForObject(
                    "SELECT DATE_FORMAT(expire_at, '%Y-%m-%d %H:%i:%s.%f') FROM service_account WHERE id = ?",
                    String.class, serviceAccountId));
            assertEquals("PROCESSING", jdbc.queryForObject(
                    "SELECT status FROM service_code WHERE id = ?", String.class, serviceCodeId));
            assertEquals("RENEWAL", jdbc.queryForObject(
                    "SELECT processing_type FROM service_code WHERE id = ?", String.class, serviceCodeId));
            assertEquals(requestId, jdbc.queryForObject(
                    "SELECT processing_request_id FROM service_code WHERE id = ?", String.class, serviceCodeId));
            assertEquals("PROCESSING", jdbc.queryForObject(
                    "SELECT status FROM account_renewal WHERE request_id = ?", String.class, requestId));
            assertEquals("CLAIMED", jdbc.queryForObject(
                    "SELECT status FROM cors_operation WHERE request_id = ?", String.class, requestId));

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement renewalUpdate = connection.prepareStatement(
                        "UPDATE account_renewal SET status = 'MANUAL_REVIEW', "
                                + "active_service_code_id = service_code_id, "
                                + "active_service_account_id = service_account_id WHERE request_id = ?");
                     PreparedStatement operationUpdate = connection.prepareStatement(
                             "UPDATE cors_operation SET status = 'MANUAL_REVIEW' WHERE request_id = ?")) {
                    renewalUpdate.setString(1, requestId);
                    renewalUpdate.executeUpdate();
                    operationUpdate.setString(1, requestId);
                    operationUpdate.executeUpdate();
                }
                connection.commit();
            }
            assertEquals("MANUAL_REVIEW", jdbc.queryForObject(
                    "SELECT status FROM account_renewal WHERE request_id = ?", String.class, requestId));
            assertEquals("MANUAL_REVIEW", jdbc.queryForObject(
                    "SELECT status FROM cors_operation WHERE request_id = ?", String.class, requestId));
            assertEquals("PROCESSING", jdbc.queryForObject(
                    "SELECT status FROM service_code WHERE id = ?", String.class, serviceCodeId));
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS " + triggerName);
            }
        }
    }

    private int reserveServiceCode(Connection connection, long serviceCodeId, String requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE service_code SET status = 'PROCESSING', processing_type = 'RENEWAL', "
                        + "processing_request_id = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND status = 'PENDING'")) {
            statement.setString(1, requestId);
            statement.setLong(2, serviceCodeId);
            return statement.executeUpdate();
        }
    }

    private void insertRenewalOperation(Connection connection, String requestId, long serviceAccountId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO cors_operation (request_id, operation_type, biz_type, biz_id, "
                        + "service_account_id, status) VALUES (?, 'RENEW_ACCOUNT', 'ACCOUNT_RENEWAL', ?, ?, 'PENDING')")) {
            statement.setString(1, requestId);
            statement.setLong(2, serviceAccountId);
            statement.setLong(3, serviceAccountId);
            statement.executeUpdate();
        }
    }

    private long insertPendingServiceCode(JdbcTemplate jdbc, String code) {
        jdbc.update("INSERT INTO service_code (code, owner_company_id, spec_code, service_type, duration_days, "
                        + "code_silence_days, expire_at, status, version) "
                        + "VALUES (?, 100, 'RENEWAL', 'CORS', 1, 0, CURRENT_TIMESTAMP(3) + INTERVAL 1 DAY, 'PENDING', 0)",
                code);
        return jdbc.queryForObject("SELECT id FROM service_code WHERE code = ?", Long.class, code);
    }

    private void insertRenewal(JdbcTemplate jdbc, RenewalInsert renewal) {
        jdbc.update("INSERT INTO account_renewal (service_account_id, service_code_id, owner_company_id, "
                        + "spec_code, service_type, duration_days, code_silence_days, service_code_snapshot, request_id, "
                        + "status, active_service_code_id, active_service_account_id) "
                        + "VALUES (?, ?, 100, 'RENEWAL', 'CORS', 1, 0, CAST('{}' AS JSON), ?, ?, ?, ?)",
                renewal.serviceAccountId(), renewal.serviceCodeId(), renewal.requestId(), renewal.status(),
                renewal.activeServiceCodeId(), renewal.activeServiceAccountId());
    }

    private void insertRenewal(Connection connection, RenewalInsert renewal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO account_renewal (service_account_id, service_code_id, owner_company_id, "
                        + "spec_code, service_type, duration_days, code_silence_days, service_code_snapshot, request_id, "
                        + "status, active_service_code_id, active_service_account_id) "
                        + "VALUES (?, ?, 100, 'RENEWAL', 'CORS', 1, 0, CAST('{}' AS JSON), ?, ?, ?, ?)")) {
            statement.setLong(1, renewal.serviceAccountId());
            statement.setLong(2, renewal.serviceCodeId());
            statement.setString(3, renewal.requestId());
            statement.setString(4, renewal.status());
            statement.setObject(5, renewal.activeServiceCodeId());
            statement.setObject(6, renewal.activeServiceAccountId());
            statement.executeUpdate();
        }
    }

    private void assertDuplicateRenewal(JdbcTemplate jdbc, RenewalInsert renewal) {
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> insertRenewal(jdbc, renewal));
    }

    private int countByServiceCode(JdbcTemplate jdbc, long serviceCodeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_renewal WHERE service_code_id = ?",
                Integer.class, serviceCodeId);
    }

    private int countByAccount(JdbcTemplate jdbc, long serviceAccountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_renewal WHERE service_account_id = ?",
                Integer.class, serviceAccountId);
    }

    private enum InsertOutcome {
        INSERTED,
        DUPLICATE_KEY
    }

    private record RenewalInsert(long serviceAccountId, long serviceCodeId, String requestId, String status) {
        private Long activeServiceCodeId() {
            return switch (status) {
                case "PROCESSING", "COMPLETED", "MANUAL_REVIEW" -> serviceCodeId;
                default -> null;
            };
        }

        private Long activeServiceAccountId() {
            return switch (status) {
                case "PROCESSING", "MANUAL_REVIEW" -> serviceAccountId;
                default -> null;
            };
        }
    }
}

package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.vantix.application.cors.CorsOperationRetryJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationClaimService;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationFinalizeService;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationProcessor;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationReserveOutcome;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationReserveService;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationRetryJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "vantix.cors.force-activation.enabled=true",
        "vantix.cors.force-activation.candidate-batch-size=1",
        "vantix.cors.account-status-sync.enabled=true",
        "vantix.cors-operation.enabled=false"
})
@Import(MySqlForceActivationWorkflowIntegrationTest.ConditionalGatewayDefinition.class)
@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlForceActivationWorkflowIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_force_activation_workflow");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ServiceAccountMapper accountMapper;
    @Autowired
    private AccountForceActivationReserveService reserveService;
    @Autowired
    private AccountForceActivationClaimService claimService;
    @Autowired
    private AccountForceActivationFinalizeService finalizeService;
    @Autowired
    private AccountForceActivationProcessor processor;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private AccountStatusReconcileService reconcileService;
    @Autowired
    private CorsForceActivationProperties forceActivationProperties;
    @Autowired
    private Clock clock;

    @Autowired
    private CorsAccountStatusGateway statusGateway;

    @TestConfiguration(proxyBeanMethods = false)
    static class ConditionalGatewayDefinition {
        @Bean
        CorsAccountStatusGateway conditionalGatewayDefinition() {
            return org.mockito.Mockito.mock(CorsAccountStatusGateway.class);
        }

    }
    @MockBean
    private CorsForceActivationGateway forceActivationGateway;
    @MockBean
    private CorsAccountGateway corsAccountGateway;
    @MockBean
    private CorsOperationRetryJob corsOperationRetryJob;
    @MockBean
    private AccountStatusReconcileJob reconcileJob;
    @MockBean
    private AccountForceActivationJob forceActivationJob;
    @MockBean
    private AccountForceActivationRetryJob forceActivationRetryJob;

    private long nextSourceCodeId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void resetData() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_force_activation_success");
        jdbc.update("DELETE FROM cors_operation");
        jdbc.update("DELETE FROM service_account");
        nextSourceCodeId = 4000L;
    }

    @Test
    void mysql57CandidateQueryIncludesOnlyDueWaitingAccountsWithoutAnyExistingOperation() {
        LocalDateTime now = now();
        long dueWithNullSync = insertAccount("force-due-null-sync", "WAITING_ACTIVATION",
                now.minusMinutes(10), null);
        long dueWithPastSync = insertAccount("force-due-past-sync", "WAITING_ACTIVATION",
                now.minusMinutes(5), now.minusMinutes(1));
        insertAccount("force-future-force-time", "WAITING_ACTIVATION",
                now.plusMinutes(1), null);
        insertAccount("force-active", "ACTIVE", now.minusMinutes(4), null);
        insertAccount(null, "WAITING_ACTIVATION", now.minusMinutes(3), null);
        insertAccount("", "WAITING_ACTIVATION", now.minusMinutes(2), null);
        insertAccount("force-future-sync", "WAITING_ACTIVATION",
                now.minusMinutes(1), now.plusMinutes(1));

        long pending = insertAccount("force-has-pending", "WAITING_ACTIVATION",
                now.minusSeconds(50), null);
        long retryWait = insertAccount("force-has-retry-wait", "WAITING_ACTIVATION",
                now.minusSeconds(40), null);
        long succeeded = insertAccount("force-has-succeeded", "WAITING_ACTIVATION",
                now.minusSeconds(30), null);
        long manualReview = insertAccount("force-has-manual-review", "WAITING_ACTIVATION",
                now.minusSeconds(20), null);
        insertForceActivationOperation(pending, "PENDING");
        insertForceActivationOperation(retryWait, "RETRY_WAIT");
        insertForceActivationOperation(succeeded, "SUCCEEDED");
        insertForceActivationOperation(manualReview, "MANUAL_REVIEW");

        assertEquals(List.of(dueWithNullSync),
                accountMapper.selectDueForceActivationCandidateIds(now, 1));
        assertEquals(List.of(dueWithNullSync, dueWithPastSync),
                accountMapper.selectDueForceActivationCandidateIds(now, 10));
        verifyNoInteractions(forceActivationGateway);
    }

    @Test
    void unknownPreflightSchedulesRetryAndLetsNextAccountIntoLimitOneBatch() {
        LocalDateTime now = now();
        long first = insertAccount("force-unknown-first", "WAITING_ACTIVATION",
                now.minusMinutes(20), null);
        long second = insertAccount("force-next-second", "WAITING_ACTIVATION",
                now.minusMinutes(19), null);
        when(statusGateway.getAccount("force-unknown-first"))
                .thenReturn(CorsAccountStatusResult.unknown("UPSTREAM_TIMEOUT", "not logged"));

        AccountForceActivationJob job = new AccountForceActivationJob(
                accountMapper, reconcileService, reserveService, forceActivationProperties, clock);
        job.scan();

        verify(statusGateway).getAccount("force-unknown-first");
        LocalDateTime nextStatusSyncAt = jdbc.queryForObject(
                "SELECT status_sync_next_at FROM service_account WHERE id = ?", LocalDateTime.class, first);
        assertNotNull(nextStatusSyncAt);
        assertTrue(nextStatusSyncAt.isAfter(now));
        assertEquals(List.of(second),
                accountMapper.selectDueForceActivationCandidateIds(now(), 1));
        assertEquals(0, operationCount(first));
        verifyNoInteractions(forceActivationGateway);
    }

    @Test
    void activePreflightSynchronizesAccountWithoutCreatingForceOperation() {
        LocalDateTime now = now();
        String corsAccountId = "force-preflight-active";
        long accountId = insertAccount(corsAccountId, "WAITING_ACTIVATION", now.minusMinutes(1), null);
        String account = jdbc.queryForObject(
                "SELECT account FROM service_account WHERE id = ?", String.class, accountId);
        CorsAccountSnapshot snapshot = new CorsAccountSnapshot(
                corsAccountId, account, "ACTIVE", "ACTIVE",
                now.minusHours(1).atOffset(ZoneOffset.ofHours(8)),
                now.plusDays(30).atOffset(ZoneOffset.ofHours(8)),
                now.minusDays(10).atOffset(ZoneOffset.ofHours(8)),
                now.atOffset(ZoneOffset.ofHours(8)));
        when(statusGateway.getAccount(corsAccountId))
                .thenReturn(CorsAccountStatusResult.success(snapshot));
        AccountForceActivationJob job = new AccountForceActivationJob(
                accountMapper, reconcileService, reserveService, forceActivationProperties, clock);

        job.scan();

        verify(statusGateway).getAccount(corsAccountId);
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT cors_activation_status FROM service_account WHERE id = ?", String.class, accountId));
        assertEquals(0, operationCount(accountId));
        verifyNoInteractions(forceActivationGateway);
    }

    @Test
    void waitingPreflightReservesOperationButDoesNotCallForceGateway() {
        LocalDateTime now = now();
        String corsAccountId = "force-preflight-waiting";
        long accountId = insertAccount(corsAccountId, "WAITING_ACTIVATION", now.minusMinutes(1), null);
        String account = jdbc.queryForObject(
                "SELECT account FROM service_account WHERE id = ?", String.class, accountId);
        CorsAccountSnapshot snapshot = new CorsAccountSnapshot(
                corsAccountId, account, "ENABLED", "WAITING_ACTIVATION",
                null, null,
                now.minusDays(10).atOffset(ZoneOffset.ofHours(8)),
                now.atOffset(ZoneOffset.ofHours(8)));
        when(statusGateway.getAccount(corsAccountId))
                .thenReturn(CorsAccountStatusResult.success(snapshot));
        AccountForceActivationJob job = new AccountForceActivationJob(
                accountMapper, reconcileService, reserveService, forceActivationProperties, clock);

        job.scan();

        verify(statusGateway).getAccount(corsAccountId);
        assertEquals("WAITING_ACTIVATION", jdbc.queryForObject(
                "SELECT cors_activation_status FROM service_account WHERE id = ?", String.class, accountId));
        assertEquals(1, operationCount(accountId));
        assertEquals("FORCE_ACTIVATE_ACCOUNT", jdbc.queryForObject(
                "SELECT operation_type FROM cors_operation WHERE biz_id = ?", String.class, accountId));
        verifyNoInteractions(forceActivationGateway);
    }

    @Test
    void concurrentReserveOfSameAccountCreatesOneOperationAndReturnsCreatedAndExisting() throws Exception {
        long accountId = insertAccount("force-concurrent", "WAITING_ACTIVATION",
                now().minusMinutes(1), null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<AccountForceActivationReserveOutcome> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return reserveService.reserve(accountId);
            });
            Future<AccountForceActivationReserveOutcome> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return reserveService.reserve(accountId);
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Set<AccountForceActivationReserveOutcome> outcomes = EnumSet.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(EnumSet.of(AccountForceActivationReserveOutcome.CREATED,
                    AccountForceActivationReserveOutcome.EXISTING), outcomes);
            assertEquals(1, operationCount(accountId));
            verifyNoInteractions(forceActivationGateway);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void forceActivationDueQueryExcludesExchangeAndNonDueStatuses() {
        long pending = insertOperation("FORCE_ACTIVATE_ACCOUNT", "ACCOUNT_FORCE_ACTIVATION",
                "PENDING", null);
        long retryDue = insertOperation("FORCE_ACTIVATE_ACCOUNT", "ACCOUNT_FORCE_ACTIVATION",
                "RETRY_WAIT", now().minusSeconds(1));
        insertOperation("FORCE_ACTIVATE_ACCOUNT", "ACCOUNT_FORCE_ACTIVATION",
                "RETRY_WAIT", now().plusMinutes(1));
        insertOperation("FORCE_ACTIVATE_ACCOUNT", "ACCOUNT_FORCE_ACTIVATION",
                "CLAIMED", null);
        insertOperation("FORCE_ACTIVATE_ACCOUNT", "ACCOUNT_FORCE_ACTIVATION",
                "MANUAL_REVIEW", null);
        insertOperation("BATCH_CREATE_ACCOUNT", "EXCHANGE_BATCH", "PENDING", null);

        assertEquals(List.of(pending, retryDue), claimService.findDueOperationIds(10));
    }

    @Test
    void retryQueryAndForceActivationRunOutsideCallerTransactionAndReuseRequestId() {
        long accountId = insertAccount("force-transaction-boundary", "WAITING_ACTIVATION",
                now().minusMinutes(1), null);
        String requestId = "FA-TX-" + UUID.randomUUID();
        jdbc.update("INSERT INTO cors_operation "
                        + "(request_id, operation_type, biz_type, biz_id, service_account_id, status, "
                        + "retry_count, next_retry_at, version) "
                        + "VALUES (?, 'FORCE_ACTIVATE_ACCOUNT', 'ACCOUNT_FORCE_ACTIVATION', ?, ?, "
                        + "'RETRY_WAIT', 0, ?, 0)",
                requestId, accountId, accountId, now().minusSeconds(1));
        long operationId = jdbc.queryForObject(
                "SELECT id FROM cors_operation WHERE request_id = ?", Long.class, requestId);

        when(forceActivationGateway.queryForceActivation(requestId)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(requestId, invocation.getArgument(0));
            return CorsForceActivationResult.outcome(CorsOutcome.NOT_FOUND, "NOT_FOUND", "not found");
        });
        when(forceActivationGateway.forceActivate(any(CorsForceActivationRequest.class)))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    CorsForceActivationRequest request = invocation.getArgument(0);
                    assertEquals(requestId, request.requestId());
                    assertEquals("force-transaction-boundary", request.accountId());
                    return CorsForceActivationResult.outcome(CorsOutcome.UNKNOWN, "TIMEOUT", "not logged");
                });

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> processor.process(operationId));

        assertEquals("RETRY_WAIT", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE id = ?", String.class, operationId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT retry_count FROM cors_operation WHERE id = ?", Integer.class, operationId));
    }

    @Test
    void successFinalizeUpdatesAccountAndOperationTogether() {
        LocalDateTime now = now();
        long accountId = insertAccount("force-finalize-success", "WAITING_ACTIVATION",
                now.minusMinutes(1), null);
        long operationId = insertForceActivationOperation(accountId, "CLAIMED");
        String requestId = jdbc.queryForObject(
                "SELECT request_id FROM cors_operation WHERE id = ?", String.class, operationId);
        String account = jdbc.queryForObject(
                "SELECT account FROM service_account WHERE id = ?", String.class, accountId);
        OffsetDateTime activatedAt = now.minusHours(1).atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime expireAt = now.plusDays(30).atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime createdAt = now.minusDays(10).atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime updatedAt = now.atOffset(ZoneOffset.ofHours(8));
        CorsAccountSnapshot snapshot = new CorsAccountSnapshot(
                "force-finalize-success", account, "ACTIVE", "ACTIVE",
                activatedAt, expireAt, createdAt, updatedAt);

        finalizeService.finalizeSuccess(operationId, 0L,
                CorsForceActivationResult.success(requestId, snapshot));

        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT cors_activation_status FROM service_account WHERE id = ?", String.class, accountId));
        assertEquals(now.minusHours(1), jdbc.queryForObject(
                "SELECT activated_at FROM service_account WHERE id = ?", LocalDateTime.class, accountId));
        assertEquals(now.plusDays(30), jdbc.queryForObject(
                "SELECT expire_at FROM service_account WHERE id = ?", LocalDateTime.class, accountId));
        assertEquals(now, jdbc.queryForObject(
                "SELECT cors_updated_at FROM service_account WHERE id = ?", LocalDateTime.class, accountId));
        assertNotNull(jdbc.queryForObject(
                "SELECT last_sync_at FROM service_account WHERE id = ?", LocalDateTime.class, accountId));
        assertNotNull(jdbc.queryForObject(
                "SELECT status_sync_next_at FROM service_account WHERE id = ?", LocalDateTime.class, accountId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT status_sync_failure_count FROM service_account WHERE id = ?", Integer.class, accountId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT version FROM service_account WHERE id = ?", Long.class, accountId));
        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE id = ?", String.class, operationId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT version FROM cors_operation WHERE id = ?", Long.class, operationId));
        assertNotNull(jdbc.queryForObject(
                "SELECT force_activate_at FROM service_account WHERE id = ?", LocalDateTime.class, accountId));
    }

    @Test
    void operationMarkFailureRollsBackSuccessfulAccountProjection() {
        LocalDateTime now = now();
        long accountId = insertAccount("force-finalize-rollback", "WAITING_ACTIVATION",
                now.minusMinutes(1), null);
        long operationId = insertForceActivationOperation(accountId, "CLAIMED");
        String requestId = jdbc.queryForObject(
                "SELECT request_id FROM cors_operation WHERE id = ?", String.class, operationId);
        String account = jdbc.queryForObject(
                "SELECT account FROM service_account WHERE id = ?", String.class, accountId);
        CorsAccountSnapshot snapshot = new CorsAccountSnapshot(
                "force-finalize-rollback", account, "ACTIVE", "ACTIVE",
                now.minusHours(1).atOffset(ZoneOffset.ofHours(8)),
                now.plusDays(30).atOffset(ZoneOffset.ofHours(8)),
                now.minusDays(10).atOffset(ZoneOffset.ofHours(8)),
                now.atOffset(ZoneOffset.ofHours(8)));
        jdbc.execute("CREATE TRIGGER trg_fail_force_activation_success "
                + "BEFORE UPDATE ON cors_operation FOR EACH ROW "
                + "BEGIN IF OLD.id = " + operationId + " AND NEW.status = 'SUCCEEDED' "
                + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected operation update failure'; "
                + "END IF; END");

        assertThrows(DataAccessException.class, () -> finalizeService.finalizeSuccess(
                operationId, 0L, CorsForceActivationResult.success(requestId, snapshot)));

        assertEquals("WAITING_ACTIVATION", jdbc.queryForObject(
                "SELECT cors_activation_status FROM service_account WHERE id = ?", String.class, accountId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT version FROM service_account WHERE id = ?", Long.class, accountId));
        assertEquals("CLAIMED", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE id = ?", String.class, operationId));
    }

    private long insertOperation(String operationType, String bizType, String status,
                                 LocalDateTime nextRetryAt) {
        String requestId = "force-due-test-" + UUID.randomUUID();
        long bizId = 900_000L + nextSourceCodeId++;
        jdbc.update("INSERT INTO cors_operation "
                        + "(request_id, operation_type, biz_type, biz_id, service_account_id, status, "
                        + "retry_count, next_retry_at, version) VALUES (?, ?, ?, ?, ?, ?, 0, ?, 0)",
                requestId, operationType, bizType, bizId, bizId, status, nextRetryAt);
        return jdbc.queryForObject("SELECT id FROM cors_operation WHERE request_id = ?", Long.class, requestId);
    }

    private long insertAccount(String corsAccountId, String activationStatus,
                               LocalDateTime forceActivateAt, LocalDateTime statusSyncNextAt) {
        long sourceCodeId = nextSourceCodeId++;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO service_account "
                            + "(cors_account_id, account, owner_company_id, source_service_code_id, service_type, "
                            + "duration_value, duration_unit, account_silence_months, cors_activation_status, "
                            + "force_activate_at, status_sync_next_at, version) "
                            + "VALUES (?, ?, 1, ?, 'CORS', 1, 'MONTH', 0, ?, ?, ?, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, corsAccountId);
            statement.setString(2, "force-account-" + sourceCodeId);
            statement.setLong(3, sourceCodeId);
            statement.setString(4, activationStatus);
            statement.setObject(5, forceActivateAt);
            statement.setObject(6, statusSyncNextAt);
            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();
        assertNotNull(id, "service account insert should return its generated id");
        return id.longValue();
    }

    private long insertForceActivationOperation(long accountId, String status) {
        String requestId = "force-test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO cors_operation "
                        + "(request_id, operation_type, biz_type, biz_id, service_account_id, status, retry_count, version) "
                        + "VALUES (?, 'FORCE_ACTIVATE_ACCOUNT', 'ACCOUNT_FORCE_ACTIVATION', ?, ?, ?, 0, 0)",
                requestId, accountId, accountId, status);
        return jdbc.queryForObject("SELECT id FROM cors_operation WHERE request_id = ?", Long.class, requestId);
    }

    private int operationCount(long accountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cors_operation "
                + "WHERE biz_type = 'ACCOUNT_FORCE_ACTIVATION' AND biz_id = ?", Integer.class, accountId);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}

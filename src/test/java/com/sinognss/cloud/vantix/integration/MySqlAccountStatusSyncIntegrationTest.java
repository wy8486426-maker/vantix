package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.vantix.application.cors.CorsOperationRetryJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileOutcome;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyOutcome;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.config.CorsAccountStatusSyncProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountCorsSnapshotUpdate;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountStatusSyncScheduleUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "vantix.cors.account-status-sync.enabled=true",
        "vantix.cors-operation.enabled=false"
})
@Import(MySqlAccountStatusSyncIntegrationTest.ConditionalGatewayDefinition.class)
class MySqlAccountStatusSyncIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_account_status_sync");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ServiceAccountMapper accountMapper;
    @Autowired
    private CorsAccountStateApplyService applyService;
    @Autowired
    private AccountStatusReconcileService reconcileService;
    @Autowired
    private AccountStatusSyncScheduleService scheduleService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Clock clock;

    @Autowired
    private CorsAccountStatusGateway statusGateway;

    @MockBean
    private CorsAccountGateway corsAccountGateway;

    @TestConfiguration(proxyBeanMethods = false)
    static class ConditionalGatewayDefinition {
        @Bean
        CorsAccountStatusGateway conditionalGatewayDefinition() {
            return org.mockito.Mockito.mock(CorsAccountStatusGateway.class);
        }

        @Bean
        AccountStatusSyncScheduleService accountStatusSyncScheduleService(
                ServiceAccountMapper mapper, CorsAccountStatusSyncProperties properties, Clock clock,
                CorsAccountStatusGateway gateway) {
            return new AccountStatusSyncScheduleService(mapper, properties, clock);
        }

        @Bean
        AccountStatusReconcileService accountStatusReconcileService(
                ServiceAccountMapper mapper, CorsAccountStatusGateway gateway,
                CorsAccountStateApplyService applyService, AccountStatusSyncScheduleService scheduleService) {
            return new AccountStatusReconcileService(mapper, gateway, applyService, scheduleService);
        }
    }
    @MockBean
    private CorsOperationRetryJob corsOperationRetryJob;
    @MockBean
    private AccountStatusReconcileJob reconcileJob;

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
        jdbc.update("DELETE FROM service_account");
        nextSourceCodeId = 1000L;
    }

    @Test
    void mysql57V5CreatesScheduleColumnsAndBothCompositeIndexes() {
        List<String> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'service_account'",
                String.class);
        assertTrue(columns.containsAll(List.of(
                "status_sync_next_at", "status_sync_last_attempt_at", "status_sync_failure_count")));

        assertEquals(List.of("status_sync_next_at", "id"),
                indexColumns("idx_service_account_sync_next"));
        assertEquals(List.of("cors_activation_status", "status_sync_next_at", "id"),
                indexColumns("idx_service_account_activation_sync"));
    }

    @Test
    void mysql57DueQueriesUsePriorityDueTimeAndLimits() {
        LocalDateTime now = now();
        long waitingNullDue = insertAccount("cors-waiting-null", "WAITING_ACTIVATION", null, null);
        long waitingPastDue = insertAccount("cors-waiting-past", "WAITING_ACTIVATION",
                now.minusMinutes(20), null);
        insertAccount("cors-waiting-future", "WAITING_ACTIVATION", null, now.plusMinutes(5));
        long activeNullDue = insertAccount("cors-active-null", "ACTIVE", null, null);
        long noActivationPastDue = insertAccount("cors-no-activation", null, null, now.minusMinutes(1));
        insertAccount("cors-active-future", "ACTIVE", null, now.plusMinutes(5));
        insertAccount(null, "WAITING_ACTIVATION", null, null);
        insertAccount("", "WAITING_ACTIVATION", null, null);

        assertEquals(List.of(waitingNullDue),
                accountMapper.selectDueWaitingActivationIds(now, 1));
        assertEquals(List.of(waitingNullDue, waitingPastDue),
                accountMapper.selectDueWaitingActivationIds(now, 10));
        assertEquals(List.of(activeNullDue, noActivationPastDue),
                accountMapper.selectDueOtherIds(now, 10));
        assertEquals(List.of(activeNullDue),
                accountMapper.selectDueOtherIds(now, 1));
    }

    @Test
    void mysql57CasUpdatesSnapshotAndSuccessScheduleTogetherAndOnlyOnce() {
        long id = insertAccount("cors-cas", "WAITING_ACTIVATION", null, null);
        jdbc.update("UPDATE service_account SET status_sync_failure_count = 5 WHERE id = ?", id);
        LocalDateTime now = now();
        ServiceAccountCorsSnapshotUpdate update = new ServiceAccountCorsSnapshotUpdate(
                id, 0L, "ACTIVE", "ACTIVE", now.minusMinutes(1), now.plusDays(10),
                now.minusDays(1), now, now, now, now.plusMinutes(10), now);

        assertEquals(1, accountMapper.updateCorsSnapshot(update));
        assertEquals(0, accountMapper.updateCorsSnapshot(update));
        ServiceAccount current = accountMapper.selectById(id);
        assertEquals(1L, current.getVersion());
        assertEquals("ACTIVE", current.getCorsStatus());
        assertEquals("ACTIVE", current.getCorsActivationStatus());
        assertEquals(now.minusDays(1), current.getCorsCreatedAt());
        assertEquals(now, current.getCorsUpdatedAt());
        assertEquals(now, current.getLastSyncAt());
        assertEquals(now, current.getStatusSyncLastAttemptAt());
        assertEquals(now.plusMinutes(10), current.getStatusSyncNextAt());
        assertEquals(0, current.getStatusSyncFailureCount());
    }

    @Test
    void mysql57FailureScheduleUsesVersionCasAndPreservesBusinessAndLastSyncFields() {
        long id = insertAccount("cors-failure-cas", "ACTIVE", null, null);
        LocalDateTime now = now();
        LocalDateTime lastSyncAt = now.minusDays(1);
        jdbc.update("UPDATE service_account SET cors_status = 'SUSPENDED', cors_activation_status = 'ACTIVE', "
                        + "last_sync_at = ?, status_sync_failure_count = 2, version = 5 WHERE id = ?",
                lastSyncAt, id);
        ServiceAccount local = accountMapper.selectById(id);

        assertTrue(scheduleService.markFailure(local));

        ServiceAccount after = accountMapper.selectById(id);
        assertEquals(6L, after.getVersion());
        assertEquals("SUSPENDED", after.getCorsStatus());
        assertEquals("ACTIVE", after.getCorsActivationStatus());
        assertEquals(lastSyncAt, after.getLastSyncAt());
        assertEquals(3, after.getStatusSyncFailureCount());
        assertEquals(after.getStatusSyncLastAttemptAt().plusMinutes(4), after.getStatusSyncNextAt());
        assertFalse(scheduleService.markFailure(local));
        assertEquals(6L, accountMapper.selectById(id).getVersion());
    }

    @Test
    void inconsistentSnapshotSchedulesFailureWithoutChangingProjection() {
        long id = insertAccount("cors-inconsistent", "WAITING_ACTIVATION", null, null);
        ServiceAccount before = accountMapper.selectById(id);
        LocalDateTime now = now();
        CorsAccountSnapshot snapshot = new CorsAccountSnapshot(
                "cors-inconsistent", "different-account", "ACTIVE", "ACTIVE",
                now.minusMinutes(1).atOffset(ZoneOffset.ofHours(8)),
                now.plusDays(1).atOffset(ZoneOffset.ofHours(8)),
                now.minusDays(1).atOffset(ZoneOffset.ofHours(8)),
                now.atOffset(ZoneOffset.ofHours(8)));
        when(statusGateway.getAccount("cors-inconsistent"))
                .thenReturn(CorsAccountStatusResult.success(snapshot));

        assertEquals(AccountStatusReconcileOutcome.INCONSISTENT, reconcileService.reconcileOne(id));

        ServiceAccount after = accountMapper.selectById(id);
        assertEquals(before.getCorsStatus(), after.getCorsStatus());
        assertEquals(before.getCorsActivationStatus(), after.getCorsActivationStatus());
        assertNull(after.getLastSyncAt());
        assertEquals(1, after.getStatusSyncFailureCount());
        assertTrue(after.getStatusSyncNextAt().isAfter(now()));
    }

    @Test
    void notFoundSchedulesRetryWithoutChangingBusinessStateOrLastSync() {
        long id = insertAccount("cors-missing", "WAITING_ACTIVATION", null, null);
        ServiceAccount before = accountMapper.selectById(id);
        when(statusGateway.getAccount("cors-missing"))
                .thenReturn(CorsAccountStatusResult.notFound("ACCOUNT_NOT_FOUND", "not logged"));

        assertEquals(AccountStatusReconcileOutcome.NOT_FOUND, reconcileService.reconcileOne(id));

        ServiceAccount after = accountMapper.selectById(id);
        assertEquals(before.getCorsStatus(), after.getCorsStatus());
        assertEquals(before.getCorsActivationStatus(), after.getCorsActivationStatus());
        assertNull(after.getLastSyncAt());
        assertNotNull(after.getStatusSyncLastAttemptAt());
        assertTrue(after.getStatusSyncNextAt().isAfter(now()));
        assertEquals(1, after.getStatusSyncFailureCount());
        assertTrue(accountMapper.selectDueWaitingActivationIds(now(), 10).isEmpty());
    }

    @Test
    void failedFrontBatchMovesOutOfQueueAndNextBatchRunsImmediately() {
        long first = insertAccount("cors-starve-1", "WAITING_ACTIVATION", null, null);
        long second = insertAccount("cors-starve-2", "WAITING_ACTIVATION", null, null);
        long third = insertAccount("cors-starve-3", "WAITING_ACTIVATION", null, null);
        long fourth = insertAccount("cors-starve-4", "WAITING_ACTIVATION", null, null);
        for (int index = 1; index <= 4; index++) {
            when(statusGateway.getAccount("cors-starve-" + index))
                    .thenReturn(CorsAccountStatusResult.unknown("UPSTREAM_TIMEOUT", "not logged"));
        }
        CorsAccountStatusSyncProperties properties = new CorsAccountStatusSyncProperties();
        properties.setBatchSize(2);
        AccountStatusReconcileJob job = new AccountStatusReconcileJob(
                accountMapper, reconcileService, properties, clock);

        job.reconcile();

        assertEquals(List.of(third, fourth),
                accountMapper.selectDueWaitingActivationIds(now(), 10));
        for (long id : List.of(first, second)) {
            ServiceAccount failed = accountMapper.selectById(id);
            assertNull(failed.getLastSyncAt());
            assertEquals(1, failed.getStatusSyncFailureCount());
            assertTrue(failed.getStatusSyncNextAt().isAfter(now()));
        }

        job.reconcile();

        var order = inOrder(statusGateway);
        order.verify(statusGateway).getAccount("cors-starve-1");
        order.verify(statusGateway).getAccount("cors-starve-2");
        order.verify(statusGateway).getAccount("cors-starve-3");
        order.verify(statusGateway).getAccount("cors-starve-4");
        assertEquals(1, accountMapper.selectById(third).getStatusSyncFailureCount());
        assertEquals(1, accountMapper.selectById(fourth).getStatusSyncFailureCount());
    }

    @Test
    void olderRemoteSnapshotCannotOverwriteNewerProjectionButAdvancesSuccessSchedule() {
        long id = insertAccount("cors-stale", "ACTIVE", null, null);
        LocalDateTime now = now();
        LocalDateTime localUpdatedAt = now.minusMinutes(1);
        LocalDateTime localExpireAt = now.plusDays(20);
        jdbc.update("UPDATE service_account SET cors_status = 'SUSPENDED', cors_activation_status = 'ACTIVE', "
                        + "expire_at = ?, cors_updated_at = ?, version = 4, status_sync_failure_count = 3 WHERE id = ?",
                localExpireAt, localUpdatedAt, id);
        ServiceAccount local = accountMapper.selectById(id);
        OffsetDateTime remoteUpdatedAt = localUpdatedAt.minusMinutes(1).atOffset(ZoneOffset.ofHours(8));
        CorsAccountSnapshot oldSnapshot = new CorsAccountSnapshot(
                "cors-stale", local.getAccount(), "ACTIVE", "ACTIVE",
                now.minusDays(1).atOffset(ZoneOffset.ofHours(8)),
                now.plusDays(5).atOffset(ZoneOffset.ofHours(8)),
                now.minusDays(30).atOffset(ZoneOffset.ofHours(8)),
                remoteUpdatedAt);

        assertEquals(CorsAccountStateApplyOutcome.STALE_IGNORED,
                applyService.apply(local, oldSnapshot, scheduleService.successSchedule()));
        ServiceAccount current = accountMapper.selectById(id);
        assertEquals("SUSPENDED", current.getCorsStatus());
        assertEquals(localExpireAt, current.getExpireAt());
        assertEquals(localUpdatedAt, current.getCorsUpdatedAt());
        assertEquals(5L, current.getVersion());
        assertNotNull(current.getLastSyncAt());
        assertEquals(current.getLastSyncAt(), current.getStatusSyncLastAttemptAt());
        assertTrue(current.getStatusSyncNextAt().isAfter(current.getLastSyncAt()));
        assertEquals(0, current.getStatusSyncFailureCount());
    }

    @Test
    void gatewayRunsOutsideCallerTransactionAndApplyUsesItsOwnShortTransaction() {
        long id = insertAccount("cors-boundary", "WAITING_ACTIVATION", null, null);
        LocalDateTime now = now();
        OffsetDateTime remoteUpdatedAt = now.minusMinutes(1).atOffset(ZoneOffset.ofHours(8));
        CorsAccountSnapshot snapshot = new CorsAccountSnapshot(
                "cors-boundary", accountMapper.selectById(id).getAccount(), "ACTIVE", "ACTIVE",
                now.minusMinutes(30).atOffset(ZoneOffset.ofHours(8)),
                now.plusDays(30).atOffset(ZoneOffset.ofHours(8)),
                now.minusDays(10).atOffset(ZoneOffset.ofHours(8)),
                remoteUpdatedAt);
        when(statusGateway.getAccount("cors-boundary")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return CorsAccountStatusResult.success(snapshot);
        });

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AccountStatusReconcileOutcome outcome = transaction.execute(
                status -> reconcileService.reconcileOne(id));

        assertEquals(AccountStatusReconcileOutcome.UPDATED, outcome);
        ServiceAccount current = accountMapper.selectById(id);
        assertEquals("ACTIVE", current.getCorsStatus());
        assertEquals("ACTIVE", current.getCorsActivationStatus());
        assertEquals(now.minusMinutes(30), current.getActivatedAt());
        assertEquals(now.plusDays(30), current.getExpireAt());
        assertEquals(now.minusDays(10), current.getCorsCreatedAt());
        assertNotNull(current.getStatusSyncNextAt());
        assertEquals(0, current.getStatusSyncFailureCount());
    }

    private List<String> indexColumns(String indexName) {
        return jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'service_account' "
                        + "AND INDEX_NAME = ? ORDER BY SEQ_IN_INDEX",
                String.class, indexName);
    }

    private long insertAccount(String corsAccountId, String activationStatus, LocalDateTime lastSyncAt) {
        return insertAccount(corsAccountId, activationStatus, lastSyncAt, null);
    }

    private long insertAccount(String corsAccountId, String activationStatus,
                               LocalDateTime lastSyncAt, LocalDateTime nextAt) {
        long sourceCodeId = nextSourceCodeId++;
        String account = "account-" + sourceCodeId;
        jdbc.update("INSERT INTO service_account "
                        + "(cors_account_id, account, owner_company_id, source_service_code_id, service_type, "
                        + "duration_value, duration_unit, account_silence_months, cors_activation_status, "
                        + "last_sync_at, status_sync_next_at, version) "
                        + "VALUES (?, ?, 1, ?, 'CORS', 1, 'MONTH', 0, ?, ?, ?, 0)",
                corsAccountId, account, sourceCodeId, activationStatus, lastSyncAt, nextAt);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}

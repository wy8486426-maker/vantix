package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileOutcome;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyOutcome;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.application.cors.CorsOperationRetryJob;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountCorsSnapshotUpdate;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "vantix.cors.account-status-sync.enabled=true",
        "vantix.cors-operation.enabled=false"
})
class MySqlAccountStatusSyncIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("vantix_account_status_sync")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ServiceAccountMapper accountMapper;
    @Autowired
    private CorsAccountStateApplyService applyService;
    @Autowired
    private AccountStatusReconcileService reconcileService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Clock clock;

    @MockBean
    private CorsAccountStatusGateway statusGateway;
    @MockBean
    private CorsAccountGateway corsAccountGateway;
    @MockBean
    private CorsOperationRetryJob corsOperationRetryJob;
    @MockBean
    private com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob reconcileJob;

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
    void mysql57CandidateQueryHonorsStalenessPriorityAndLimit() {
        LocalDateTime now = LocalDateTime.now(clock);
        long activeStale = insertAccount("cors-active-stale", "ACTIVE", now.minusMinutes(20));
        long waitingStale = insertAccount("cors-waiting-stale", "WAITING_ACTIVATION", now.minusMinutes(20));
        long waitingNeverSynced = insertAccount("cors-waiting-new", "WAITING_ACTIVATION", null);
        insertAccount("cors-active-recent", "ACTIVE", now.minusMinutes(5));
        insertAccount("cors-waiting-recent", "WAITING_ACTIVATION", now.minusMinutes(5));
        insertAccount(null, "WAITING_ACTIVATION", null);

        List<Long> limited = accountMapper.selectSyncCandidates(now.minusMinutes(10), 2);
        List<Long> all = accountMapper.selectSyncCandidates(now.minusMinutes(10), 10);

        assertEquals(List.of(waitingNeverSynced, waitingStale), limited);
        assertEquals(List.of(waitingNeverSynced, waitingStale, activeStale), all);
    }

    @Test
    void mysql57CasAllowsOnlyOneUpdateForAnExpectedVersion() {
        long id = insertAccount("cors-cas", "WAITING_ACTIVATION", null);
        LocalDateTime now = LocalDateTime.now(clock);
        ServiceAccountCorsSnapshotUpdate update = new ServiceAccountCorsSnapshotUpdate(
                id, 0L, "ACTIVE", "ACTIVE", now.minusMinutes(1), now.plusDays(10),
                now.minusDays(1), now, now, now);

        assertEquals(1, accountMapper.updateCorsSnapshot(update));
        assertEquals(0, accountMapper.updateCorsSnapshot(update));
        ServiceAccount current = accountMapper.selectById(id);
        assertEquals(1L, current.getVersion());
        assertEquals("ACTIVE", current.getCorsStatus());
        assertEquals("ACTIVE", current.getCorsActivationStatus());
        assertEquals(now.minusDays(1), current.getCorsCreatedAt());
        assertEquals(now, current.getCorsUpdatedAt());
    }

    @Test
    void olderRemoteSnapshotCannotOverwriteNewerProjection() {
        long id = insertAccount("cors-stale", "ACTIVE", null);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime localUpdatedAt = now.minusMinutes(1);
        LocalDateTime localExpireAt = now.plusDays(20);
        jdbc.update("UPDATE service_account SET cors_status = 'SUSPENDED', cors_activation_status = 'ACTIVE', "
                        + "expire_at = ?, cors_updated_at = ?, version = 4 WHERE id = ?",
                localExpireAt, localUpdatedAt, id);
        ServiceAccount local = accountMapper.selectById(id);
        OffsetDateTime remoteUpdatedAt = localUpdatedAt.minusMinutes(1).atOffset(ZoneOffset.ofHours(8));
        CorsAccountSnapshot oldSnapshot = new CorsAccountSnapshot(
                "cors-stale", local.getAccount(), "ACTIVE", "ACTIVE",
                now.minusDays(1).atOffset(ZoneOffset.ofHours(8)),
                now.plusDays(5).atOffset(ZoneOffset.ofHours(8)),
                now.minusDays(30).atOffset(ZoneOffset.ofHours(8)),
                remoteUpdatedAt);

        assertEquals(CorsAccountStateApplyOutcome.STALE_IGNORED, applyService.apply(local, oldSnapshot));
        ServiceAccount current = accountMapper.selectById(id);
        assertEquals("SUSPENDED", current.getCorsStatus());
        assertEquals(localExpireAt, current.getExpireAt());
        assertEquals(localUpdatedAt, current.getCorsUpdatedAt());
        assertEquals(4L, current.getVersion());
    }

    @Test
    void gatewayRunsOutsideCallerTransactionAndApplyUsesItsOwnShortTransaction() {
        long id = insertAccount("cors-boundary", "WAITING_ACTIVATION", null);
        LocalDateTime now = LocalDateTime.now(clock);
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
    }

    private long insertAccount(String corsAccountId, String activationStatus, LocalDateTime lastSyncAt) {
        long sourceCodeId = nextSourceCodeId++;
        String account = "account-" + sourceCodeId;
        jdbc.update("INSERT INTO service_account "
                        + "(cors_account_id, account, owner_company_id, source_service_code_id, service_type, "
                        + "duration_value, duration_unit, account_silence_months, cors_activation_status, "
                        + "last_sync_at, version) "
                        + "VALUES (?, ?, 1, ?, 'CORS', 1, 'MONTH', 0, ?, ?, 0)",
                corsAccountId, account, sourceCodeId, activationStatus, lastSyncAt);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}

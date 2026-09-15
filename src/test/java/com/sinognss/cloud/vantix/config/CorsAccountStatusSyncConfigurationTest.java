package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.application.cors.account.CorsMySqlAccountStatusSyncJob;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountQueryOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsMySqlAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsReadOnlyDatabaseClient;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoRepository;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorsAccountStatusSyncConfigurationTest {
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class, CorsAccountStatusSyncConfiguration.class,
                    CorsAccountStatusReconcileConfiguration.class)
            .withBean(DataSource.class, () -> new DriverManagerDataSource(
                    "jdbc:h2:mem:primary-context;DB_CLOSE_DELAY=-1", "sa", ""))
            .withBean(ServiceAccountMapper.class, () -> accountMapper);

    @Test
    void disabledByDefaultDoesNotRequireGatewayOrCreateReconcileBeans() {
        runner.run(context -> {
        assertTrue(context.isRunning());
        assertTrue(context.containsBean("corsAccountStateApplyService"));
        assertEquals(1, context.getBeansOfType(DataSource.class).size());
        assertEquals(1, context.getBeansOfType(CorsAccountStateApplyService.class).size());
        assertEquals(1, context.getBeansOfType(AccountStatusSyncScheduleService.class).size());
        assertFalse(context.containsBean("accountStatusReconcileService"));
        assertFalse(context.containsBean("accountStatusReconcileJob"));
        assertFalse(context.containsBean("corsReadOnlyDatabaseClient"));
        assertFalse(context.containsBean("corsMySqlAccountStatusGateway"));
        assertFalse(context.containsBean("corsMySqlAccountStatusSyncJob"));
        });
    }

    @Test
    void enabledCreatesReconcileBeansWhenGatewayIsProvided() {
        runner.withPropertyValues("vantix.cors.account-status-sync.enabled=true")
                .withBean(CorsAccountStatusGateway.class, () -> mock(CorsAccountStatusGateway.class))
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBean(AccountStatusReconcileService.class) != null);
                    assertTrue(context.getBean(AccountStatusReconcileJob.class) != null);
                    assertTrue(context.getBean(CorsAccountStateApplyService.class) != null);
                    assertTrue(context.getBean(AccountStatusSyncScheduleService.class) != null);
                });
    }


    @Test
    void reconcileCoreIsAvailableWithGatewayWhenPeriodicSyncIsDisabled() {
        runner.withBean(CorsAccountStatusGateway.class, () -> mock(CorsAccountStatusGateway.class))
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBean(AccountStatusReconcileService.class) != null);
                    assertTrue(context.getBean(AccountStatusSyncScheduleService.class) != null);
                    assertFalse(context.containsBean("accountStatusReconcileJob"));
                });
    }

    @Test
    void forceActivationConfigurationIsIgnoredAndNoForceActivationBeansExist() {
        runner.withPropertyValues("vantix.cors.force-activation.enabled=true")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertFalse(context.containsBean("accountForceActivationJob"));
                    assertFalse(context.containsBean("accountForceActivationRetryJob"));
                });
    }

    @Test
    void invalidBatchSizeFailsConfigurationBinding() {
        runner.withPropertyValues("vantix.cors.account-status-sync.batch-size=501")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void retryMaximumCannotBeSmallerThanRetryBase() {
        runner.withPropertyValues(
                        "vantix.cors.account-status-sync.retry-base-delay=2m",
                        "vantix.cors.account-status-sync.retry-max-delay=1m")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void corsDatabaseEnabledCreatesOneGatewayAndOneReconcileCoreWithoutSyncJob() {
        runner.withUserConfiguration(CorsReadOnlyDatabaseConfiguration.class)
                .withPropertyValues(
                        "vantix.cors-db.enabled=true",
                        "vantix.cors-db.jdbc-url=jdbc:h2:mem:cors-context-b;DB_CLOSE_DELAY=-1",
                        "vantix.cors-db.username=sa",
                        "vantix.cors-db.password=",
                        "vantix.cors-db.driver-class-name=org.h2.Driver",
                        "vantix.cors-db.status-sync.enabled=false")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertEquals(1, context.getBeansOfType(CorsReadOnlyDatabaseClient.class).size());
                    assertEquals(1, context.getBeansOfType(CorsUserInfoRepository.class).size());
                    assertEquals(1, context.getBeansOfType(CorsAccountStatusGateway.class).size());
                    assertEquals(1, context.getBeansOfType(CorsMySqlAccountStatusGateway.class).size());
                    assertEquals(1, context.getBeansOfType(AccountStatusSyncScheduleService.class).size());
                    assertEquals(1, context.getBeansOfType(AccountStatusReconcileService.class).size());
                    assertEquals(0, context.getBeansOfType(CorsMySqlAccountStatusSyncJob.class).size());
                });
    }

    @Test
    void corsDatabaseAndStatusSyncEnabledCreateExactlyOneOfEachCoreBean() {
        runner.withUserConfiguration(CorsReadOnlyDatabaseConfiguration.class)
                .withPropertyValues(
                        "vantix.cors-db.enabled=true",
                        "vantix.cors-db.jdbc-url=jdbc:h2:mem:cors-context-c;DB_CLOSE_DELAY=-1",
                        "vantix.cors-db.username=sa",
                        "vantix.cors-db.password=",
                        "vantix.cors-db.driver-class-name=org.h2.Driver",
                        "vantix.cors-db.status-sync.enabled=true")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertEquals(1, context.getBeansOfType(AccountStatusSyncScheduleService.class).size());
                    assertEquals(1, context.getBeansOfType(AccountStatusReconcileService.class).size());
                    assertEquals(1, context.getBeansOfType(CorsMySqlAccountStatusSyncJob.class).size());
                });
    }

    @Test
    void corsDatabaseAndAccountStatusSyncEnabledCreateReconcileJobWithoutDuplicates() {
        runner.withUserConfiguration(CorsReadOnlyDatabaseConfiguration.class)
                .withPropertyValues(
                        "vantix.cors-db.enabled=true",
                        "vantix.cors-db.jdbc-url=jdbc:h2:mem:cors-context-d;DB_CLOSE_DELAY=-1",
                        "vantix.cors-db.username=sa",
                        "vantix.cors-db.password=",
                        "vantix.cors-db.driver-class-name=org.h2.Driver",
                        "vantix.cors-db.status-sync.enabled=false",
                        "vantix.cors.account-status-sync.enabled=true")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertEquals(1, context.getBeansOfType(AccountStatusReconcileJob.class).size());
                    assertEquals(1, context.getBeansOfType(AccountStatusReconcileService.class).size());
                    assertEquals(1, context.getBeansOfType(AccountStatusSyncScheduleService.class).size());
                });
    }

    @Test
    void unreachableCorsDatabaseDoesNotPreventContextStartupOrLeakSqlException() {
        runner.withUserConfiguration(CorsReadOnlyDatabaseConfiguration.class)
                .withPropertyValues(
                        "vantix.cors-db.enabled=true",
                        "vantix.cors-db.jdbc-url=jdbc:mysql://127.0.0.1:1/nonexistent?connectTimeout=100&socketTimeout=100",
                        "vantix.cors-db.username=root",
                        "vantix.cors-db.password=invalid",
                        "vantix.cors-db.status-sync.enabled=true")
                .run(context -> {
                    assertTrue(context.isRunning());
                    CorsAccountStatusResult result = context.getBean(CorsAccountStatusGateway.class).getAccount("1");
                    assertEquals(CorsAccountQueryOutcome.UNKNOWN, result.outcome());
                    assertEquals("CORS_DB_UNAVAILABLE", result.errorCode());

                    ServiceAccount candidate = new ServiceAccount();
                    candidate.setId(1L);
                    candidate.setCorsAccountId("1");
                    candidate.setAccount("cors-account-1");
                    candidate.setVersion(0L);
                    when(accountMapper.selectCorsStatusSyncCandidatesAfterId(0L, 500))
                            .thenReturn(java.util.List.of(candidate));
                    when(accountMapper.selectCorsStatusSyncCandidatesAfterId(1L, 500))
                            .thenReturn(java.util.List.of());
                    context.getBean(CorsMySqlAccountStatusSyncJob.class).sync();
                    verify(accountMapper, never()).updateCorsSnapshot(any());
                    verify(accountMapper, never()).updateStatusSyncSuccess(any());
                    verify(accountMapper, never()).updateStatusSyncFailure(any());
                });
    }

    @Test
    void invalidCorsPoolOrBatchConfigurationFailsFast() {
        runner.withUserConfiguration(CorsReadOnlyDatabaseConfiguration.class)
                .withPropertyValues(
                        "vantix.cors-db.enabled=true",
                        "vantix.cors-db.jdbc-url=jdbc:h2:mem:cors-invalid",
                        "vantix.cors-db.username=sa",
                        "vantix.cors-db.driver-class-name=org.h2.Driver",
                        "vantix.cors-db.maximum-pool-size=0")
                .run(context -> assertNotNull(context.getStartupFailure()));

        runner.withUserConfiguration(CorsReadOnlyDatabaseConfiguration.class)
                .withPropertyValues(
                        "vantix.cors-db.enabled=true",
                        "vantix.cors-db.jdbc-url=jdbc:h2:mem:cors-invalid-batch",
                        "vantix.cors-db.username=sa",
                        "vantix.cors-db.driver-class-name=org.h2.Driver",
                        "vantix.cors-db.status-sync.batch-size=1001")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}

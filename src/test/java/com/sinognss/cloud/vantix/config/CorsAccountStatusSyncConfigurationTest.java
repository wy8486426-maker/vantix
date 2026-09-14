package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationRetryJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationGateway;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CorsAccountStatusSyncConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class, CorsAccountStatusSyncConfiguration.class,
                    CorsForceActivationConfiguration.class)
            .withBean(ServiceAccountMapper.class, () -> mock(ServiceAccountMapper.class));

    @Test
    void disabledByDefaultDoesNotRequireGatewayOrCreateReconcileBeans() {
        runner.run(context -> {
            assertTrue(context.isRunning());
            assertFalse(context.getBean(CorsForceActivationProperties.class).isEnabled());
            assertTrue(context.containsBean("corsAccountStateApplyService"));
            assertFalse(context.containsBean("accountStatusReconcileService"));
            assertFalse(context.containsBean("accountStatusReconcileJob"));
            assertFalse(context.containsBean("accountStatusSyncScheduleService"));
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
    void enabledForceActivationRequiresAndUsesBothCapabilityPorts() {
        runner.withPropertyValues("vantix.cors.force-activation.enabled=true")
                .withBean(CorsAccountStatusGateway.class, () -> mock(CorsAccountStatusGateway.class))
                .withBean(CorsForceActivationGateway.class, () -> mock(CorsForceActivationGateway.class))
                .withBean(CorsOperationMapper.class, () -> mock(CorsOperationMapper.class))
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBean(AccountForceActivationJob.class) != null);
                    assertTrue(context.getBean(AccountForceActivationRetryJob.class) != null);
                    assertFalse(context.containsBean("accountStatusReconcileJob"));
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
}

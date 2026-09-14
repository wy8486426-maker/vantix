package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CorsAccountStatusSyncConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class, CorsAccountStatusSyncConfiguration.class)
            .withBean(ServiceAccountMapper.class, () -> mock(ServiceAccountMapper.class));

    @Test
    void disabledByDefaultDoesNotRequireGatewayOrCreateReconcileBeans() {
        runner.run(context -> {
            assertTrue(context.isRunning());
            assertTrue(context.containsBean("corsAccountStateApplyService"));
            assertFalse(context.containsBean("accountStatusReconcileService"));
            assertFalse(context.containsBean("accountStatusReconcileJob"));
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
                });
    }

    @Test
    void invalidBatchSizeFailsConfigurationBinding() {
        runner.withPropertyValues("vantix.cors.account-status-sync.batch-size=501")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}

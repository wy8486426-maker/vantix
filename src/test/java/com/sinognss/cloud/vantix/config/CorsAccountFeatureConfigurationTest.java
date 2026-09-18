package com.sinognss.cloud.vantix.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.application.password.AccountPasswordOperationService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetQueryService;
import com.sinognss.cloud.vantix.application.password.reveal.AccountPasswordRevealService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalReserveService;
import com.sinognss.cloud.vantix.controller.AccountPasswordResetController;
import com.sinognss.cloud.vantix.controller.AccountPasswordRevealController;
import com.sinognss.cloud.vantix.controller.AccountRenewalController;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordGateway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsAccountFeatureConfigurationTest {
    @Test
    void renewalEnabledWithGatewaysCreatesReserveServiceAndController() {
        runnerWithDependencies()
                .withPropertyValues("vantix.cors.renewal.enabled=true")
                .withBean(CorsAccountStatusGateway.class, () -> Mockito.mock(CorsAccountStatusGateway.class))
                .withBean(CorsAccountRenewalGateway.class, () -> Mockito.mock(CorsAccountRenewalGateway.class))
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertNotNull(context.getBean(AccountRenewalReserveService.class));
                    assertNotNull(context.getBean(AccountRenewalController.class));
                });
    }

    @Test
    void renewalDisabledDoesNotCreateReserveServiceOrController() {
        runnerWithDependencies()
                .withPropertyValues("vantix.cors.renewal.enabled=false")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBeansOfType(AccountRenewalReserveService.class).isEmpty());
                    assertTrue(context.getBeansOfType(AccountRenewalController.class).isEmpty());
                });
    }

    @Test
    void passwordEnabledWithGatewaysCreatesServicesAndControllers() {
        runnerWithDependencies()
                .withPropertyValues("vantix.cors.password.enabled=true")
                .withBean(CorsPasswordGateway.class, () -> Mockito.mock(CorsPasswordGateway.class))
                .withBean(CorsAccountPasswordGateway.class,
                        () -> Mockito.mock(CorsAccountPasswordGateway.class))
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertNotNull(context.getBean(AccountPasswordOperationService.class));
                    assertNotNull(context.getBean(AccountPasswordResetQueryService.class));
                    assertNotNull(context.getBean(AccountPasswordResetController.class));
                    assertNotNull(context.getBean(AccountPasswordRevealService.class));
                    assertNotNull(context.getBean(AccountPasswordRevealController.class));
                });
    }

    @Test
    void enabledRenewalWithoutRequiredGatewayFailsContextInsteadOfHalfAssembling() {
        runnerWithDependencies()
                .withPropertyValues("vantix.cors.renewal.enabled=true")
                .withBean(CorsAccountStatusGateway.class, () -> Mockito.mock(CorsAccountStatusGateway.class))
                .run(context -> assertMissingGatewayFailure(context, CorsAccountRenewalGateway.class));
    }

    @Test
    void enabledPasswordWithoutResetGatewayFailsContextInsteadOfHalfAssembling() {
        runnerWithDependencies()
                .withPropertyValues("vantix.cors.password.enabled=true")
                .withBean(CorsAccountPasswordGateway.class,
                        () -> Mockito.mock(CorsAccountPasswordGateway.class))
                .run(context -> assertMissingGatewayFailure(context, CorsPasswordGateway.class));
    }

    @Test
    void enabledPasswordWithoutRevealGatewayFailsContextInsteadOfHalfAssembling() {
        runnerWithDependencies()
                .withPropertyValues("vantix.cors.password.enabled=true")
                .withBean(CorsPasswordGateway.class, () -> Mockito.mock(CorsPasswordGateway.class))
                .run(context -> assertMissingGatewayFailure(context, CorsAccountPasswordGateway.class));
    }

    private static ApplicationContextRunner runnerWithDependencies() {
        return new ApplicationContextRunner()
                .withUserConfiguration(FeatureConfiguration.class)
                .withBean(AccountRenewalMapper.class, () -> Mockito.mock(AccountRenewalMapper.class))
                .withBean(AccountPasswordActionMapper.class,
                        () -> Mockito.mock(AccountPasswordActionMapper.class))
                .withBean(CorsOperationMapper.class, () -> Mockito.mock(CorsOperationMapper.class))
                .withBean(ServiceAccountMapper.class, () -> Mockito.mock(ServiceAccountMapper.class))
                .withBean(ServiceCodeMapper.class, () -> Mockito.mock(ServiceCodeMapper.class))
                .withBean(UserHolderBridge.class, UserHolderBridge::new)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(CorsAccountRenewalProperties.class, CorsAccountRenewalProperties::new)
                .withBean(CorsAccountStateApplyService.class,
                        () -> Mockito.mock(CorsAccountStateApplyService.class))
                .withBean(AccountStatusSyncScheduleService.class,
                        () -> Mockito.mock(AccountStatusSyncScheduleService.class));
    }

    private static void assertMissingGatewayFailure(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context,
            Class<?> gatewayType) {
        Throwable failure = context.getStartupFailure();
        assertNotNull(failure);
        assertTrue(hasMessage(failure, gatewayType.getName()),
                () -> "Startup failure did not mention " + gatewayType.getName() + ": " + failure);
    }

    private static boolean hasMessage(Throwable failure, String expected) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.toString().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({
            CorsAccountRenewalConfiguration.class,
            AccountRenewalController.class,
            CorsAccountPasswordResetConfiguration.class,
            AccountPasswordResetController.class,
            CorsAccountPasswordRevealConfiguration.class,
            AccountPasswordRevealController.class
    })
    static class FeatureConfiguration {
    }
}

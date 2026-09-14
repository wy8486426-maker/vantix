package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetClaimService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetFinalizeService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetProcessor;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetQueryService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetReserveService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetReserveTransaction;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetRetryJob;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetStateService;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "vantix.cors.password", name = "enabled", havingValue = "true")
@ConditionalOnBean({CorsAccountPasswordGateway.class, CorsAccountStatusGateway.class})
public class CorsAccountPasswordResetConfiguration {
    @Bean
    AccountPasswordResetReserveTransaction accountPasswordResetReserveTransaction(
            AccountPasswordActionMapper actionMapper, CorsOperationMapper operationMapper,
            ServiceAccountMapper serviceAccountMapper, Clock clock) {
        return new AccountPasswordResetReserveTransaction(actionMapper, operationMapper, serviceAccountMapper, clock);
    }

    @Bean
    AccountPasswordResetReserveService accountPasswordResetReserveService(
            AccountPasswordResetReserveTransaction transaction, AccountPasswordActionMapper actionMapper,
            CorsOperationMapper operationMapper, UserHolderBridge userHolder) {
        return new AccountPasswordResetReserveService(transaction, actionMapper, operationMapper, userHolder);
    }

    @Bean
    AccountPasswordResetQueryService accountPasswordResetQueryService(
            AccountPasswordActionMapper actionMapper, UserHolderBridge userHolder) {
        return new AccountPasswordResetQueryService(actionMapper, userHolder);
    }

    @Bean
    AccountPasswordResetStateService accountPasswordResetStateService(
            CorsOperationMapper operationMapper, AccountPasswordActionMapper actionMapper,
            CorsAccountPasswordProperties properties, Clock clock) {
        return new AccountPasswordResetStateService(operationMapper, actionMapper, properties, clock);
    }

    @Bean
    AccountPasswordResetClaimService accountPasswordResetClaimService(
            CorsOperationMapper operationMapper, AccountPasswordActionMapper actionMapper,
            AccountPasswordResetStateService stateService, CorsAccountPasswordProperties properties, Clock clock) {
        return new AccountPasswordResetClaimService(operationMapper, actionMapper, stateService, properties, clock);
    }

    @Bean
    AccountPasswordResetFinalizeService accountPasswordResetFinalizeService(
            CorsOperationMapper operationMapper, AccountPasswordActionMapper actionMapper,
            ServiceAccountMapper serviceAccountMapper, Clock clock) {
        return new AccountPasswordResetFinalizeService(operationMapper, actionMapper, serviceAccountMapper, clock);
    }

    @Bean
    AccountPasswordResetProcessor accountPasswordResetProcessor(
            AccountPasswordResetClaimService claimService, AccountPasswordResetStateService stateService,
            AccountPasswordResetFinalizeService finalizeService, CorsAccountStatusGateway statusGateway,
            CorsAccountPasswordGateway passwordGateway) {
        return new AccountPasswordResetProcessor(claimService, stateService, finalizeService,
                statusGateway, passwordGateway);
    }

    @Bean
    AccountPasswordResetRetryJob accountPasswordResetRetryJob(
            AccountPasswordResetClaimService claimService, AccountPasswordResetProcessor processor,
            CorsAccountPasswordProperties properties) {
        return new AccountPasswordResetRetryJob(claimService, processor, properties);
    }
}

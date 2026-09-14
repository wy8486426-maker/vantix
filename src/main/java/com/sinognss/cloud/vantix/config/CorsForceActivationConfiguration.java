package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationClaimService;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationFinalizeService;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationProcessor;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationReserveService;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationRetryJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountForceActivationStateService;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "vantix.cors.force-activation", name = "enabled", havingValue = "true")
public class CorsForceActivationConfiguration {
    @Bean
    AccountForceActivationReserveService accountForceActivationReserveService(
            ServiceAccountMapper accountMapper, CorsOperationMapper operationMapper, Clock clock) {
        return new AccountForceActivationReserveService(accountMapper, operationMapper, clock);
    }

    @Bean
    AccountForceActivationClaimService accountForceActivationClaimService(
            CorsOperationMapper operationMapper, CorsForceActivationProperties properties, Clock clock) {
        return new AccountForceActivationClaimService(operationMapper, properties, clock);
    }

    @Bean
    AccountForceActivationStateService accountForceActivationStateService(
            CorsOperationMapper operationMapper, CorsForceActivationProperties properties, Clock clock) {
        return new AccountForceActivationStateService(operationMapper, properties, clock);
    }

    @Bean
    AccountForceActivationFinalizeService accountForceActivationFinalizeService(
            CorsOperationMapper operationMapper, ServiceAccountMapper accountMapper,
            CorsAccountStateApplyService applyService, AccountStatusSyncScheduleService scheduleService,
            Clock clock) {
        return new AccountForceActivationFinalizeService(
                operationMapper, accountMapper, applyService, scheduleService, clock);
    }

    @Bean
    AccountForceActivationProcessor accountForceActivationProcessor(
            AccountForceActivationClaimService claimService,
            AccountForceActivationStateService stateService,
            AccountForceActivationFinalizeService finalizeService,
            ServiceAccountMapper accountMapper,
            CorsForceActivationGateway gateway) {
        return new AccountForceActivationProcessor(
                claimService, stateService, finalizeService, accountMapper, gateway);
    }

    @Bean
    AccountForceActivationJob accountForceActivationJob(
            ServiceAccountMapper accountMapper, AccountStatusReconcileService reconcileService,
            AccountForceActivationReserveService reserveService,
            CorsForceActivationProperties properties, Clock clock) {
        return new AccountForceActivationJob(
                accountMapper, reconcileService, reserveService, properties, clock);
    }

    @Bean
    AccountForceActivationRetryJob accountForceActivationRetryJob(
            AccountForceActivationClaimService claimService,
            AccountForceActivationProcessor processor, CorsForceActivationProperties properties) {
        return new AccountForceActivationRetryJob(claimService, processor, properties);
    }
}

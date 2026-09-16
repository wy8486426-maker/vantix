package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalClaimService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalFinalizeService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalProcessor;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalQueryService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalReserveService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalReserveTransaction;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalRetryJob;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalStateService;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "vantix.cors.renewal", name = "enabled", havingValue = "true")
@ConditionalOnBean({CorsAccountStatusGateway.class, CorsAccountRenewalGateway.class})
public class CorsAccountRenewalConfiguration {
    @Bean
    AccountRenewalReserveTransaction accountRenewalReserveTransaction(
            AccountRenewalMapper renewalMapper, CorsOperationMapper operationMapper,
            ServiceAccountMapper accountMapper, ServiceCodeMapper serviceCodeMapper,
            ObjectMapper objectMapper, Clock clock) {
        return new AccountRenewalReserveTransaction(renewalMapper, operationMapper, accountMapper,
                serviceCodeMapper, objectMapper, clock);
    }

    @Bean
    AccountRenewalReserveService accountRenewalReserveService(
            AccountRenewalReserveTransaction transaction, AccountRenewalMapper renewalMapper,
            CorsOperationMapper operationMapper, UserHolderBridge userHolder) {
        return new AccountRenewalReserveService(transaction, renewalMapper, operationMapper, userHolder);
    }

    @Bean
    AccountRenewalQueryService accountRenewalQueryService(
            AccountRenewalMapper renewalMapper, AccountRenewalLogQueryMapper logQueryMapper,
            UserHolderBridge userHolder) {
        return new AccountRenewalQueryService(renewalMapper, logQueryMapper, userHolder);
    }

    @Bean
    AccountRenewalClaimService accountRenewalClaimService(
            CorsOperationMapper operationMapper, AccountRenewalMapper renewalMapper,
            AccountRenewalStateService stateService, CorsAccountRenewalProperties properties, Clock clock) {
        return new AccountRenewalClaimService(operationMapper, renewalMapper, stateService, properties, clock);
    }

    @Bean
    AccountRenewalStateService accountRenewalStateService(
            CorsOperationMapper operationMapper, AccountRenewalMapper renewalMapper,
            ServiceCodeMapper serviceCodeMapper, CorsAccountRenewalProperties properties, Clock clock) {
        return new AccountRenewalStateService(operationMapper, renewalMapper, serviceCodeMapper, properties, clock);
    }

    @Bean
    AccountRenewalFinalizeService accountRenewalFinalizeService(
            CorsOperationMapper operationMapper, AccountRenewalMapper renewalMapper,
            ServiceAccountMapper accountMapper, ServiceCodeMapper serviceCodeMapper,
            CorsAccountStateApplyService applyService, AccountStatusSyncScheduleService scheduleService,
            Clock clock) {
        return new AccountRenewalFinalizeService(operationMapper, renewalMapper, accountMapper, serviceCodeMapper,
                applyService, scheduleService, clock);
    }

    @Bean
    AccountRenewalProcessor accountRenewalProcessor(
            AccountRenewalClaimService claimService, AccountRenewalStateService stateService,
            AccountRenewalFinalizeService finalizeService, ServiceAccountMapper accountMapper,
            CorsAccountStatusGateway statusGateway, CorsAccountRenewalGateway renewalGateway) {
        return new AccountRenewalProcessor(claimService, stateService, finalizeService,
                accountMapper, statusGateway, renewalGateway);
    }

    @Bean
    AccountRenewalRetryJob accountRenewalRetryJob(
            AccountRenewalClaimService claimService, AccountRenewalProcessor processor,
            CorsAccountRenewalProperties properties) {
        return new AccountRenewalRetryJob(claimService, processor, properties);
    }
}

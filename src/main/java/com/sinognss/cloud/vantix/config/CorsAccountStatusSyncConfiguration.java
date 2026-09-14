package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CorsAccountStatusSyncConfiguration {
    @Bean
    CorsAccountStateApplyService corsAccountStateApplyService(ServiceAccountMapper accountMapper, Clock clock) {
        return new CorsAccountStateApplyService(accountMapper, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "vantix.cors.account-status-sync", name = "enabled",
            havingValue = "true")
    AccountStatusReconcileService accountStatusReconcileService(ServiceAccountMapper accountMapper,
                                                                CorsAccountStatusGateway gateway,
                                                                CorsAccountStateApplyService applyService) {
        return new AccountStatusReconcileService(accountMapper, gateway, applyService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "vantix.cors.account-status-sync", name = "enabled",
            havingValue = "true")
    AccountStatusReconcileJob accountStatusReconcileJob(ServiceAccountMapper accountMapper,
                                                        AccountStatusReconcileService reconcileService,
                                                        CorsAccountStatusSyncProperties properties,
                                                        Clock clock) {
        return new AccountStatusReconcileJob(accountMapper, reconcileService, properties, clock);
    }
}

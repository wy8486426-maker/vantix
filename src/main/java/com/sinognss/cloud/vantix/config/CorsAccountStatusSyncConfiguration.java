package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CorsAccountStatusSyncConfiguration {
    @Bean
    CorsAccountStateApplyService corsAccountStateApplyService(ServiceAccountMapper accountMapper) {
        return new CorsAccountStateApplyService(accountMapper);
    }

    @Bean
    AccountStatusSyncScheduleService accountStatusSyncScheduleService(
            ServiceAccountMapper accountMapper, CorsAccountStatusSyncProperties properties, Clock clock) {
        return new AccountStatusSyncScheduleService(accountMapper, properties, clock);
    }
}

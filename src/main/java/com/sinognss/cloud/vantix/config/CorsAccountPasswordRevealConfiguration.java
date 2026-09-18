package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.password.reveal.AccountPasswordRevealAuditService;
import com.sinognss.cloud.vantix.application.password.reveal.AccountPasswordRevealService;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsAccountPasswordProperties.class)
@ConditionalOnProperty(prefix = "vantix.cors.password", name = "enabled", havingValue = "true")
public class CorsAccountPasswordRevealConfiguration {
    @Bean
    AccountPasswordRevealAuditService accountPasswordRevealAuditService(
            AccountPasswordActionMapper actionMapper, ServiceAccountMapper accountMapper,
            UserHolderBridge userHolder, Clock clock) {
        return new AccountPasswordRevealAuditService(actionMapper, accountMapper, userHolder, clock);
    }

    @Bean
    AccountPasswordRevealService accountPasswordRevealService(
            AccountPasswordRevealAuditService auditService, CorsAccountPasswordGateway gateway) {
        return new AccountPasswordRevealService(auditService, gateway);
    }
}

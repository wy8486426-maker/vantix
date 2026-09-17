package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.password.AccountPasswordAuditService;
import com.sinognss.cloud.vantix.application.password.AccountPasswordOperationService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetQueryService;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsAccountPasswordProperties.class)
@ConditionalOnProperty(prefix = "vantix.cors.password", name = "enabled", havingValue = "true")
@ConditionalOnBean(CorsPasswordGateway.class)
public class CorsAccountPasswordResetConfiguration {
    @Bean
    AccountPasswordAuditService accountPasswordAuditService(
            AccountPasswordActionMapper actionMapper, ServiceAccountMapper serviceAccountMapper,
            UserHolderBridge userHolder, Clock clock) {
        return new AccountPasswordAuditService(actionMapper, serviceAccountMapper, userHolder, clock);
    }

    @Bean
    AccountPasswordOperationService accountPasswordOperationService(
            AccountPasswordAuditService auditService, CorsPasswordGateway passwordGateway) {
        return new AccountPasswordOperationService(auditService, passwordGateway);
    }

    @Bean
    AccountPasswordResetQueryService accountPasswordResetQueryService(
            AccountPasswordActionMapper actionMapper, UserHolderBridge userHolder) {
        return new AccountPasswordResetQueryService(actionMapper, userHolder);
    }
}

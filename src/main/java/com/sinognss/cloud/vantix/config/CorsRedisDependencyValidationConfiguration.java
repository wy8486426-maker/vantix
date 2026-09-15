package com.sinognss.cloud.vantix.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "vantix.cors-redis", name = "enabled", havingValue = "true")
public class CorsRedisDependencyValidationConfiguration {
    @Bean
    CorsRedisDependencyGuard corsRedisDependencyGuard(CorsRedisProperties redis, CorsDbProperties db) {
        if (!db.isEnabled()) {
            throw new IllegalStateException("CORS Redis realtime sync requires CORS DB");
        }
        redis.validate();
        return new CorsRedisDependencyGuard();
    }

    static final class CorsRedisDependencyGuard { }
}

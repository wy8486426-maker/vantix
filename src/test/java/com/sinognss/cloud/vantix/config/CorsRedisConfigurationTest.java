package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.cors.redis.CorsRedisMessageListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CorsRedisConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class, CorsAccountStatusSyncConfiguration.class,
                    CorsRedisConfiguration.class, CorsRedisDependencyValidationConfiguration.class)
            .withBean(ServiceAccountMapper.class, () -> mock(ServiceAccountMapper.class));

    @Test
    void disabledDoesNotCreateRedisBeans() {
        runner.run(context -> {
            assertTrue(context.isRunning());
            assertEquals(0, context.getBeansOfType(CorsRedisMessageListener.class).size());
        });
    }

    @Test
    void enabledWithoutCorsDbFailsWithClearConfigurationError() {
        runner.withPropertyValues("vantix.cors-redis.enabled=true")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(context.getStartupFailure().toString()
                            .contains("CORS Redis realtime sync requires CORS DB"));
                });
    }
}

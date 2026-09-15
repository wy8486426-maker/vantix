package com.sinognss.cloud.vantix.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.cors.redis.CorsRedisMessageListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CorsRedisConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class, CorsAccountStatusSyncConfiguration.class,
                    CorsRedisConfiguration.class, CorsRedisDependencyValidationConfiguration.class,
                    CorsReadOnlyDatabaseConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
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

    @Test
    void unreachableRedisDoesNotPreventContextStartupWhenCorsDbIsEnabled() {
        runner.withPropertyValues(
                        "vantix.cors-db.enabled=true",
                        "vantix.cors-db.jdbc-url=jdbc:h2:mem:cors-redis-unavailable;DB_CLOSE_DELAY=-1",
                        "vantix.cors-db.username=sa",
                        "vantix.cors-db.password=",
                        "vantix.cors-db.driver-class-name=org.h2.Driver",
                        "vantix.cors-redis.enabled=true",
                        "vantix.cors-redis.host=127.0.0.1",
                        "vantix.cors-redis.port=1",
                        "vantix.cors-redis.connect-timeout=10ms")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertNotNull(context.getBean(CorsRedisMessageListener.class));
                });
    }
}

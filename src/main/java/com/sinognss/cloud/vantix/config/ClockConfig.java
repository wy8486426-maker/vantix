package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties({VantixProperties.class, OfflineImportProperties.class,
        GenerationProperties.class, CorsProperties.class, CorsOperationProperties.class,
        CorsAccountStatusSyncProperties.class, CorsForceActivationProperties.class})
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}

package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountRealtimeRefreshService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.infrastructure.cors.redis.CorsRedisMessageListener;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoRepository;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoSnapshotMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "vantix.cors-redis", name = "enabled", havingValue = "true")
@Conditional(CorsDbEnabledCondition.class)
public class CorsRedisConfiguration {
    @Bean(destroyMethod = "destroy")
    LettuceConnectionFactory corsRedisConnectionFactory(CorsRedisProperties properties) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                properties.getHost(), properties.getPort());
        server.setDatabase(properties.getDatabase());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            server.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            server.setPassword(properties.getPassword());
        }
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(properties.getConnectTimeout()).build();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder().commandTimeout(properties.getConnectTimeout())
                        .clientOptions(ClientOptions.builder().socketOptions(socketOptions).build());
        if (properties.isSsl()) builder.useSsl();
        return new LettuceConnectionFactory(server, builder.build());
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor corsRedisRefreshWorkers(CorsRedisProperties properties) {
        return new ThreadPoolExecutor(properties.getWorkerCoreSize(), properties.getWorkerMaxSize(),
                60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    CorsAccountRealtimeRefreshService corsAccountRealtimeRefreshService(
            ServiceAccountMapper accountMapper, CorsUserInfoRepository repository,
            CorsUserInfoSnapshotMapper snapshotMapper, CorsAccountStateApplyService applyService,
            AccountStatusSyncScheduleService scheduleService) {
        return new CorsAccountRealtimeRefreshService(accountMapper, repository, snapshotMapper,
                applyService, scheduleService);
    }

    @Bean
    CorsRedisMessageListener corsRedisMessageListener(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            CorsAccountRealtimeRefreshService refreshService,
            ThreadPoolExecutor workers) {
        return new CorsRedisMessageListener(objectMapper, refreshService, workers);
    }

    @Bean(destroyMethod = "stop")
    RedisMessageListenerContainer corsRedisMessageListenerContainer(
            LettuceConnectionFactory connectionFactory, CorsRedisProperties properties,
            CorsRedisMessageListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(properties.getRecoveryInterval().toMillis());
        container.addMessageListener(listener, new ChannelTopic(properties.getChannel()));
        return container;
    }

}

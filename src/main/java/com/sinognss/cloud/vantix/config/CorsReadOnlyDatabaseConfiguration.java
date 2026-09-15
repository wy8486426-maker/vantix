package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.integration.cors.account.CorsMySqlAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsReadOnlyDatabaseClient;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoRepository;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoSnapshotMapper;
import com.sinognss.cloud.vantix.application.cors.account.CorsMySqlAccountStatusSyncJob;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsDbProperties.class)
@ConditionalOnProperty(prefix = "vantix.cors-db", name = "enabled", havingValue = "true")
public class CorsReadOnlyDatabaseConfiguration {
    @Bean(destroyMethod = "close")
    CorsReadOnlyDatabaseClient corsReadOnlyDatabaseClient(CorsDbProperties properties) {
        return new CorsReadOnlyDatabaseClient(properties.getJdbcUrl(), properties.getUsername(),
                properties.getPassword(), properties.getDriverClassName(), properties.getMaximumPoolSize(),
                properties.getMinimumIdle());
    }

    @Bean
    CorsUserInfoRepository corsUserInfoRepository(CorsReadOnlyDatabaseClient client) {
        return new CorsUserInfoRepository(client);
    }

    @Bean
    CorsUserInfoSnapshotMapper corsUserInfoSnapshotMapper() {
        return new CorsUserInfoSnapshotMapper();
    }

    @Bean
    CorsMySqlAccountStatusGateway corsMySqlAccountStatusGateway(CorsUserInfoRepository repository,
                                                                CorsUserInfoSnapshotMapper mapper) {
        return new CorsMySqlAccountStatusGateway(repository, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(AccountStatusSyncScheduleService.class)
    AccountStatusSyncScheduleService corsDbAccountStatusSyncScheduleService(
            ServiceAccountMapper accountMapper, CorsAccountStatusSyncProperties properties, Clock clock) {
        return new AccountStatusSyncScheduleService(accountMapper, properties, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "vantix.cors-db.status-sync", name = "enabled", havingValue = "true")
    CorsMySqlAccountStatusSyncJob corsMySqlAccountStatusSyncJob(
            ServiceAccountMapper accountMapper, CorsUserInfoRepository repository,
            CorsUserInfoSnapshotMapper snapshotMapper, CorsAccountStateApplyService applyService,
            AccountStatusSyncScheduleService scheduleService, CorsDbProperties properties) {
        return new CorsMySqlAccountStatusSyncJob(accountMapper, repository, snapshotMapper, applyService,
                scheduleService, properties);
    }
}

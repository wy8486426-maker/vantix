package com.sinognss.cloud.vantix.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalFinalizeService;
import com.sinognss.cloud.vantix.config.CorsAccountStatusSyncProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Runs against MySQL 5.7.44 in Testcontainers, or an isolated temporary schema
 * on a developer-provided local MySQL server when VANTIX_TEST_MYSQL_* is set.
 */
@EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlAccountRenewalStaleIgnoredFinalizeIntegrationTest {
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static final LocalMySqlTestDatabase MYSQL =
            LocalMySqlTestDatabase.create("vantix_renewal_stale_it");

    @BeforeAll
    static void startDatabase() throws Exception {
        jdbcUrl = MYSQL.getJdbcUrl();
        username = MYSQL.getUsername();
        password = MYSQL.getPassword();
    }

    @AfterAll
    static void stopDatabase() {
        MYSQL.close();
    }

    @Test
    void staleRenewalSuccessConsumesCodeWithoutOverwritingNewerAccountSnapshot() throws Exception {
        Flyway.configure().dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long accountId = 910_001L;
        long codeId = 920_001L;
        String requestId = "RN-STALE-" + UUID.randomUUID();
        insertFixture(jdbc, accountId, codeId, requestId);
        Long renewalId = jdbc.queryForObject(
                "SELECT id FROM account_renewal WHERE request_id = ?", Long.class, requestId);
        Long operationId = jdbc.queryForObject(
                "SELECT id FROM cors_operation WHERE request_id = ?", Long.class, requestId);

        SqlSessionFactory sessionFactory = sqlSessionFactory(dataSource);
        SqlSessionTemplate sessions = new SqlSessionTemplate(sessionFactory);
        AccountRenewalMapper renewalMapper = sessions.getMapper(AccountRenewalMapper.class);
        CorsOperationMapper operationMapper = sessions.getMapper(CorsOperationMapper.class);
        ServiceAccountMapper accountMapper = sessions.getMapper(ServiceAccountMapper.class);
        ServiceCodeMapper codeMapper = sessions.getMapper(ServiceCodeMapper.class);
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        Clock clock = Clock.fixed(Instant.parse("2031-01-20T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        CorsAccountStateApplyService applyService = new CorsAccountStateApplyService(accountMapper);
        AccountStatusSyncScheduleService scheduleService = new AccountStatusSyncScheduleService(
                accountMapper, new CorsAccountStatusSyncProperties(), clock);
        AccountRenewalFinalizeService target = new AccountRenewalFinalizeService(operationMapper, renewalMapper,
                accountMapper, codeMapper, applyService, scheduleService, clock);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        AccountRenewalFinalizeService transactionalFinalize =
                (AccountRenewalFinalizeService) proxyFactory.getProxy();

        CorsAccountSnapshot staleSnapshot = new CorsAccountSnapshot(
                "cors-renewal-" + accountId, "renewal-account-" + accountId,
                "ENABLED", "ACTIVE",
                time("2030-01-01T00:00:00+08:00"),
                time("2031-01-01T00:00:00+08:00"),
                time("2029-01-01T00:00:00+08:00"),
                time("2031-01-20T03:00:00Z"));
        transactionalFinalize.finalizeSuccess(operationId, 3L,
                CorsAccountRenewalResult.success(requestId, staleSnapshot));

        assertEquals("2031-02-01 00:00:00.000000", jdbc.queryForObject(
                "SELECT DATE_FORMAT(expire_at, '%Y-%m-%d %H:%i:%s.%f') FROM service_account WHERE id = ?",
                String.class, accountId));
        assertEquals("2031-01-20 12:00:00.000000", jdbc.queryForObject(
                "SELECT DATE_FORMAT(cors_updated_at, '%Y-%m-%d %H:%i:%s.%f') "
                        + "FROM service_account WHERE id = ?", String.class, accountId));
        assertEquals(11L, jdbc.queryForObject(
                "SELECT version FROM service_account WHERE id = ?", Long.class, accountId),
                "stale apply may advance sync metadata version but must preserve the business snapshot");

        assertEquals("CONSUMED", jdbc.queryForObject(
                "SELECT status FROM service_code WHERE id = ?", String.class, codeId));
        assertEquals("RENEWAL", jdbc.queryForObject(
                "SELECT consume_type FROM service_code WHERE id = ?", String.class, codeId));
        assertNotNull(jdbc.queryForObject(
                "SELECT consumed_at FROM service_code WHERE id = ?", java.sql.Timestamp.class, codeId));
        assertNull(jdbc.queryForObject(
                "SELECT processing_type FROM service_code WHERE id = ?", String.class, codeId));
        assertNull(jdbc.queryForObject(
                "SELECT processing_request_id FROM service_code WHERE id = ?", String.class, codeId));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM account_renewal WHERE id = ?", String.class, renewalId));
        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE id = ?", String.class, operationId));
        assertFalse(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM account_renewal WHERE service_account_id = ? "
                        + "AND status = 'MANUAL_REVIEW')", Boolean.class, accountId));
    }

    private static void insertFixture(JdbcTemplate jdbc, long accountId, long codeId, String requestId) {
        jdbc.update("INSERT INTO service_account (id, cors_account_id, account, owner_company_id, "
                        + "source_service_code_id, service_type, duration_value, duration_unit, "
                        + "account_silence_months, cors_status, cors_activation_status, activated_at, expire_at, "
                        + "cors_updated_at, last_sync_at, version) "
                        + "VALUES (?, ?, ?, 901, ?, 'CORS', 1, 'MONTH', 6, 'ENABLED', 'ACTIVE', "
                        + "'2030-01-01 00:00:00.000', '2031-02-01 00:00:00.000', "
                        + "'2031-01-20 12:00:00.000', '2031-01-20 12:00:00.000', 10)",
                accountId, "cors-renewal-" + accountId, "renewal-account-" + accountId, accountId);
        jdbc.update("INSERT INTO service_code (id, code, owner_company_id, service_type, duration_value, "
                        + "duration_unit, code_silence_months, expire_at, status, processing_type, "
                        + "processing_request_id, version) VALUES (?, ?, 901, 'CORS', 3, 'MONTH', 6, "
                        + "'2032-01-01 00:00:00.000', 'PROCESSING', 'RENEWAL', ?, 7)",
                codeId, "VANTIX-STALE-" + codeId, requestId);
        jdbc.update("INSERT INTO account_renewal (service_account_id, service_code_id, owner_company_id, "
                        + "service_type, duration_value, duration_unit, service_code_snapshot, request_id, "
                        + "status, version) VALUES (?, ?, 901, 'CORS', 3, 'MONTH', CAST('{}' AS JSON), ?, "
                        + "'PROCESSING', 5)", accountId, codeId, requestId);
        Long renewalId = jdbc.queryForObject(
                "SELECT id FROM account_renewal WHERE request_id = ?", Long.class, requestId);
        jdbc.update("INSERT INTO cors_operation (request_id, operation_type, biz_type, biz_id, "
                        + "service_account_id, status, retry_count, version, claimed_at) "
                        + "VALUES (?, 'RENEW_ACCOUNT', 'ACCOUNT_RENEWAL', ?, ?, 'CLAIMED', 0, 3, "
                        + "'2031-01-20 12:00:00.000')", requestId, renewalId, accountId);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AccountRenewalMapper.class);
        configuration.addMapper(CorsOperationMapper.class);
        configuration.addMapper(ServiceAccountMapper.class);
        configuration.addMapper(ServiceCodeMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml"));
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    private static OffsetDateTime time(String value) {
        return OffsetDateTime.parse(value);
    }

}

package com.sinognss.cloud.vantix.integration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.application.account.ServiceAccountQueryService;
import com.sinognss.cloud.vantix.application.account.ServiceAccountStatistics;
import com.sinognss.cloud.vantix.application.account.ServiceAccountStatisticsQuery;
import com.sinognss.cloud.vantix.application.account.ServiceAccountView;
import com.sinognss.cloud.vantix.application.account.ServiceAccountPageQuery;
import com.sinognss.cloud.vantix.application.dashboard.DashboardQueryService;
import com.sinognss.cloud.vantix.application.dashboard.DashboardView;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogDetailView;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogPageQuery;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogQueryService;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogView;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalLogPageQuery;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalLogQueryService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalLogView;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalQueryService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalView;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DashboardQueryMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeLogQueryMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountQueryMapper;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlFrontendQueryIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 10, 0);
    private static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_frontend_queries");

    private static JdbcTemplate jdbc;
    private static UserHolderBridge userHolder;
    private static ServiceAccountQueryService accountQueryService;
    private static ExchangeLogQueryService exchangeLogQueryService;
    private static AccountRenewalLogQueryService renewalLogQueryService;
    private static AccountRenewalQueryService renewalQueryService;
    private static DashboardQueryService dashboardQueryService;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        DataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sessions = new SqlSessionTemplate(sqlSessionFactory(dataSource));
        userHolder = new UserHolderBridge();
        accountQueryService = new ServiceAccountQueryService(
                sessions.getMapper(ServiceAccountQueryMapper.class), userHolder);
        exchangeLogQueryService = new ExchangeLogQueryService(
                sessions.getMapper(ExchangeLogQueryMapper.class), userHolder);
        renewalLogQueryService = new AccountRenewalLogQueryService(
                sessions.getMapper(AccountRenewalLogQueryMapper.class), userHolder);
        renewalQueryService = new AccountRenewalQueryService(
                sessions.getMapper(AccountRenewalMapper.class),
                sessions.getMapper(AccountRenewalLogQueryMapper.class), userHolder);
        VantixProperties properties = new VantixProperties();
        properties.setUpcomingDays(30);
        dashboardQueryService = new DashboardQueryService(
                sessions.getMapper(DashboardQueryMapper.class), userHolder,
                Clock.fixed(Instant.parse("2026-09-16T02:00:00Z"), ZoneOffset.UTC), properties);
    }

    @AfterAll
    static void closeDatabase() {
        MYSQL.close();
    }

    @BeforeEach
    void resetData() {
        UserHolder.removeUser();
        jdbc.update("DELETE FROM account_renewal");
        jdbc.update("DELETE FROM service_account");
        jdbc.update("DELETE FROM exchange_detail");
        jdbc.update("DELETE FROM exchange_batch");
        jdbc.update("DELETE FROM service_code_transfer");
        jdbc.update("DELETE FROM service_code");
        jdbc.update("DELETE FROM service_code_generate_batch");
        jdbc.update("DELETE FROM service_code_generate_order");
        jdbc.update("DELETE FROM service_duration_config");
        jdbc.update("DELETE FROM company_exchange_config");
        jdbc.update("DELETE FROM dealer_company");
        jdbc.update("INSERT INTO dealer_company (company_id, company_name, company_status) VALUES "
                + "(10, '公司十', 'ACTIVE'), (20, '公司二十', 'ACTIVE'), (30, '公司三十', 'ACTIVE')");
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void serviceAccountQueriesUseMySqlScopeFiltersAndDerivedStatuses() {
        insertServiceAccountFixture();

        asPersonalUser(7L, 10L);
        PageResponse<ServiceAccountView> personal = accountQueryService.page(
                new ServiceAccountPageQuery(1, 20, null, null, null, null, null, null));
        assertEquals(Set.of(1001L, 1003L), personal.records().stream()
                .map(ServiceAccountView::id).collect(Collectors.toSet()));
        ServiceAccountStatistics personalStatistics = accountQueryService.statistics(
                new ServiceAccountStatisticsQuery(null, null, null, null, null));
        assertEquals(new ServiceAccountStatistics(2, 1, 0, 1, 0), personalStatistics);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> accountQueryService.get(1002L)).getVantixErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> accountQueryService.get(2001L)).getVantixErrorCode());

        asCompanyUser(10L);
        assertEquals(4, accountQueryService.page(
                new ServiceAccountPageQuery(1, 20, null, null, null, null, null, null)).total());
        assertEquals(1, accountQueryService.page(
                new ServiceAccountPageQuery(1, 20, null, "DISABLED", null, null, null, null))
                .records().size());

        asGlobalUser();
        assertEquals(5, accountQueryService.page(
                new ServiceAccountPageQuery(1, 20, null, null, null, null, null, null)).total());
    }

    @Test
    void exchangeLogUsesFrozenBatchDisplayNameAndCorrelatedSuccessCount() {
        insertExchangeFixture();
        asPersonalUser(7L, 10L);

        PageResponse<ExchangeLogView> page = exchangeLogQueryService.page(
                new ExchangeLogPageQuery(1, 20, null, null, null, null, null, null));
        assertEquals(1, page.total());
        assertEquals("兑换时名称", page.records().get(0).displayName());
        assertEquals(1, page.records().get(0).successQuantity());
        assertEquals(1, exchangeLogQueryService.page(new ExchangeLogPageQuery(
                1, 20, "兑换时名称", null, null, null, null, null)).total());
        assertEquals(0, exchangeLogQueryService.page(new ExchangeLogPageQuery(
                1, 20, "生成批次旧名称", null, null, null, null, null)).total());

        ExchangeLogDetailView detail = exchangeLogQueryService.detail("EXCHANGE-10");
        assertEquals("兑换时名称", detail.batch().displayName());
        assertEquals(3, detail.items().size());
        assertEquals(1, detail.batch().successQuantity());

        asCompanyUser(10L);
        assertEquals(1, exchangeLogQueryService.page(
                new ExchangeLogPageQuery(1, 20, null, null, null, null, null, null)).total());
        asGlobalUser();
        assertEquals(2, exchangeLogQueryService.page(
                new ExchangeLogPageQuery(1, 20, null, null, null, null, null, null)).total());
    }

    @Test
    void renewalListAndDetailApplyPersonalCompanyAndGlobalScopes() {
        insertRenewalFixture();

        asPersonalUser(7L, 10L);
        PageResponse<AccountRenewalLogView> personal = renewalLogQueryService.page(
                new AccountRenewalLogPageQuery(1, 20, null, null, null, null, null));
        assertEquals(1, personal.total());
        assertEquals("续期规格一", personal.records().get(0).displayName());
        assertEquals(1, renewalLogQueryService.page(new AccountRenewalLogPageQuery(
                1, 20, "续期码一", null, null, null, null)).total());
        assertEquals(1, renewalLogQueryService.page(new AccountRenewalLogPageQuery(
                1, 20, null, "COMPLETED", null, null, null)).total());
        assertEquals(0, renewalLogQueryService.page(new AccountRenewalLogPageQuery(
                1, 20, null, null, null, NOW.plusMinutes(1), NOW.plusHours(1))).total());

        AccountRenewalView personalDetail = renewalQueryService.get("RENEWAL-10-7");
        assertEquals(9, personalDetail.codeSilenceDays());
        assertEquals("续期码一", personalDetail.serviceCode());
        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, assertThrows(BusinessException.class,
                () -> renewalQueryService.get("RENEWAL-10-8")).getVantixErrorCode());

        asCompanyUser(10L);
        assertEquals(2, renewalLogQueryService.page(new AccountRenewalLogPageQuery(
                1, 20, null, null, null, null, null)).total());
        assertEquals("续期规格二", renewalQueryService.get("RENEWAL-10-8").displayName());

        asGlobalUser();
        assertEquals(3, renewalLogQueryService.page(new AccountRenewalLogPageQuery(
                1, 20, null, null, null, null, null)).total());
        assertEquals("续期规格三", renewalQueryService.get("RENEWAL-20-9").displayName());
    }

    @Test
    void dashboardAggregatesEachMetricForPersonalCompanyAndGlobalScopes() {
        insertDashboardFixture();

        asPersonalUser(7L, 10L);
        assertEquals(new DashboardView(5, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0,
                1, 1, 1, 1), dashboardQueryService.get());

        asCompanyUser(10L);
        assertEquals(new DashboardView(5, 1, 1, 1, 1, 1, 2, 1, 1, 0, 0,
                1, 1, 2, 1), dashboardQueryService.get());

        asGlobalUser();
        assertEquals(new DashboardView(6, 2, 1, 1, 1, 1, 3, 1, 1, 0, 1,
                2, 2, 3, 2), dashboardQueryService.get());
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ServiceAccountQueryMapper.class);
        configuration.addMapper(ExchangeLogQueryMapper.class);
        configuration.addMapper(AccountRenewalLogQueryMapper.class);
        configuration.addMapper(AccountRenewalMapper.class);
        configuration.addMapper(DashboardQueryMapper.class);

        MybatisPlusInterceptor pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setPlugins(new Interceptor[]{pagination});
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml"));
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    private void insertServiceAccountFixture() {
        insertGeneration(1001L, 11001L, 10L, "S1", "生成规格一", NOW.minusHours(4));
        insertGeneration(1002L, 11002L, 10L, "S1", "生成规格二", NOW.minusHours(3));
        insertGeneration(1003L, 11003L, 10L, "S1", "生成规格三", NOW.minusHours(2));
        insertGeneration(1004L, 11004L, 10L, "S1", "生成规格四", NOW.minusHours(1));
        insertGeneration(2001L, 12001L, 20L, "S1", "生成规格五", NOW.minusMinutes(30));
        insertServiceCode(1101L, "ACCOUNT-CODE-1", 1001L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(1102L, "ACCOUNT-CODE-2", 1002L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(1103L, "ACCOUNT-CODE-3", 1003L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(1104L, "ACCOUNT-CODE-4", 1004L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(1201L, "ACCOUNT-CODE-5", 2001L, 20L, "S1", NOW.plusDays(30), "PENDING");
        insertAccount(1001L, 10L, 7L, 1101L, "ENABLED", "WAITING_ACTIVATION", NOW.plusDays(30), NOW.minusHours(4));
        insertAccount(1002L, 10L, 8L, 1102L, "ENABLED", "ACTIVE", NOW.plusDays(30), NOW.minusHours(3));
        insertAccount(1003L, 10L, 7L, 1103L, "ENABLED", "EXPIRED", NOW.minusDays(1), NOW.minusHours(2));
        insertAccount(1004L, 10L, 8L, 1104L, "DISABLED", "ACTIVE", NOW.plusDays(30), NOW.minusHours(1));
        insertAccount(2001L, 20L, 8L, 1201L, "ENABLED", "ACTIVE", NOW.plusDays(30), NOW.minusMinutes(30));
    }

    private void insertExchangeFixture() {
        insertGeneration(6001L, 16001L, 10L, "S1", "生成批次旧名称", NOW.minusHours(4));
        insertGeneration(6002L, 16002L, 10L, "S1", "生成批次另一个旧名称", NOW.minusHours(3));
        insertGeneration(6003L, 16003L, 10L, "S1", "生成批次第三个旧名称", NOW.minusHours(2));
        insertServiceCode(3101L, "EX-CODE-1", 6001L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(3102L, "EX-CODE-2", 6002L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(3103L, "EX-CODE-3", 6003L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertExchangeBatch(3001L, "EX-BATCH-10", "EXCHANGE-10", 10L, 7L,
                "兑换时名称", "COMPLETED", NOW.minusHours(2));
        insertExchangeBatch(3002L, "EX-BATCH-20", "EXCHANGE-20", 20L, 8L,
                "另一公司名称", "PROCESSING", NOW.minusHours(1));
        insertExchangeDetail(3101L, 3001L, 1, 3101L, "EX-CODE-1", "COMPLETED");
        insertExchangeDetail(3102L, 3001L, 2, 3102L, "EX-CODE-2", "PROCESSING");
        insertExchangeDetail(3103L, 3001L, 3, 3103L, "EX-CODE-3", "FAILED");
    }

    private void insertRenewalFixture() {
        insertGeneration(4001L, 14001L, 10L, "S1", "续期规格一", NOW.minusHours(3));
        insertGeneration(4002L, 14002L, 10L, "S1", "续期规格二", NOW.minusHours(2));
        insertGeneration(4003L, 14003L, 20L, "S1", "续期规格三", NOW.minusHours(1));
        insertServiceCode(4101L, "续期码一", 4001L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(4102L, "续期码二", 4002L, 10L, "S1", NOW.plusDays(30), "PENDING");
        insertServiceCode(4103L, "续期码三", 4003L, 20L, "S1", NOW.plusDays(30), "PENDING");
        insertAccount(4201L, 10L, 7L, 4101L, "ENABLED", "ACTIVE", NOW.plusDays(12), NOW.minusHours(3));
        insertAccount(4202L, 10L, 8L, 4102L, "ENABLED", "ACTIVE", NOW.plusDays(14), NOW.minusHours(2));
        insertAccount(4203L, 20L, 9L, 4103L, "ENABLED", "ACTIVE", NOW.plusDays(16), NOW.minusHours(1));
        insertRenewal(4301L, 4201L, 4101L, 10L, 7L, "RENEWAL-10-7", "续期码一", "COMPLETED", 9, NOW.minusHours(3));
        insertRenewal(4302L, 4202L, 4102L, 10L, 8L, "RENEWAL-10-8", "续期码二", "FAILED", 10, NOW.minusHours(2));
        insertRenewal(4303L, 4203L, 4103L, 20L, 9L, "RENEWAL-20-9", "续期码三", "MANUAL_REVIEW", 11, NOW.minusHours(1));
    }

    private void insertDashboardFixture() {
        insertServiceCode(5001L, "DASH-CODE-1", null, 10L, "S1", NOW.plusDays(45), "PENDING");
        insertServiceCode(5002L, "DASH-CODE-2", null, 10L, "S1", NOW.plusDays(10), "PENDING");
        insertServiceCode(5003L, "DASH-CODE-3", null, 10L, "S1", NOW.minusDays(1), "PENDING");
        insertServiceCode(5004L, "DASH-CODE-4", null, 10L, "S1", NOW.plusDays(10), "PROCESSING");
        insertServiceCode(5005L, "DASH-CODE-5", null, 10L, "S1", NOW.plusDays(10), "CONSUMED");
        insertServiceCode(5006L, "DASH-CODE-6", null, 20L, "S1", NOW.plusDays(45), "PENDING");
        insertAccount(5101L, 10L, 7L, 5001L, "ENABLED", "ACTIVE", NOW.plusDays(30), NOW.minusHours(2));
        insertAccount(5102L, 10L, 8L, 5002L, "ENABLED", "WAITING_ACTIVATION", NOW.plusDays(30), NOW.minusHours(1));
        insertAccount(5201L, 20L, 9L, 5006L, "DISABLED", "ACTIVE", NOW.plusDays(30), NOW.minusMinutes(30));
        insertGenerationOrderOnly(5301L, 10L, NOW.minusHours(3));
        insertGenerationOrderOnly(5302L, 20L, NOW.minusHours(2));
        insertExchangeBatch(5401L, "DASH-EX-10", "DASH-EXCHANGE-10", 10L, 7L,
                "仪表盘兑换一", "COMPLETED", NOW.minusHours(2));
        insertExchangeBatch(5402L, "DASH-EX-20", "DASH-EXCHANGE-20", 20L, 9L,
                "仪表盘兑换二", "FAILED", NOW.minusHours(1));
        insertRenewal(5501L, 5101L, 5001L, 10L, 7L, "DASH-RENEWAL-10-7", "DASH-CODE-1", "COMPLETED", 9, NOW.minusHours(2));
        insertRenewal(5502L, 5102L, 5002L, 10L, 8L, "DASH-RENEWAL-10-8", "DASH-CODE-2", "COMPLETED", 10, NOW.minusHours(1));
        insertRenewal(5503L, 5201L, 5006L, 20L, 9L, "DASH-RENEWAL-20-9", "DASH-CODE-6", "COMPLETED", 11, NOW.minusMinutes(30));
        jdbc.update("INSERT INTO service_code_transfer "
                        + "(transfer_no, service_code_id, service_code, from_company_id, to_company_id, transfer_type, created_at) "
                        + "VALUES ('DASH-TRANSFER-1', 5001, 'DASH-CODE-1', 10, 20, 'PARENT_CHILD', ?), "
                        + "('DASH-TRANSFER-2', 5002, 'DASH-CODE-2', 20, 30, 'PARENT_CHILD', ?)",
                NOW.minusHours(2), NOW.minusHours(1));
    }

    private void insertGeneration(long orderId, long batchId, long ownerCompanyId, String specCode,
                                  String displayName, LocalDateTime createdAt) {
        insertGenerationOrderOnly(orderId, ownerCompanyId, createdAt);
        jdbc.update("INSERT INTO service_code_generate_batch "
                        + "(id, batch_no, request_id, generation_source, source_order_no, owner_company_id, spec_code, "
                        + "display_name, service_type, duration_days, code_silence_days, quantity, generated_count, "
                        + "status, business_key_hash, generate_order_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'USER', ?, ?, ?, ?, 'CORS', 30, 180, 1, 1, 'COMPLETED', ?, ?, ?, ?)",
                batchId, "BATCH-" + batchId, "BATCH-REQUEST-" + batchId, "ORDER-" + orderId,
                ownerCompanyId, specCode, displayName, String.format("%064x", batchId), orderId,
                createdAt, createdAt);
    }

    private void insertGenerationOrderOnly(long orderId, long ownerCompanyId, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO service_code_generate_order "
                        + "(id, request_id, generation_source, source_order_no, owner_company_id, payload_hash, "
                        + "item_count, total_quantity, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'USER', ?, ?, ?, 1, 1, 'COMPLETED', ?, ?)",
                orderId, "ORDER-REQUEST-" + orderId, "ORDER-" + orderId, ownerCompanyId,
                String.format("%064x", orderId), createdAt, createdAt);
    }

    private void insertServiceCode(Long id, String code, Long orderId, long ownerCompanyId,
                                   String specCode, LocalDateTime expireAt, String status) {
        Long batchId = orderId == null ? null : jdbc.queryForObject(
                "SELECT id FROM service_code_generate_batch WHERE generate_order_id = ?", Long.class, orderId);
        jdbc.update("INSERT INTO service_code "
                        + "(id, code, source_order_id, source_order_no, generate_batch_id, owner_company_id, spec_code, "
                        + "service_type, duration_days, code_silence_days, expire_at, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'CORS', 30, 9, ?, ?, 0, ?, ?)",
                id, code, orderId, orderId == null ? null : "ORDER-" + orderId, batchId, ownerCompanyId,
                specCode, expireAt, status, NOW, NOW);
    }

    private void insertAccount(long id, long ownerCompanyId, long assignedUserId, long sourceServiceCodeId,
                               String corsStatus, String activationStatus, LocalDateTime expireAt,
                               LocalDateTime createdAt) {
        jdbc.update("INSERT INTO service_account "
                        + "(id, cors_account_id, account, owner_company_id, assigned_user_id, source_service_code_id, "
                        + "spec_code, display_name, service_type, duration_days, account_silence_days, cors_status, "
                        + "cors_activation_status, expire_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'S1', '测试规格', 'CORS', 30, 360, ?, ?, ?, ?, ?)",
                id, "CORS-ACCOUNT-" + id, "ACCOUNT-" + id, ownerCompanyId, assignedUserId, sourceServiceCodeId,
                corsStatus, activationStatus, expireAt, createdAt, createdAt);
    }

    private void insertExchangeBatch(long id, String batchNo, String requestId, long ownerCompanyId,
                                     long assignedUserId, String displayName, String status,
                                     LocalDateTime createdAt) {
        jdbc.update("INSERT INTO exchange_batch "
                        + "(id, exchange_batch_no, request_id, owner_company_id, assigned_user_id, generation_source, "
                        + "spec_code, display_name, service_type, duration_days, account_silence_days, quantity, "
                        + "payload_hash, status, created_at, updated_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'USER', 'S1', ?, 'CORS', 30, 360, 3, ?, ?, ?, ?, ?)",
                id, batchNo, requestId, ownerCompanyId, assignedUserId, displayName,
                String.format("%064x", id), status, createdAt, createdAt,
                "COMPLETED".equals(status) ? createdAt : null);
    }

    private void insertExchangeDetail(long id, long batchId, int detailIndex, long serviceCodeId,
                                      String serviceCode, String status) {
        jdbc.update("INSERT INTO exchange_detail "
                        + "(id, exchange_batch_id, detail_index, service_code_id, request_id, service_code_snapshot, "
                        + "status, active_service_code_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, batchId, detailIndex, serviceCodeId, "DETAIL-" + id,
                "{\"code\":\"" + serviceCode + "\"}", status,
                "PROCESSING".equals(status) || "COMPLETED".equals(status) ? serviceCodeId : null);
    }

    private void insertRenewal(long id, long serviceAccountId, long serviceCodeId, long ownerCompanyId,
                               long assignedUserId, String requestId, String snapshotCode, String status,
                               int codeSilenceDays, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO account_renewal "
                        + "(id, service_account_id, service_code_id, owner_company_id, assigned_user_id, spec_code, "
                        + "service_type, duration_days, code_silence_days, service_code_snapshot, request_id, status, "
                        + "created_at, updated_at, completed_at, version, active_service_code_id, "
                        + "active_service_account_id) VALUES (?, ?, ?, ?, ?, 'S1', 'CORS', 30, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                id, serviceAccountId, serviceCodeId, ownerCompanyId, assignedUserId, codeSilenceDays,
                "{\"code\":\"" + snapshotCode + "\"}", requestId, status, createdAt, createdAt,
                "COMPLETED".equals(status) ? createdAt : null,
                "PROCESSING".equals(status) || "COMPLETED".equals(status) || "MANUAL_REVIEW".equals(status)
                        ? serviceCodeId : null,
                "PROCESSING".equals(status) || "MANUAL_REVIEW".equals(status) ? serviceAccountId : null);
    }

    private void asPersonalUser(long userId, long companyId) {
        asUser(userId, companyId, 3);
    }

    private void asCompanyUser(long companyId) {
        asUser(88L, companyId, 2);
    }

    private void asGlobalUser() {
        asUser(88L, null, 4);
    }

    private void asUser(Long userId, Long companyId, int dataType) {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(userId);
        user.setCompanyId(companyId);
        user.setUserNickname("frontend-query-test");
        user.setDataType(dataType);
        UserHolder.setUser(user);
    }
}

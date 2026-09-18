package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.application.servicecode.DisplayStatus;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodePageQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeService;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeSpecStatistics;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeSpecStatisticsItem;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeStatistics;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeStatisticsQuery;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlServiceCodeQueryIntegrationTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_service_code_query");

    @AfterAll
    static void closeDatabase() {
        MYSQL.close();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ServiceCodeService service;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void resetData() {
        UserHolder.removeUser();
        jdbc.update("DELETE FROM service_code_transfer");
        jdbc.update("DELETE FROM service_code");
        jdbc.update("DELETE FROM service_code_generate_batch");
        jdbc.update("DELETE FROM service_code_generate_order");
        jdbc.update("DELETE FROM service_duration_config");
        jdbc.update("DELETE FROM dealer_company");
        jdbc.update("INSERT INTO dealer_company (company_id, company_name, company_status) VALUES "
                + "(10, '客户十', 'ACTIVE'), (20, '客户二十', 'ACTIVE')");
        jdbc.update("INSERT INTO service_code_generate_order "
                + "(request_id, generation_source, source_order_no, owner_company_id, payload_hash, "
                + "item_count, total_quantity, status) VALUES "
                + "('QUERY-ORDER-1', 'INTERNAL', 'ORDER-10', 10, REPEAT('a', 64), 1, 3, 'COMPLETED')");
        jdbc.update("INSERT INTO service_code_generate_batch "
                + "(batch_no, request_id, generation_source, source_order_no, owner_company_id, spec_code, "
                + "display_name, service_type, duration_days, code_silence_days, quantity, generated_count, "
                + "status, business_key_hash, generate_order_id) VALUES "
                + "('BATCH-OLD', 'QUERY-BATCH-1', 'INTERNAL', 'ORDER-10', 10, 'SC001', '旧规格名称', "
                + "'NTRIP', 90, 30, 3, 3, 'COMPLETED', REPEAT('b', 64), 1)");
        jdbc.update("INSERT INTO service_code_generate_order "
                + "(request_id, generation_source, source_order_no, owner_company_id, payload_hash, "
                + "item_count, total_quantity, status) VALUES "
                + "('QUERY-ORDER-2', 'INTERNAL', 'ORDER-20', 20, REPEAT('c', 64), 1, 2, 'COMPLETED')");
        jdbc.update("INSERT INTO service_code_generate_batch "
                + "(batch_no, request_id, generation_source, source_order_no, owner_company_id, spec_code, "
                + "display_name, service_type, duration_days, code_silence_days, quantity, generated_count, "
                + "status, business_key_hash, generate_order_id) VALUES "
                + "('BATCH-20', 'QUERY-BATCH-2', 'INTERNAL', 'ORDER-20', 20, 'SC002', '客户二十规格', "
                + "'CORS', 30, 15, 2, 2, 'COMPLETED', REPEAT('d', 64), 2)");
        jdbc.update("INSERT INTO service_duration_config "
                + "(spec_code, display_name, service_type, duration_days, code_silence_days, account_silence_days) "
                + "VALUES ('SC001', '当前新规格名称', 'NTRIP', 90, 30, 15)");

        Long oldBatchId = jdbc.queryForObject(
                "SELECT id FROM service_code_generate_batch WHERE batch_no = 'BATCH-OLD'", Long.class);
        Long companyBatchId = jdbc.queryForObject(
                "SELECT id FROM service_code_generate_batch WHERE batch_no = 'BATCH-20'", Long.class);
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        insertCode("QUERY-EXPIRED", "ORDER-10", 10L, oldBatchId, "SC001", "NTRIP", 90,
                now.minusDays(1), ServiceCodeStatus.PENDING, now.minusMinutes(5));
        insertCode("QUERY-EXPIRING", "ORDER-10", 10L, oldBatchId, "SC001", "NTRIP", 90,
                now.plusDays(1), ServiceCodeStatus.PENDING, now.minusMinutes(4));
        insertCode("QUERY-WAITING", "ORDER-20", 20L, companyBatchId, "SC002", "CORS", 30,
                now.plusDays(40), ServiceCodeStatus.PENDING, now.minusMinutes(3));
        insertCode("QUERY-PROCESSING", "ORDER-20", 20L, companyBatchId, "SC002", "CORS", 30,
                now.minusDays(1), ServiceCodeStatus.PROCESSING, now.minusMinutes(2));
        insertCode("QUERY-CONSUMED", "ORDER-20", 20L, companyBatchId, "SC002", "CORS", 30,
                now.minusDays(1), ServiceCodeStatus.CONSUMED, now.minusMinutes(1));
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void listUsesSnapshotJoinsStableOrderingAndDatabasePagination() {
        asGlobalUser();

        PageResponse<com.sinognss.cloud.vantix.application.servicecode.ServiceCodeView> first = service.page(
                new ServiceCodePageQuery(1, 2, null, null, null, null, null, null, null));
        PageResponse<com.sinognss.cloud.vantix.application.servicecode.ServiceCodeView> second = service.page(
                new ServiceCodePageQuery(2, 2, null, null, null, null, null, null, null));
        PageResponse<com.sinognss.cloud.vantix.application.servicecode.ServiceCodeView> third = service.page(
                new ServiceCodePageQuery(3, 2, null, null, null, null, null, null, null));

        assertEquals(5, first.total());
        assertEquals(3, first.pages());
        assertEquals("QUERY-CONSUMED", first.records().get(0).code());
        assertEquals("QUERY-PROCESSING", first.records().get(1).code());
        assertEquals("客户二十规格", first.records().get(0).displayName());
        assertEquals("客户二十", first.records().get(0).ownerCompanyName());
        List<String> codes = new ArrayList<>();
        codes.addAll(first.records().stream().map(view -> view.code()).toList());
        codes.addAll(second.records().stream().map(view -> view.code()).toList());
        codes.addAll(third.records().stream().map(view -> view.code()).toList());
        assertEquals(5, codes.stream().distinct().count());
    }

    @Test
    void keywordAndExactFiltersSearchJoinedHistoricalFieldsBeforePaging() {
        asGlobalUser();

        assertEquals(1, service.page(query("QUERY-EXPIR", null, null, null, null)).total());
        assertEquals(2, service.page(query("ORDER-10", null, null, null, null)).total());
        assertEquals(2, service.page(query("旧规格名称", null, null, null, null)).total());
        assertEquals(2, service.page(query("客户二十", null, null, null, null)).total());
        assertEquals(1, service.page(query(null, "SC001", 90, "ORDER-10", null)).total());
        assertEquals(0, service.page(query("不存在的关键字", null, null, null, null)).total());
    }

    @Test
    void displayStatusFiltersMatchReturnedStatusAndStatisticsUseOneAggregation() {
        asGlobalUser();

        assertEquals(1, service.page(new ServiceCodePageQuery(1, 20, null, null,
                DisplayStatus.WAITING, null, null, null, null)).total());
        assertEquals(1, service.page(new ServiceCodePageQuery(1, 20, null, null,
                DisplayStatus.EXPIRING, null, null, null, null)).total());
        assertEquals(1, service.page(new ServiceCodePageQuery(1, 20, null, null,
                DisplayStatus.EXPIRED, null, null, null, null)).total());
        assertEquals(1, service.page(new ServiceCodePageQuery(1, 20, null, null,
                DisplayStatus.PROCESSING, null, null, null, null)).total());
        assertEquals(1, service.page(new ServiceCodePageQuery(1, 20, null, null,
                DisplayStatus.CONSUMED, null, null, null, null)).total());

        ServiceCodeStatistics statistics = service.statistics(new ServiceCodeStatisticsQuery(
                null, null, null, null, null));
        assertEquals(5, statistics.total());
        assertEquals(1, statistics.waiting());
        assertEquals(1, statistics.expiring());
        assertEquals(1, statistics.expired());
        assertEquals(1, statistics.processing());
        assertEquals(1, statistics.consumed());
        assertEquals(statistics.total(), statistics.waiting() + statistics.expiring() + statistics.expired()
                + statistics.processing() + statistics.consumed());

        ServiceCodeStatistics filtered = service.statistics(new ServiceCodeStatisticsQuery(
                "客户二十", "SC002", 30, "ORDER-20", null));
        assertEquals(3, filtered.total());
        assertEquals(1, filtered.waiting());
        assertEquals(0, filtered.expiring());
        assertEquals(0, filtered.expired());
        assertEquals(1, filtered.processing());
        assertEquals(1, filtered.consumed());
    }

    @Test
    void specStatisticsUsesSpecCodeScopeAndDisplayStatusAggregation() {
        Long sameDurationBatchId = insertSameDurationDifferentSpecBatch();
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        insertCode("QUERY-SAME-DURATION", "ORDER-20", 20L, sameDurationBatchId, "SC003", "CORS", 30,
                now.plusDays(2), ServiceCodeStatus.CONSUMED, now);

        asGlobalUser();
        ServiceCodeSpecStatistics global = service.specStatistics(null);
        Map<String, ServiceCodeSpecStatisticsItem> globalItems = bySpec(global);
        assertEquals(6, global.total());
        assertEquals(3, globalItems.size());
        assertEquals(2, globalItems.get("SC001").total());
        assertEquals(0, globalItems.get("SC001").waiting());
        assertEquals(1, globalItems.get("SC001").expiring());
        assertEquals(0, globalItems.get("SC001").processing());
        assertEquals(0, globalItems.get("SC001").consumed());
        assertEquals(1, globalItems.get("SC001").expired());
        assertEquals(3, globalItems.get("SC002").total());
        assertEquals(1, globalItems.get("SC002").waiting());
        assertEquals(0, globalItems.get("SC002").expiring());
        assertEquals(1, globalItems.get("SC002").processing());
        assertEquals(1, globalItems.get("SC002").consumed());
        assertEquals(0, globalItems.get("SC002").expired());
        assertEquals(1, globalItems.get("SC003").total());
        assertEquals(30, globalItems.get("SC002").durationDays());
        assertEquals(30, globalItems.get("SC003").durationDays());
        assertEquals("另一规格", globalItems.get("SC003").displayName());
        assertEquals(global.total(), global.items().stream().mapToLong(ServiceCodeSpecStatisticsItem::total).sum());
        for (ServiceCodeSpecStatisticsItem item : global.items()) {
            assertEquals(item.total(), item.waiting() + item.expiring() + item.processing()
                    + item.consumed() + item.expired());
        }
        assertEquals(List.of(30, 30, 90), global.items().stream()
                .map(ServiceCodeSpecStatisticsItem::durationDays).toList());

        ServiceCodeSpecStatistics globalCompany10 = service.specStatistics(10L);
        assertEquals(2, globalCompany10.total());
        assertEquals(List.of("SC001"), globalCompany10.items().stream()
                .map(ServiceCodeSpecStatisticsItem::specCode).toList());

        asCompanyUser(10L, 2);
        assertEquals(2, service.specStatistics(null).total());
        BusinessException crossCompany = assertThrows(BusinessException.class,
                () -> service.specStatistics(20L));
        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, crossCompany.getVantixErrorCode());

        asCompanyUser(20L, 3);
        ServiceCodeSpecStatistics personal = service.specStatistics(null);
        assertEquals(4, personal.total());
        assertEquals(Set.of("SC002", "SC003"), personal.items().stream()
                .map(ServiceCodeSpecStatisticsItem::specCode).collect(Collectors.toSet()));
    }

    @Test
    void allScopesAreFailClosedAndOwnerFilterIsExplicit() {
        asGlobalUser();
        assertEquals(2, service.page(query(null, null, null, null, 10L)).total());

        asCompanyUser(10L, 2);
        assertEquals(2, service.page(query(null, null, null, null, null)).total());
        assertEquals(2, service.page(query(null, null, null, null, 10L)).total());
        BusinessException differentCompany = assertThrows(BusinessException.class,
                () -> service.page(query(null, null, null, null, 20L)));
        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, differentCompany.getVantixErrorCode());

        asCompanyUser(10L, 3);
        assertEquals(2, service.statistics(new ServiceCodeStatisticsQuery(null, null, null, null, null)).total());

        UserCacheDTO unsupported = new UserCacheDTO();
        unsupported.setUserId(88L);
        unsupported.setCompanyId(null);
        unsupported.setDataType(3);
        UserHolder.setUser(unsupported);
        BusinessException unsupportedException = assertThrows(BusinessException.class,
                () -> service.statistics(new ServiceCodeStatisticsQuery(null, null, null, null, null)));
        assertEquals(ErrorCode.UNSUPPORTED_USER_SCOPE, unsupportedException.getVantixErrorCode());
    }

    @Test
    void detailUsesJoinedSnapshotAndAuthorizationWithoutTransferHistory() {
        asGlobalUser();
        Long id = jdbc.queryForObject("SELECT id FROM service_code WHERE code = 'QUERY-EXPIRED'", Long.class);
        var detail = service.get(id);
        assertEquals("旧规格名称", detail.displayName());
        assertEquals("客户十", detail.ownerCompanyName());
        assertEquals(DisplayStatus.EXPIRED, detail.displayStatus());
        assertEquals(90, detail.durationDays());
        assertEquals("NTRIP", detail.serviceType());

        asCompanyUser(20L, 2);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.get(id));
        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
    }

    private ServiceCodePageQuery query(String keyword, String specCode, Integer durationDays,
                                       String sourceOrderNo, Long ownerCompanyId) {
        return new ServiceCodePageQuery(1, 20, keyword, null, null, specCode, durationDays,
                sourceOrderNo, ownerCompanyId);
    }

    private void insertCode(String code, String orderNo, Long ownerCompanyId, Long batchId,
                            String specCode, String serviceType, int durationDays,
                            LocalDateTime expireAt, ServiceCodeStatus status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO service_code "
                        + "(code, source_order_id, source_order_no, generate_batch_id, owner_company_id, spec_code, "
                        + "service_type, duration_days, code_silence_days, expire_at, status, processing_request_id, "
                        + "version, created_at, updated_at) VALUES (?, 100, ?, ?, ?, ?, ?, ?, 30, ?, ?, ?, 0, ?, ?)",
                code, orderNo, batchId, ownerCompanyId, specCode, serviceType, durationDays, expireAt,
                status.name(), status == ServiceCodeStatus.PROCESSING ? "PROCESS-" + code : null,
                createdAt, createdAt);
    }

    private Long insertSameDurationDifferentSpecBatch() {
        jdbc.update("INSERT INTO service_code_generate_batch "
                        + "(batch_no, request_id, generation_source, source_order_no, owner_company_id, spec_code, "
                        + "display_name, service_type, duration_days, code_silence_days, quantity, generated_count, "
                        + "status, business_key_hash, generate_order_id) VALUES "
                        + "('BATCH-30', 'QUERY-BATCH-3', 'INTERNAL', 'ORDER-20', 20, 'SC003', '另一规格', "
                        + "'CORS', 30, 15, 1, 1, 'COMPLETED', REPEAT('e', 64), 2)");
        return jdbc.queryForObject(
                "SELECT id FROM service_code_generate_batch WHERE batch_no = 'BATCH-30'", Long.class);
    }

    private Map<String, ServiceCodeSpecStatisticsItem> bySpec(ServiceCodeSpecStatistics statistics) {
        return statistics.items().stream().collect(Collectors.toMap(
                ServiceCodeSpecStatisticsItem::specCode, Function.identity()));
    }

    private void asGlobalUser() {
        asCompanyUser(null, 4);
    }

    private void asCompanyUser(Long companyId, int dataType) {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(88L);
        user.setUserNickname("query-user");
        user.setCompanyId(companyId);
        user.setDataType(dataType);
        UserHolder.setUser(user);
    }
}

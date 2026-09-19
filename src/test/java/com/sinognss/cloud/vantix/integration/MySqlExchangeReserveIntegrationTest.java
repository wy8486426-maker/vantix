package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.application.cors.CorsOperationRetryJob;
import com.sinognss.cloud.vantix.application.exchange.ExchangeReservation;
import com.sinognss.cloud.vantix.application.exchange.ExchangeQueryService;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeByCodesCommand;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeCommand;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeReserveService;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeService;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeView;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlExchangeReserveIntegrationTest {
    private static final long COMPANY_ID = 100L;
    private static final String SPEC_CODE = "M1";

    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_exchange_reserve");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ServiceCodeExchangeReserveService reserveService;

    @Autowired
    private ExchangeQueryService queryService;

    @Autowired
    private ServiceCodeExchangeService exchangeService;

    @Autowired
    private ExchangeDetailMapper detailMapper;

    @MockBean
    private CorsOperationRetryJob retryJob;

    @MockBean
    private CorsAccountGateway corsGateway;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM service_account");
        jdbc.update("DELETE FROM exchange_detail");
        jdbc.update("DELETE FROM cors_operation WHERE biz_type = 'EXCHANGE_BATCH'");
        jdbc.update("DELETE FROM exchange_batch");
        jdbc.update("DELETE FROM service_code");
        jdbc.update("DELETE FROM service_code_generate_batch");
        jdbc.update("DELETE FROM service_code_generate_order");
        jdbc.update("DELETE FROM service_duration_config");
        jdbc.update("DELETE FROM company_exchange_config");
        jdbc.update("DELETE FROM dealer_company");
        jdbc.update("INSERT INTO dealer_company (company_id, company_name, manager_id, company_status) "
                + "VALUES (?, 'exchange test company', 123, 'ACTIVE')", COMPANY_ID);
        jdbc.update("INSERT INTO company_exchange_config (company_id, account_prefix) VALUES (?, 'AB12')",
                COMPANY_ID);
        jdbc.update("INSERT INTO service_duration_config "
                        + "(service_type, display_name, duration_days, code_silence_days, account_silence_days, "
                        + "enabled, spec_code) VALUES ('CORS', '1个月', 30, 180, 360, 0, ?)",
                SPEC_CODE);
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void concurrentReservationsLockEligibleCodesAndKeepSourcesAndExpirySeparate() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1001L, "B2B-RESERVE-ORDER", "B2B-RESERVE-BATCH", 16);
        insertCodes("B2B-ELIG-", 1001L, 14, now.plusDays(180));
        List<Long> expiredCodeIds = insertCodes("B2B-EXP-", 1001L, 2, now.minusHours(1));
        insertGeneration("OFFLINE", 1002L, "OFFLINE-RESERVE-ORDER", "OFFLINE-RESERVE-BATCH", 5);
        insertCodes("OFFLINE-ELIG-", 1002L, 5, now.plusDays(180));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<ReserveAttempt>> futures;
        try {
            futures = List.of(
                    executor.submit(() -> reserveInWorker(start, "B2B-RESERVE-1", GenerationSource.B2B, 10)),
                    executor.submit(() -> reserveInWorker(start, "B2B-RESERVE-2", GenerationSource.B2B, 10)));
            start.countDown();

            List<ReserveAttempt> attempts = List.of(futures.get(0).get(), futures.get(1).get());
            List<ReserveAttempt> successes = attempts.stream().filter(ReserveAttempt::success).toList();
            List<ReserveAttempt> failures = attempts.stream().filter(attempt -> !attempt.success()).toList();
            assertEquals(1, successes.size());
            assertEquals(1, failures.size());
            assertEquals(ErrorCode.INSUFFICIENT_SERVICE_CODES, failures.get(0).errorCode());

            long b2bBatchId = successes.get(0).reservation().batchId();
            assertEquals(10, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail WHERE exchange_batch_id = ?",
                    Integer.class, b2bBatchId));
            assertEquals(10, jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT service_code_id) FROM exchange_detail WHERE exchange_batch_id = ?",
                    Integer.class, b2bBatchId));
            assertEquals(10, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail WHERE exchange_batch_id = ? "
                            + "AND active_service_code_id = service_code_id",
                    Integer.class, b2bBatchId));
            assertEquals(10, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail d "
                            + "JOIN service_code c ON c.id = d.service_code_id "
                            + "JOIN service_code_generate_batch g ON g.id = c.generate_batch_id "
                            + "WHERE d.exchange_batch_id = ? AND g.generation_source = 'B2B'",
                    Integer.class, b2bBatchId));
            assertEquals("B2B", jdbc.queryForObject(
                    "SELECT generation_source FROM exchange_batch WHERE id = ?", String.class, b2bBatchId));
            assertEquals("1个月", jdbc.queryForObject(
                    "SELECT display_name FROM exchange_batch WHERE id = ?", String.class, b2bBatchId));
            assertEquals(4, countAvailable("B2B", now));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_code WHERE code LIKE 'B2B-EXP-%' AND status = 'PENDING'",
                    Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail WHERE service_code_id IN (?, ?)",
                    Integer.class, expiredCodeIds.get(0), expiredCodeIds.get(1)));

            setGlobalUser();
            ExchangeReservation offline = reserveService.reserve(new ServiceCodeExchangeCommand(
                    "OFFLINE-RESERVE", COMPANY_ID, SPEC_CODE, GenerationSource.OFFLINE, 5));
            assertTrue(offline.created());
            assertEquals(5, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail WHERE exchange_batch_id = ?",
                    Integer.class, offline.batchId()));
            assertEquals(5, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail WHERE exchange_batch_id = ? "
                            + "AND active_service_code_id = service_code_id",
                    Integer.class, offline.batchId()));
            assertEquals(5, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail d "
                            + "JOIN service_code c ON c.id = d.service_code_id "
                            + "JOIN service_code_generate_batch g ON g.id = c.generate_batch_id "
                            + "WHERE d.exchange_batch_id = ? AND g.generation_source = 'OFFLINE'",
                    Integer.class, offline.batchId()));
            assertEquals("OFFLINE", jdbc.queryForObject(
                    "SELECT generation_source FROM exchange_batch WHERE id = ?",
                    String.class, offline.batchId()));
            assertEquals("1个月", jdbc.queryForObject(
                    "SELECT display_name FROM exchange_batch WHERE id = ?", String.class, offline.batchId()));
            assertEquals(0, countAvailable("OFFLINE", now));
            assertEquals(4, countAvailable("B2B", now));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_code WHERE code LIKE 'B2B-EXP-%' AND status = 'PENDING'",
                    Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void remoteGatewayRunsOnlyAfterReservationTransactionCommits() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1003L, "B2B-REMOTE-ORDER", "B2B-REMOTE-BATCH", 1);
        insertCodes("REMOTE-BOUNDARY-", 1003L, 1, now.plusDays(180));
        setGlobalUser();

        String requestId = "REMOTE-BOUNDARY-REQUEST";
        when(corsGateway.createBatch(any(CorsBatchCreateRequest.class))).thenAnswer(invocation -> {
            CorsBatchCreateRequest corsRequest = invocation.getArgument(0);
            assertNotEquals(requestId, corsRequest.requestId());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("PROCESSING", jdbc.queryForObject(
                    "SELECT status FROM service_code WHERE code = 'REMOTE-BOUNDARY-001'", String.class));
            assertEquals(requestId, jdbc.queryForObject(
                    "SELECT processing_request_id FROM service_code WHERE code = 'REMOTE-BOUNDARY-001'",
                    String.class));
            assertEquals("PROCESSING", jdbc.queryForObject(
                    "SELECT status FROM exchange_batch WHERE request_id = ?", String.class, requestId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail WHERE request_id = ? AND status = 'PROCESSING'",
                    Integer.class, requestId));
            return CorsBatchResult.outcome(CorsOutcome.UNKNOWN, corsRequest.requestId(), "TIMEOUT", "mock unknown");
        });

        ServiceCodeExchangeView response = exchangeService.exchange(new ServiceCodeExchangeCommand(
                requestId, COMPANY_ID, SPEC_CODE, GenerationSource.B2B, 1));

        assertEquals("PROCESSING", response.status());
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM exchange_batch WHERE request_id = ?", String.class, requestId));
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM service_code WHERE code = 'REMOTE-BOUNDARY-001'", String.class));
        assertEquals("RETRY_WAIT", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE biz_type = 'EXCHANGE_BATCH' "
                        + "AND biz_id = (SELECT id FROM exchange_batch WHERE request_id = ?)",
                String.class, requestId));
    }

    @Test
    void exactExchangeSupportsSingleAndBatchAndUsesOnlyRequestedCodes() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1010L, "EXACT-ORDER", "EXACT-BATCH", 3);
        List<Long> ids = insertCodes("EXACT-CODE-", 1010L, 3, now.plusDays(180));
        setGlobalUser();

        ExchangeReservation single = reserveService.reserveByCodes(
                new ServiceCodeExchangeByCodesCommand("EXACT-SINGLE", COMPANY_ID, List.of(ids.get(1))));
        assertTrue(single.created());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM exchange_detail WHERE exchange_batch_id = ?", Integer.class,
                single.batchId()));
        assertEquals(ids.get(1), jdbc.queryForObject(
                "SELECT service_code_id FROM exchange_detail WHERE exchange_batch_id = ?", Long.class,
                single.batchId()));
        assertEquals("EXACT", jdbc.queryForObject(
                "SELECT generation_source FROM exchange_batch WHERE id = ?", String.class, single.batchId()));

        ExchangeReservation batch = reserveService.reserveByCodes(
                new ServiceCodeExchangeByCodesCommand("EXACT-BATCH", COMPANY_ID,
                        List.of(ids.get(2), ids.get(0))));
        assertTrue(batch.created());
        assertEquals(List.of(ids.get(0), ids.get(2)), jdbc.queryForList(
                "SELECT service_code_id FROM exchange_detail WHERE exchange_batch_id = ? ORDER BY detail_index",
                Long.class, batch.batchId()));
        assertEquals(2, jdbc.queryForObject(
                "SELECT quantity FROM exchange_batch WHERE id = ?", Integer.class, batch.batchId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM exchange_detail WHERE exchange_batch_id = ? AND service_code_id = ?",
                Integer.class, batch.batchId(), ids.get(1)));
    }

    @Test
    void concurrentExactExchangeOfTheSameCodeSucceedsOnlyOnce() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1011L, "EXACT-CONCURRENT-ORDER", "EXACT-CONCURRENT-BATCH", 1);
        long serviceCodeId = insertCodes("EXACT-CONCURRENT-CODE-", 1011L, 1,
                now.plusDays(180)).get(0);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ReserveAttempt>> futures = List.of(
                    executor.submit(() -> reserveExactInWorker(start, "EXACT-CONCURRENT-1", serviceCodeId)),
                    executor.submit(() -> reserveExactInWorker(start, "EXACT-CONCURRENT-2", serviceCodeId)));
            start.countDown();

            List<ReserveAttempt> attempts = List.of(futures.get(0).get(), futures.get(1).get());
            assertEquals(1, attempts.stream().filter(ReserveAttempt::success).count());
            assertEquals(1, attempts.stream().filter(attempt -> !attempt.success()).count());
            assertEquals(ErrorCode.SERVICE_CODE_NOT_PENDING, attempts.stream()
                    .filter(attempt -> !attempt.success()).findFirst().orElseThrow().errorCode());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_code WHERE id = ? AND status = 'PROCESSING'",
                    Integer.class, serviceCodeId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exchange_detail WHERE service_code_id = ?",
                    Integer.class, serviceCodeId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void activeExchangeCodeUniqueKeyRejectsASecondProcessingDetailAndCompletedKeepsTheLock() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1007L, "B2B-ACTIVE-UNIQUE-ORDER", "B2B-ACTIVE-UNIQUE-BATCH", 1);
        long serviceCodeId = insertCodes("ACTIVE-UNIQUE-CODE-", 1007L, 1, now.plusDays(180)).get(0);
        setGlobalUser();

        ExchangeReservation first = reserveService.reserve(new ServiceCodeExchangeCommand(
                "ACTIVE-UNIQUE-FIRST", COMPANY_ID, SPEC_CODE, GenerationSource.B2B, 1));
        assertEquals(serviceCodeId, jdbc.queryForObject(
                "SELECT active_service_code_id FROM exchange_detail WHERE exchange_batch_id = ?",
                Long.class, first.batchId()));

        assertEquals(1, detailMapper.completeBatchDetails(first.batchId(),
                List.of(new ExchangeDetailMapper.CompletedAccountRow(1, "cors-active-1", "account-active-1")),
                now));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM exchange_detail WHERE exchange_batch_id = ?", String.class, first.batchId()));
        assertEquals(serviceCodeId, jdbc.queryForObject(
                "SELECT active_service_code_id FROM exchange_detail WHERE exchange_batch_id = ?",
                Long.class, first.batchId()));

        jdbc.update("INSERT INTO exchange_batch "
                        + "(exchange_batch_no, request_id, owner_company_id, generation_source, spec_code, "
                        + "display_name, service_type, duration_days, account_silence_days, quantity, payload_hash, status) "
                        + "VALUES ('EX-ACTIVE-UNIQUE-SECOND', 'ACTIVE-UNIQUE-SECOND', ?, 'B2B', ?, '1个月', "
                        + "'CORS', 30, 360, 1, ?, 'PROCESSING')",
                COMPANY_ID, SPEC_CODE, String.format("%064x", 1008L));
        Long secondBatchId = jdbc.queryForObject(
                "SELECT id FROM exchange_batch WHERE request_id = 'ACTIVE-UNIQUE-SECOND'", Long.class);

        assertThrows(DuplicateKeyException.class, () -> jdbc.update("INSERT INTO exchange_detail "
                        + "(exchange_batch_id, detail_index, service_code_id, request_id, service_code_snapshot, "
                        + "status, active_service_code_id) VALUES (?, 1, ?, 'ACTIVE-UNIQUE-SECOND', '{}', 'PROCESSING', ?)",
                secondBatchId, serviceCodeId, serviceCodeId));
    }

    @Test
    void definitiveRejectAllowsSameServiceCodeToBeReservedByANewRequest() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1004L, "B2B-REUSE-ORDER", "B2B-REUSE-BATCH", 1);
        long serviceCodeId = insertCodes("REUSE-CODE-", 1004L, 1, now.plusDays(180)).get(0);
        setGlobalUser();

        AtomicInteger attempts = new AtomicInteger();
        when(corsGateway.createBatch(any(CorsBatchCreateRequest.class))).thenAnswer(invocation -> {
            CorsBatchCreateRequest request = invocation.getArgument(0);
            return attempts.getAndIncrement() == 0
                    ? CorsBatchResult.outcome(CorsOutcome.DEFINITIVE_REJECT, request.requestId(),
                    "5302", "用户名称重复")
                    : CorsBatchResult.outcome(CorsOutcome.UNKNOWN, request.requestId(), "TIMEOUT", "unknown");
        });

        ServiceCodeExchangeView rejected = exchangeService.exchange(new ServiceCodeExchangeCommand(
                "REUSE-REQ-A", COMPANY_ID, SPEC_CODE, GenerationSource.B2B, 1));

        assertEquals("FAILED", rejected.status());
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM exchange_detail WHERE request_id = 'REUSE-REQ-A'", String.class));
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE biz_type = 'EXCHANGE_BATCH' "
                        + "AND biz_id = (SELECT id FROM exchange_batch WHERE request_id = 'REUSE-REQ-A')",
                String.class));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM service_code WHERE id = ?", String.class, serviceCodeId));
        assertNull(jdbc.queryForObject(
                "SELECT active_service_code_id FROM exchange_detail WHERE request_id = 'REUSE-REQ-A'", Long.class));

        ServiceCodeExchangeView retried = exchangeService.exchange(new ServiceCodeExchangeCommand(
                "REUSE-REQ-B", COMPANY_ID, SPEC_CODE, GenerationSource.B2B, 1));

        assertEquals("PROCESSING", retried.status());
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM service_code WHERE id = ?", String.class, serviceCodeId));
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM exchange_detail WHERE request_id = 'REUSE-REQ-A'", String.class));
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM exchange_detail WHERE request_id = 'REUSE-REQ-B'", String.class));
        assertEquals(serviceCodeId, jdbc.queryForObject(
                "SELECT active_service_code_id FROM exchange_detail WHERE request_id = 'REUSE-REQ-B'", Long.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM exchange_detail WHERE service_code_id = ?", Integer.class, serviceCodeId));
    }

    @Test
    void reservationUsesCompanyManagerAndQueryIgnoresCurrentUserScope() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1005L, "B2B-PERSONAL-ORDER", "B2B-PERSONAL-BATCH", 1);
        insertCodes("PERSONAL-CODE-", 1005L, 1, now.plusDays(180));
        setUnsupportedUser(88L);

        reserveService.reserve(new ServiceCodeExchangeCommand(
                "PERSONAL-OWNERSHIP", COMPANY_ID, SPEC_CODE, GenerationSource.B2B, 1));

        Long assignedUserId = jdbc.queryForObject(
                "SELECT assigned_user_id FROM exchange_batch WHERE request_id = ?", Long.class,
                "PERSONAL-OWNERSHIP");
        assertEquals(123L, assignedUserId);
        setUnsupportedUser(123L);
        assertEquals("PROCESSING", queryService.get("PERSONAL-OWNERSHIP").status());

        setUnsupportedUser(89L);
        assertEquals("PROCESSING", queryService.get("PERSONAL-OWNERSHIP").status());
    }

    @Test
    void sameRequestReplayKeepsOriginalExchangeDisplayNameAfterConfigChange() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        insertGeneration("B2B", 1006L, "B2B-SNAPSHOT-ORDER", "B2B-SNAPSHOT-BATCH", 1);
        insertCodes("SNAPSHOT-CODE-", 1006L, 1, now.plusDays(180));
        setGlobalUser();

        String requestId = "EXCHANGE-DISPLAY-SNAPSHOT";
        ExchangeReservation first = reserveService.reserve(new ServiceCodeExchangeCommand(
                requestId, COMPANY_ID, SPEC_CODE, GenerationSource.B2B, 1));
        jdbc.update("UPDATE service_duration_config SET display_name = '更新后规格' WHERE spec_code = ?", SPEC_CODE);

        ExchangeReservation retry = reserveService.reserve(new ServiceCodeExchangeCommand(
                requestId, COMPANY_ID, SPEC_CODE, GenerationSource.B2B, 1));

        assertTrue(first.created());
        assertFalse(retry.created());
        assertEquals(first.batchId(), retry.batchId());
        assertEquals("1个月", jdbc.queryForObject(
                "SELECT display_name FROM exchange_batch WHERE request_id = ?", String.class, requestId));
    }

    private ReserveAttempt reserveInWorker(CountDownLatch start, String requestId,
                                           GenerationSource source, int quantity) throws InterruptedException {
        start.await();
        setGlobalUser();
        try {
            ExchangeReservation reservation = reserveService.reserve(new ServiceCodeExchangeCommand(
                    requestId, COMPANY_ID, SPEC_CODE, source, quantity));
            return new ReserveAttempt(true, null, reservation);
        } catch (BusinessException exception) {
            return new ReserveAttempt(false, exception.getVantixErrorCode(), null);
        } finally {
            UserHolder.removeUser();
        }
    }

    private int countAvailable(String source, LocalDateTime now) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_code c "
                        + "JOIN service_code_generate_batch g ON g.id = c.generate_batch_id "
                        + "WHERE g.owner_company_id = ? AND g.generation_source = ? AND g.spec_code = ? "
                        + "AND c.status = 'PENDING' AND c.expire_at > ?",
                Integer.class, COMPANY_ID, source, SPEC_CODE, now);
    }

    private void insertGeneration(String source, long orderId, String orderNo,
                                  String batchNo, int quantity) {
        String hash = String.format("%064x", orderId);
        jdbc.update("INSERT INTO service_code_generate_order "
                        + "(id, request_id, generation_source, source_order_no, owner_company_id, payload_hash, "
                        + "item_count, total_quantity, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, ?, 'COMPLETED')",
                orderId, source + ":" + orderNo, source, orderNo, COMPANY_ID, hash, quantity);
        jdbc.update("INSERT INTO service_code_generate_batch "
                        + "(batch_no, request_id, generation_source, source_order_no, owner_company_id, spec_code, "
                        + "display_name, service_type, duration_days, code_silence_days, quantity, generated_count, status, "
                        + "business_key_hash, generate_order_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, '1个月', 'CORS', 30, 180, ?, ?, 'COMPLETED', ?, ?)",
                batchNo, source + ":" + batchNo, source, orderNo, COMPANY_ID, SPEC_CODE, quantity, quantity,
                String.format("%064x", orderId + 1000), orderId);
    }

    private List<Long> insertCodes(String prefix, long orderId, int quantity, LocalDateTime expireAt) {
        Long batchId = jdbc.queryForObject(
                "SELECT id FROM service_code_generate_batch WHERE generate_order_id = ?", Long.class, orderId);
        assertNotNull(batchId);
        for (int index = 0; index < quantity; index++) {
            jdbc.update("INSERT INTO service_code "
                            + "(code, generate_batch_id, owner_company_id, spec_code, service_type, duration_days, "
                            + "code_silence_days, expire_at, status, version) "
                            + "VALUES (?, ?, ?, 'M1', 'CORS', 30, 180, ?, 'PENDING', 0)",
                    prefix + String.format("%03d", index + 1), batchId, COMPANY_ID, expireAt);
        }
        return jdbc.queryForList("SELECT id FROM service_code WHERE code LIKE ? ORDER BY id",
                Long.class, prefix + "%");
    }

    private void setGlobalUser() {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(88L);
        user.setUserNickname("integration-user");
        user.setCompanyId(null);
        user.setDataType(4);
        UserHolder.setUser(user);
    }

    private void setPersonalUser(long userId) {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(userId);
        user.setUserNickname("personal-integration-user-" + userId);
        user.setCompanyId(COMPANY_ID);
        user.setDataType(3);
        UserHolder.setUser(user);
    }

    private void setUnsupportedUser(long userId) {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(userId);
        user.setUserNickname("unsupported-integration-user-" + userId);
        user.setCompanyId(COMPANY_ID);
        user.setDataType(99);
        UserHolder.setUser(user);
    }

    private ReserveAttempt reserveExactInWorker(CountDownLatch start, String requestId,
                                                long serviceCodeId) throws InterruptedException {
        start.await();
        setGlobalUser();
        try {
            ExchangeReservation reservation = reserveService.reserveByCodes(
                    new ServiceCodeExchangeByCodesCommand(requestId, COMPANY_ID, List.of(serviceCodeId)));
            return new ReserveAttempt(true, null, reservation);
        } catch (BusinessException exception) {
            return new ReserveAttempt(false, exception.getVantixErrorCode(), null);
        } finally {
            UserHolder.removeUser();
        }
    }
    private record ReserveAttempt(boolean success, ErrorCode errorCode, ExchangeReservation reservation) {
    }
}

package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.application.offline.OfflineImportValidationException;
import com.sinognss.cloud.vantix.application.offline.OfflineOrderImportService;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeItemCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeOrderCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateOrderView;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeOrderGenerateService;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeResult;
import com.sinognss.cloud.vantix.application.servicecode.generation.IntegrationActor;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateService;
import com.sinognss.cloud.vantix.common.ServiceCodeGenerator;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlGenerationIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_generation");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ServiceCodeGenerateService generateService;
    @Autowired
    private ServiceCodeOrderGenerateService orderGenerateService;
    @Autowired
    private ServiceDurationConfigMapper durationMapper;
    @Autowired
    private OfflineOrderImportService offlineImportService;
    @Autowired
    private ServiceCodeGenerator codeGenerator;
    @Autowired
    private GenerationProperties generationProperties;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void resetData() {
        generationProperties.setBatchInsertSize(500);
        jdbc.update("DELETE FROM service_code_transfer");
        jdbc.update("DELETE FROM service_code");
        jdbc.update("DELETE FROM service_code_generate_batch");
        jdbc.update("DELETE FROM service_code_generate_order");
        jdbc.update("DELETE FROM service_duration_config");
        jdbc.update("DELETE FROM dealer_company");
        jdbc.update("INSERT INTO dealer_company (company_id, company_name, company_status) "
                + "VALUES (100, 'test company', 'ACTIVE'), (200, 'other company', 'ACTIVE')");
        insertSpec("CORS", 30, "1个月", 180, true, "M1");
        insertSpec("CORS", 365, "1年", 360, false, "Y1");
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
        jdbc.execute("DROP TRIGGER IF EXISTS trg_test_fail_service_code");
        jdbc.execute("DROP TABLE IF EXISTS service_code_insert_counter");
    }

    @Test
    void flywayRunsSingleBaselineAndSpecsExposeEnabledConfigsOnly() {
        assertEquals(500, generationProperties.getBatchInsertSize());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
        List<ServiceDurationConfig> specs = durationMapper.selectEnabled();
        assertEquals(1, specs.size());
        assertEquals("M1", specs.get(0).getSpecCode());
        assertEquals(180, specs.get(0).getCodeSilenceDays());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'service_code' AND column_name = 'generate_batch_id'", Integer.class));
    }

    @Test
    void enabledSpecsHaveUniqueDurationDisplayNames() {
        insertSpec("SDK", 90, "3个月", 360, true, "M3");

        List<String> displayNames = durationMapper.selectEnabled().stream()
                .map(ServiceDurationConfig::getDisplayName)
                .toList();

        assertEquals(displayNames.size(), displayNames.stream().distinct().count());
        assertEquals(List.of("1个月", "3个月"), displayNames);
    }
    @Test
    void b2bGenerationPersistsSnapshotsAndSequentialRetriesAreIdempotent() {
        GenerateServiceCodeCommand command = command("B2B:REQ-1", "ORDER-1", 100L, "M1", 3);
        GenerateServiceCodeResult first = generateService.generate(command, IntegrationActor.B2B.operatorIdentity());

        assertFalse(first.idempotent());
        assertEquals(3, first.serviceCodes().size());
        assertEquals(3, first.serviceCodes().stream().distinct().count());
        assertTrue(first.serviceCodes().stream().allMatch(code ->
                code.matches("^VX[0-9]{6}[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{20}$")));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_code WHERE generate_batch_id = ?", Integer.class,
                jdbc.queryForObject("SELECT id FROM service_code_generate_batch WHERE batch_no = ?",
                        Long.class, first.batch().batchNo())));
        assertEquals(100L, jdbc.queryForObject(
                "SELECT owner_company_id FROM service_code WHERE generate_batch_id = ? LIMIT 1",
                Long.class, jdbc.queryForObject("SELECT id FROM service_code_generate_batch WHERE batch_no = ?",
                        Long.class, first.batch().batchNo())));
        assertEquals(3, jdbc.queryForObject(
                "SELECT generated_count FROM service_code_generate_batch WHERE batch_no = ?",
                Integer.class, first.batch().batchNo()));
        assertEquals("COMPLETED", first.batch().status());

        var snapshot = jdbc.queryForMap(
                "SELECT status, duration_days, code_silence_days, expire_at, created_at "
                        + "FROM service_code WHERE generate_batch_id = ? LIMIT 1",
                jdbc.queryForObject("SELECT id FROM service_code_generate_batch WHERE batch_no = ?",
                        Long.class, first.batch().batchNo()));
        assertEquals("PENDING", snapshot.get("status"));
        assertEquals(30, snapshot.get("duration_days"));
        assertEquals(180, snapshot.get("code_silence_days"));
        assertEquals(((java.time.LocalDateTime) snapshot.get("created_at")).plusDays(180),
                (java.time.LocalDateTime) snapshot.get("expire_at"));

        GenerateServiceCodeResult repeated = generateService.generate(command, IntegrationActor.B2B.operatorIdentity());
        GenerateServiceCodeResult differentRequestSameBusinessKey = generateService.generate(
                command("B2B:REQ-2", "ORDER-1", 100L, "M1", 3), IntegrationActor.B2B.operatorIdentity());

        assertTrue(repeated.idempotent());
        assertTrue(differentRequestSameBusinessKey.idempotent());
        assertEquals(first.batch().batchNo(), repeated.batch().batchNo());
        assertEquals(first.batch().batchNo(), differentRequestSameBusinessKey.batch().batchNo());

        BusinessException sameRequestDifferentQuantity = assertThrows(BusinessException.class,
                () -> generateService.generate(command("B2B:REQ-1", "ORDER-1", 100L, "M1", 90),
                        IntegrationActor.B2B.operatorIdentity()));
        assertEquals(com.sinognss.cloud.vantix.common.exception.ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT,
                sameRequestDifferentQuantity.getVantixErrorCode());

        BusinessException businessKeyDifferentQuantity = assertThrows(BusinessException.class,
                () -> generateService.generate(command("B2B:REQ-3", "ORDER-1", 100L, "M1", 90),
                        IntegrationActor.B2B.operatorIdentity()));
        assertEquals(com.sinognss.cloud.vantix.common.exception.ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT,
                businessKeyDifferentQuantity.getVantixErrorCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }

    @Test
    void databaseEnforcesSpecRequestBusinessAndCodeUniqueness() {
        GenerateServiceCodeResult generated = generateService.generate(
                command("B2B:UNIQUE", "ORDER-UNIQUE", 100L, "M1", 1), IntegrationActor.B2B.operatorIdentity());

        insertSpec("OTHER", 30, "另一个30天规格", 180, true, "OTHER-M1");
        assertThrows(DuplicateKeyException.class, () ->
                insertSpec("OTHER", 31, "另一个规格", 180, true, "M1"));
        assertThrows(DuplicateKeyException.class, () ->
                insertSpec("OTHER", 31, "1个月", 180, true, "OTHER-M2"));
        String hash = ServiceCodeGenerateService.businessKeyHash(
                GenerationSource.B2B, 100L, "ORDER-UNIQUE", "M1");
        String generatedRequestId = jdbc.queryForObject(
                "SELECT request_id FROM service_code_generate_batch WHERE batch_no = ?", String.class,
                generated.batch().batchNo());
        assertThrows(DuplicateKeyException.class, () -> insertBatch(
                "GB-REQUEST-DUP", generatedRequestId, "OTHER-ORDER", hash));
        assertThrows(DuplicateKeyException.class, () -> insertBatch(
                "GB-BUSINESS-DUP", "B2B:UNIQUE-2", "ORDER-UNIQUE", hash));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO service_code (code, owner_company_id, spec_code, service_type, duration_days, "
                        + "code_silence_days, expire_at) VALUES (?, 100, 'M1', 'CORS', 30, 180, ?)",
                generated.serviceCodes().get(0), LocalDateTime.now().plusDays(180)));

    }

    @Test
    void rejectsDisabledSpecMissingCompanyAndInvalidQuantity() {
        assertThrows(BusinessException.class,
                () -> generateService.generate(command("REQ-DISABLED", "O1", 100L, "Y1", 1),
                        IntegrationActor.B2B.operatorIdentity()));
        assertThrows(BusinessException.class,
                () -> generateService.generate(command("REQ-COMPANY", "O2", 999L, "M1", 1),
                        IntegrationActor.B2B.operatorIdentity()));
        assertThrows(BusinessException.class,
                () -> generateService.generate(command("REQ-QUANTITY", "O3", 100L, "M1", 0),
                        IntegrationActor.B2B.operatorIdentity()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }

    @Test
    void transactionRollsBackBatchAndEarlierCodesIfAnyCodeInsertFails() {
        jdbc.execute("CREATE TRIGGER trg_test_fail_service_code BEFORE INSERT ON service_code "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced failure'");

        assertThrows(DataAccessException.class, () -> generateService.generate(
                command("REQ-ROLLBACK", "ORDER-ROLLBACK", 100L, "M1", 2),
                IntegrationActor.B2B.operatorIdentity()));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }

    @Test
    void concurrentRequestsForSameBusinessKeyLeaveOnlyOneBatch() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> generateInWorker(start, "REQ-C1"));
            Future<?> second = executor.submit(() -> generateInWorker(start, "REQ-C2"));
            start.countDown();
            first.get();
            second.get();
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentBusinessKeyWithDifferentQuantitiesNeverOverGenerates() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> generateInWorker(start, "REQ-CQ1", 2));
            Future<?> second = executor.submit(() -> generateInWorker(start, "REQ-CQ2", 5));
            start.countDown();
            first.get();
            second.get();

            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
            int persistedQuantity = jdbc.queryForObject(
                    "SELECT quantity FROM service_code_generate_batch LIMIT 1", Integer.class);
            assertTrue(persistedQuantity == 2 || persistedQuantity == 5);
            assertEquals(persistedQuantity, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_code", Integer.class));
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class) < 7);
        } finally {
            executor.shutdownNow();
        }
    }
    @Test
    void offlineExcelImportIsCompanyScopedIdempotentAndAllOrNothing() throws Exception {
        asGlobalUser();
        insertSpec("CORS", 7, "1周", 0, true, "W1");
        byte[] validFile = xlsx(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("OFF-001", "1个月", "2", "2026-09-12T18:09:22", "first"),
                List.of("OFF-001", "1周", "2", "2026-09-12T18:09:22", "second")));
        MockMultipartFile upload = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", validFile);

        var imported = offlineImportService.importFile(100L, upload);
        assertEquals(2, imported.batchCount());
        assertEquals(4, imported.generatedCount());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
        OfflineImportValidationException duplicate = assertThrows(OfflineImportValidationException.class,
                () -> offlineImportService.importFile(100L, upload));
        assertTrue(duplicate.getErrors().get(0).message().contains("已经导入"));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));

        jdbc.update("DELETE FROM service_code");
        jdbc.update("DELETE FROM service_code_generate_batch");
        jdbc.update("DELETE FROM service_code_generate_order");
        byte[] invalidFile = xlsx(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("OFF-002", "1个月", "1", "", ""),
                List.of("OFF-003", "1年", "1", "", "")));
        MockMultipartFile invalidUpload = new MockMultipartFile("file", "invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", invalidFile);
        assertThrows(OfflineImportValidationException.class,
                () -> offlineImportService.importFile(100L, invalidUpload));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }

    @Test
    void multiSpecOrdersPersistCorrectChunkedCountsAndUniqueCodes() {
        insertSpec("CORS", 1, "1天", 0, true, "D1");
        GenerateServiceCodeOrderCommand command = new GenerateServiceCodeOrderCommand(
                GenerationSource.B2B, "B2B:ORDER-CHUNKED", "ORDER-CHUNKED",
                null, 100L, List.of(new GenerateServiceCodeItemCommand("D1", 600, null),
                        new GenerateServiceCodeItemCommand("M1", 700, null)));

        ServiceCodeGenerateOrderView result = orderGenerateService.generate(
                command, IntegrationActor.B2B.operatorIdentity());

        assertEquals(1300, result.totalQuantity());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_order", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(600, jdbc.queryForObject("SELECT COUNT(*) FROM service_code c "
                + "JOIN service_code_generate_batch b ON b.id = c.generate_batch_id "
                + "WHERE b.source_order_no = 'ORDER-CHUNKED' AND b.spec_code = 'D1'", Integer.class));
        assertEquals(700, jdbc.queryForObject("SELECT COUNT(*) FROM service_code c "
                + "JOIN service_code_generate_batch b ON b.id = c.generate_batch_id "
                + "WHERE b.source_order_no = 'ORDER-CHUNKED' AND b.spec_code = 'M1'", Integer.class));
        assertEquals(1300, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT code) FROM service_code", Integer.class));
        assertEquals(600, jdbc.queryForObject("SELECT generated_count FROM service_code_generate_batch "
                + "WHERE source_order_no = 'ORDER-CHUNKED' AND spec_code = 'D1'", Integer.class));
        assertEquals(700, jdbc.queryForObject("SELECT generated_count FROM service_code_generate_batch "
                + "WHERE source_order_no = 'ORDER-CHUNKED' AND spec_code = 'M1'", Integer.class));
    }

    @Test
    void mysql57PersistsThreeThousandCodesAcrossThreeSpecs() {
        insertSpec("CORS", 1, "1天", 0, true, "D1");
        jdbc.update("UPDATE service_duration_config SET enabled = 1 WHERE spec_code = 'Y1'");
        GenerateServiceCodeOrderCommand command = new GenerateServiceCodeOrderCommand(
                GenerationSource.B2B, "B2B:ORDER-3000", "ORDER-3000",
                null, 100L, List.of(new GenerateServiceCodeItemCommand("D1", 1000, null),
                        new GenerateServiceCodeItemCommand("M1", 1000, null),
                        new GenerateServiceCodeItemCommand("Y1", 1000, null)));

        ServiceCodeGenerateOrderView result = orderGenerateService.generate(
                command, IntegrationActor.B2B.operatorIdentity());

        assertEquals(3000, result.totalQuantity());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_order", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(3000, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
        assertEquals(3000, jdbc.queryForObject("SELECT COUNT(DISTINCT code) FROM service_code", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(DISTINCT spec_code) "
                + "FROM service_code_generate_batch", Integer.class));
        assertEquals(3000, jdbc.queryForObject("SELECT SUM(generated_count) "
                + "FROM service_code_generate_batch", Integer.class));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM service_code_generate_order LIMIT 1", String.class));
    }

    @Test
    void failureInFinalChunkRollsBackEarlierChunksAndTheWholeOrder() {
        jdbc.execute("CREATE TABLE service_code_insert_counter "
                + "(id INT NOT NULL PRIMARY KEY, insert_count INT NOT NULL) ENGINE=MyISAM");
        jdbc.update("INSERT INTO service_code_insert_counter (id, insert_count) VALUES (1, 0)");
        jdbc.execute("CREATE TRIGGER trg_test_fail_service_code BEFORE INSERT ON service_code "
                + "FOR EACH ROW BEGIN "
                + "DECLARE inserted_count INT DEFAULT 0; "
                + "UPDATE service_code_insert_counter SET insert_count = insert_count + 1 WHERE id = 1; "
                + "SELECT insert_count INTO inserted_count FROM service_code_insert_counter WHERE id = 1; "
                + "IF inserted_count > 1000 THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced final chunk failure'; "
                + "END IF; END");

        GenerateServiceCodeOrderCommand command = new GenerateServiceCodeOrderCommand(
                GenerationSource.B2B, "B2B:ORDER-ROLLBACK-LAST", "ORDER-ROLLBACK-LAST",
                null, 100L, List.of(new GenerateServiceCodeItemCommand("M1", 1001, null)));

        assertThrows(DataAccessException.class, () -> orderGenerateService.generate(
                command, IntegrationActor.B2B.operatorIdentity()));

        assertEquals(1001, jdbc.queryForObject(
                "SELECT insert_count FROM service_code_insert_counter WHERE id = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }

    @Test
    void oneRequestGeneratesOneMultiSpecOrderAtomicallyAndReplaysByPayload() {
        insertSpec("CORS", 1, "1天", 0, true, "D1");
        insertSpec("CORS", 7, "1周", 0, true, "W1");
        jdbc.update("UPDATE service_duration_config SET enabled = 1 WHERE spec_code = 'Y1'");
        GenerateServiceCodeOrderCommand command = new GenerateServiceCodeOrderCommand(
                GenerationSource.B2B, "B2B:ORDER-MULTI", "ORDER-MULTI",
                LocalDateTime.parse("2026-09-12T18:09:22"), 100L,
                List.of(new GenerateServiceCodeItemCommand("D1", 6, null),
                        new GenerateServiceCodeItemCommand("W1", 5, null),
                        new GenerateServiceCodeItemCommand("M1", 6, null),
                        new GenerateServiceCodeItemCommand("Y1", 2, null)));

        ServiceCodeGenerateOrderView first = orderGenerateService.generate(
                command, IntegrationActor.B2B.operatorIdentity());

        assertFalse(first.idempotent());
        assertEquals(4, first.itemCount());
        assertEquals(19, first.totalQuantity());
        assertEquals(List.of("D1", "M1", "W1", "Y1"),
                first.items().stream().map(item -> item.specCode()).toList());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_order", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(19, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT generate_order_id) FROM service_code_generate_batch", Integer.class));

        ServiceCodeGenerateOrderView sameRequest = orderGenerateService.generate(
                command, IntegrationActor.B2B.operatorIdentity());
        ServiceCodeGenerateOrderView sameOrderDifferentRequest = orderGenerateService.generate(
                new GenerateServiceCodeOrderCommand(GenerationSource.B2B, "B2B:ORDER-MULTI-RETRY",
                        "ORDER-MULTI", command.orderTime(), 100L, command.items()),
                IntegrationActor.B2B.operatorIdentity());
        assertTrue(sameRequest.idempotent());
        assertTrue(sameOrderDifferentRequest.idempotent());
        assertEquals(first.items().stream().map(item -> item.batchNo()).toList(),
                sameRequest.items().stream().map(item -> item.batchNo()).toList());

        assertThrows(BusinessException.class, () -> orderGenerateService.generate(
                new GenerateServiceCodeOrderCommand(GenerationSource.B2B, "B2B:ORDER-MULTI-CHANGED",
                        "ORDER-MULTI", command.orderTime(), 100L,
                        List.of(new GenerateServiceCodeItemCommand("D1", 7, null),
                                new GenerateServiceCodeItemCommand("W1", 5, null),
                                new GenerateServiceCodeItemCommand("M1", 6, null),
                                new GenerateServiceCodeItemCommand("Y1", 2, null))),
                IntegrationActor.B2B.operatorIdentity()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_order", Integer.class));
        assertEquals(19, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }
    @Test
    void offlineFileRollsBackAllOrdersWhenAnyServiceCodeInsertFails() throws Exception {
        asGlobalUser();
        insertSpec("CORS", 7, "1周", 0, true, "W1");
        jdbc.execute("CREATE TRIGGER trg_test_fail_service_code BEFORE INSERT ON service_code "
                + "FOR EACH ROW BEGIN IF NEW.source_order_no = 'OFF-FAIL' THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced failure'; END IF; END");
        MockMultipartFile upload = new MockMultipartFile("file", "rollback.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx(List.of(
                        List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                        List.of("OFF-FIRST", "1个月", "2", "", ""),
                        List.of("OFF-FAIL", "1周", "1", "", ""))));

        assertThrows(DataAccessException.class, () -> offlineImportService.importFile(100L, upload));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_generate_batch", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }
    private Object generateInWorker(CountDownLatch start, String requestId) {
        return generateInWorker(start, requestId, 2);
    }

    private Object generateInWorker(CountDownLatch start, String requestId, int quantity) {
        try {
            start.await();
            generateService.generate(command(requestId, "ORDER-CONCURRENT", 100L, "M1", quantity),
                    IntegrationActor.B2B.operatorIdentity());
        } catch (RuntimeException exception) {
            // A competing request may lose the unique-key race; the database is the assertion boundary.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        return null;
    }

    private GenerateServiceCodeCommand command(String requestId, String orderNo, Long companyId,
                                               String specCode, int quantity) {
        return new GenerateServiceCodeCommand(GenerationSource.B2B, requestId, orderNo, null,
                companyId, specCode, quantity, null);
    }

    private void insertSpec(String serviceType, int durationDays, String displayName, int codeSilenceDays,
                            boolean enabled, String specCode) {
        jdbc.update("INSERT INTO service_duration_config "
                        + "(service_type, display_name, duration_days, code_silence_days, account_silence_days, "
                        + "enabled, spec_code) VALUES (?, ?, ?, ?, ?, ?, ?)",
                serviceType, displayName, durationDays, codeSilenceDays, codeSilenceDays, enabled, specCode);
    }

    private void insertBatch(String batchNo, String requestId, String orderNo, String hash) {
        jdbc.update("INSERT INTO service_code_generate_batch "
                        + "(batch_no, request_id, generation_source, source_order_no, owner_company_id, spec_code, "
                        + "display_name, service_type, duration_days, code_silence_days, quantity, status, "
                        + "business_key_hash, generate_order_id) "
                        + "VALUES (?, ?, 'B2B', ?, 100, 'M1', '1个月', 'CORS', 30, 180, 1, 'COMPLETED', ?, 9223372036854770000)",
                batchNo, requestId, orderNo, hash);
    }

    private byte[] xlsx(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("线下订单导入");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex);
                for (int column = 0; column < rows.get(rowIndex).size(); column++) {
                    row.createCell(column).setCellValue(rows.get(rowIndex).get(column));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void asGlobalUser() {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(88L);
        user.setUserNickname("integration-user");
        user.setCompanyId(null);
        user.setDataType(4);
        UserHolder.setUser(user);
    }
}

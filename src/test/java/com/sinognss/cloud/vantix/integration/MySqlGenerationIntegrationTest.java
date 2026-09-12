package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.application.offline.OfflineImportValidationException;
import com.sinognss.cloud.vantix.application.offline.OfflineOrderImportService;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeResult;
import com.sinognss.cloud.vantix.application.servicecode.generation.IntegrationActor;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateService;
import com.sinognss.cloud.vantix.common.ServiceCodeGenerator;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MySqlGenerationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("vantix")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ServiceCodeGenerateService generateService;
    @Autowired
    private ServiceDurationConfigMapper durationMapper;
    @Autowired
    private OfflineOrderImportService offlineImportService;
    @Autowired
    private ServiceCodeGenerator codeGenerator;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM service_code_transfer");
        jdbc.update("DELETE FROM service_code");
        jdbc.update("DELETE FROM service_code_generate_batch");
        jdbc.update("DELETE FROM service_duration_config");
        jdbc.update("DELETE FROM dealer_company");
        jdbc.update("INSERT INTO dealer_company (company_id, company_name, company_status) "
                + "VALUES (100, 'test company', 'ACTIVE'), (200, 'other company', 'ACTIVE')");
        insertSpec("CORS", 1, "MONTH", 6, true, "M1");
        insertSpec("CORS", 1, "YEAR", 12, false, "Y1");
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
        jdbc.execute("DROP TRIGGER IF EXISTS trg_test_fail_service_code");
    }

    @Test
    void flywayV1V2V3RunAndSpecsExposeEnabledConfigsOnly() {
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
        List<ServiceDurationConfig> specs = durationMapper.selectEnabled();
        assertEquals(1, specs.size());
        assertEquals("M1", specs.get(0).getSpecCode());
        assertEquals(6, specs.get(0).getCodeSilenceMonths());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'service_code' AND column_name = 'generate_batch_id'", Integer.class));
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
                "SELECT status, duration_value, duration_unit, code_silence_months, expire_at, created_at "
                        + "FROM service_code WHERE generate_batch_id = ? LIMIT 1",
                jdbc.queryForObject("SELECT id FROM service_code_generate_batch WHERE batch_no = ?",
                        Long.class, first.batch().batchNo()));
        assertEquals("PENDING", snapshot.get("status"));
        assertEquals(1, snapshot.get("duration_value"));
        assertEquals("MONTH", snapshot.get("duration_unit"));
        assertEquals(6, snapshot.get("code_silence_months"));
        assertEquals(((java.sql.Timestamp) snapshot.get("created_at")).toLocalDateTime().plusMonths(6),
                ((java.sql.Timestamp) snapshot.get("expire_at")).toLocalDateTime());

        GenerateServiceCodeResult repeated = generateService.generate(command, IntegrationActor.B2B.operatorIdentity());
        GenerateServiceCodeResult differentRequestSameBusinessKey = generateService.generate(
                command("B2B:REQ-2", "ORDER-1", 100L, "M1", 3), IntegrationActor.B2B.operatorIdentity());

        assertTrue(repeated.idempotent());
        assertTrue(differentRequestSameBusinessKey.idempotent());
        assertEquals(first.batch().batchNo(), repeated.batch().batchNo());
        assertEquals(first.batch().batchNo(), differentRequestSameBusinessKey.batch().batchNo());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
    }

    @Test
    void databaseEnforcesSpecRequestBusinessAndCodeUniquenessAndImmutableIdentifiers() {
        GenerateServiceCodeResult generated = generateService.generate(
                command("B2B:UNIQUE", "ORDER-UNIQUE", 100L, "M1", 1), IntegrationActor.B2B.operatorIdentity());

        assertThrows(DuplicateKeyException.class, () -> insertSpec("OTHER", 1, "MONTH", 1, true, "M1"));
        String hash = ServiceCodeGenerateService.businessKeyHash(
                GenerationSource.B2B, 100L, "ORDER-UNIQUE", "M1");
        assertThrows(DuplicateKeyException.class, () -> insertBatch(
                "GB-REQUEST-DUP", "B2B:UNIQUE", "OTHER-ORDER", hash + "1"));
        assertThrows(DuplicateKeyException.class, () -> insertBatch(
                "GB-BUSINESS-DUP", "B2B:UNIQUE-2", "ORDER-UNIQUE", hash));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO service_code (code, owner_company_id, service_type, duration_value, duration_unit, "
                        + "code_silence_months, expire_at) VALUES (?, 100, 'CORS', 1, 'MONTH', 6, ?)",
                generated.serviceCodes().get(0), LocalDateTime.now().plusMonths(6)));

        jdbc.update("UPDATE service_duration_config SET spec_code = 'CHANGED' WHERE spec_code = 'M1'");
        assertEquals("M1", jdbc.queryForObject(
                "SELECT spec_code FROM service_duration_config WHERE service_type = 'CORS' AND duration_unit = 'MONTH'",
                String.class));
        jdbc.update("UPDATE service_code SET code = 'CHANGED' WHERE code = ?", generated.serviceCodes().get(0));
        assertEquals(generated.serviceCodes().get(0), jdbc.queryForObject(
                "SELECT code FROM service_code WHERE generate_batch_id = "
                        + "(SELECT id FROM service_code_generate_batch WHERE batch_no = ?) LIMIT 1",
                String.class, generated.batch().batchNo()));
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
    void offlineExcelImportIsCompanyScopedIdempotentAndAllOrNothing() throws Exception {
        asGlobalUser();
        byte[] validFile = xlsx(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("OFF-001", "1个月", "2", "2026-09-12T18:09:22", "first")));
        MockMultipartFile upload = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", validFile);

        var imported = offlineImportService.importFile(100L, upload);
        assertEquals(1, imported.batchCount());
        assertEquals(2, imported.generatedCount());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));
        OfflineImportValidationException duplicate = assertThrows(OfflineImportValidationException.class,
                () -> offlineImportService.importFile(100L, upload));
        assertTrue(duplicate.getErrors().get(0).message().contains("已经导入"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM service_code", Integer.class));

        jdbc.update("DELETE FROM service_code");
        jdbc.update("DELETE FROM service_code_generate_batch");
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

    private Object generateInWorker(CountDownLatch start, String requestId) {
        try {
            start.await();
            generateService.generate(command(requestId, "ORDER-CONCURRENT", 100L, "M1", 2),
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

    private void insertSpec(String serviceType, int duration, String unit, int silence,
                            boolean enabled, String specCode) {
        jdbc.update("INSERT INTO service_duration_config "
                        + "(service_type, duration_value, duration_unit, code_silence_months, enabled, spec_code) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                serviceType, duration, unit, silence, enabled, specCode);
    }

    private void insertBatch(String batchNo, String requestId, String orderNo, String hash) {
        jdbc.update("INSERT INTO service_code_generate_batch "
                        + "(batch_no, request_id, generation_source, source_order_no, owner_company_id, spec_code, "
                        + "duration_value, duration_unit, code_silence_months, quantity, status, business_key_hash) "
                        + "VALUES (?, ?, 'B2B', ?, 100, 'M1', 1, 'MONTH', 6, 1, 'COMPLETED', ?)",
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
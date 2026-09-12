package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeTransferService;
import com.sinognss.cloud.vantix.application.servicecode.TransferResult;
import com.sinognss.cloud.vantix.application.servicecode.TransferServiceCodeCommand;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MySqlTransferIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("vantix")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ServiceCodeMapper codeMapper;

    @Autowired
    private ServiceCodeTransferService transferService;

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
        jdbc.update("DELETE FROM dealer_relation_log");
        jdbc.update("DELETE FROM dealer_company");
        jdbc.update("DELETE FROM system_config");
        jdbc.update("INSERT INTO dealer_company (company_id, company_name, parent_company_id, company_status) VALUES (10, 'root', NULL, 'ACTIVE'), (20, 'child', 10, 'ACTIVE')");
        jdbc.update("INSERT INTO system_config (config_key, config_value) VALUES ('SYSTEM_COMPANY_ID', '999')");
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void flywayRunsAllMigrationsAndBatchTransferStoresTwoRows() {
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));

        ServiceCode first = insertCode("MYSQL-BATCH-1", 10L, ServiceCodeStatus.PENDING);
        ServiceCode second = insertCode("MYSQL-BATCH-2", 10L, ServiceCodeStatus.PENDING);
        asGlobalUser();

        TransferResult result = transferService.transfer(new TransferServiceCodeCommand(
                10L, 20L, List.of(first.getId(), second.getId()), "真实批量测试"));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT transfer_no, service_code_id, reason FROM service_code_transfer WHERE transfer_no = ?",
                result.transferNo());
        assertEquals(2, rows.size());
        assertEquals(2, rows.stream().map(row -> row.get("service_code_id")).distinct().count());
        assertEquals(1, rows.stream().map(row -> row.get("transfer_no")).distinct().count());
        assertEquals(2, rows.stream().map(row -> row.get("reason")).filter("真实批量测试"::equals).count());
        assertEquals(20L, jdbc.queryForObject("SELECT owner_company_id FROM service_code WHERE id = ?",
                Long.class, first.getId()));
    }

    @Test
    void v2AllowsSameTransferNoForDifferentCodes() {
        jdbc.update("INSERT INTO service_code_transfer (transfer_no, service_code_id, service_code, from_company_id, to_company_id, transfer_type) VALUES ('TR-SAME', 1001, 'A', 10, 20, 'PARENT_CHILD')");
        jdbc.update("INSERT INTO service_code_transfer (transfer_no, service_code_id, service_code, from_company_id, to_company_id, transfer_type) VALUES ('TR-SAME', 1002, 'B', 10, 20, 'PARENT_CHILD')");

        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_code_transfer WHERE transfer_no = 'TR-SAME'", Integer.class));
    }

    @Test
    void invalidCodeRollsBackWholeBatch() {
        ServiceCode valid = insertCode("MYSQL-ROLLBACK-1", 10L, ServiceCodeStatus.PENDING);
        ServiceCode consumed = insertCode("MYSQL-ROLLBACK-2", 10L, ServiceCodeStatus.CONSUMED);
        asGlobalUser();

        assertThrows(BusinessException.class, () -> transferService.transfer(new TransferServiceCodeCommand(
                10L, 20L, List.of(valid.getId(), consumed.getId()), "必须回滚")));

        assertEquals(10L, jdbc.queryForObject("SELECT owner_company_id FROM service_code WHERE id = ?",
                Long.class, valid.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_code_transfer", Integer.class));
    }

    @Test
    void concurrentWorkersAllowAtMostOneTransferAndIncrementVersionOnce() throws Exception {
        ServiceCode code = insertCode("MYSQL-CONCURRENT", 10L, ServiceCodeStatus.PENDING);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> transferInWorker(start, code.getId()));
            Future<Boolean> second = executor.submit(() -> transferInWorker(start, code.getId()));
            start.countDown();

            int successful = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successful);
            assertEquals(20L, jdbc.queryForObject("SELECT owner_company_id FROM service_code WHERE id = ?",
                    Long.class, code.getId()));
            assertEquals(1L, jdbc.queryForObject("SELECT version FROM service_code WHERE id = ?",
                    Long.class, code.getId()));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_code_transfer WHERE service_code_id = ?", Integer.class, code.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean transferInWorker(CountDownLatch start, Long codeId) throws InterruptedException {
        start.await();
        asGlobalUser();
        try {
            transferService.transfer(new TransferServiceCodeCommand(10L, 20L, List.of(codeId), "并发"));
            return true;
        } catch (BusinessException exception) {
            return false;
        } finally {
            UserHolder.removeUser();
        }
    }

    private ServiceCode insertCode(String value, Long ownerCompanyId, ServiceCodeStatus status) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        ServiceCode code = new ServiceCode();
        code.setCode(value);
        code.setOwnerCompanyId(ownerCompanyId);
        code.setServiceType("CORS");
        code.setDurationValue(1);
        code.setDurationUnit("MONTH");
        code.setCodeSilenceMonths(12);
        code.setExpireAt(now.plusMonths(12));
        code.setStatus(status);
        code.setVersion(0L);
        code.setCreatedAt(now);
        code.setUpdatedAt(now);
        codeMapper.insert(code);
        return code;
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

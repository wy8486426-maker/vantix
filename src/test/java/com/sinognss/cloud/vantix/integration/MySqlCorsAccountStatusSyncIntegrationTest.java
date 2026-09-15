package com.sinognss.cloud.vantix.integration;

import com.sinognss.cloud.vantix.application.cors.account.CorsMySqlAccountStatusSyncJob;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsMySqlAccountStatusGateway;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlCorsAccountStatusSyncIntegrationTest {
    private static final LocalMySqlTestDatabase VANTIX = LocalMySqlTestDatabase.create("vantix_cors_sync");
    private static final LocalMySqlTestDatabase CORS = LocalMySqlTestDatabase.create("cors_userinfo");

    @Autowired private JdbcTemplate vantixJdbc;
    @Autowired private CorsMySqlAccountStatusGateway gateway;
    @Autowired private CorsMySqlAccountStatusSyncJob job;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", VANTIX::getJdbcUrl);
        registry.add("spring.datasource.username", VANTIX::getUsername);
        registry.add("spring.datasource.password", VANTIX::getPassword);
        registry.add("vantix.cors-db.enabled", () -> "true");
        registry.add("vantix.cors-db.jdbc-url", CORS::getJdbcUrl);
        registry.add("vantix.cors-db.username", CORS::getUsername);
        registry.add("vantix.cors-db.password", CORS::getPassword);
        registry.add("vantix.cors-db.status-sync.enabled", () -> "true");
    }

    @BeforeAll
    static void migrateAndCreateFixtures() {
        Flyway.configure().dataSource(VANTIX.getJdbcUrl(), VANTIX.getUsername(), VANTIX.getPassword())
                .locations("classpath:db/migration").load().migrate();
        JdbcTemplate corsJdbc = new JdbcTemplate(new DriverManagerDataSource(CORS.getJdbcUrl(), CORS.getUsername(), CORS.getPassword()));
        corsJdbc.execute("CREATE TABLE userinfo (ID BIGINT PRIMARY KEY, name VARCHAR(128) NOT NULL, "
                + "password VARCHAR(128), secret VARCHAR(128), phone VARCHAR(32), email VARCHAR(128), "
                + "currentIP VARCHAR(64), active_status INT, account_status INT, active_time DATETIME, "
                + "expiredate DATETIME, lastupdatetime DATETIME)");
        corsJdbc.update("INSERT INTO userinfo (ID, name, password, secret, active_status, account_status, "
                        + "active_time, expiredate, lastupdatetime) VALUES (501, 'cors-account-501', ?, ?, 0, 0, ?, ?, ?)",
                "SHOULD_NEVER_BE_READ", "SHOULD_NEVER_BE_READ",
                LocalDateTime.of(2026, 9, 15, 8, 0), LocalDateTime.of(2026, 10, 15, 8, 0),
                LocalDateTime.of(2026, 9, 15, 9, 0));
    }

    @AfterAll
    static void closeDatabases() {
        CORS.close();
        VANTIX.close();
    }

    @Test
    void readsOnlyTheStatusProjectionAndAppliesItToThePrimaryDatabase() {
        vantixJdbc.update("INSERT INTO service_code (id, code, owner_company_id, service_type, duration_value, "
                        + "duration_unit, code_silence_months, expire_at, status, version) VALUES "
                        + "(70001, 'SYNC-CODE-70001', 1, 'CORS', 1, 'MONTH', 6, ?, 'PENDING', 0)",
                LocalDateTime.of(2027, 1, 1, 0, 0));
        vantixJdbc.update("INSERT INTO service_account (id, cors_account_id, account, owner_company_id, "
                        + "source_service_code_id, service_type, duration_value, duration_unit, account_silence_months, "
                        + "cors_status, cors_activation_status, version) VALUES "
                        + "(71001, '501', 'cors-account-501', 1, 70001, 'CORS', 1, 'MONTH', 6, "
                        + "'DISABLED', 'WAITING_ACTIVATION', 0)");

        CorsAccountStatusResult result = gateway.getAccount("501");
        assertNotNull(result.snapshot());
        assertEquals("ACTIVE", result.snapshot().activationStatus());
        assertEquals("ENABLED", result.snapshot().accountStatus());

        job.sync();

        assertEquals("ENABLED", vantixJdbc.queryForObject(
                "SELECT cors_status FROM service_account WHERE id = 71001", String.class));
        assertEquals("ACTIVE", vantixJdbc.queryForObject(
                "SELECT cors_activation_status FROM service_account WHERE id = 71001", String.class));
        assertEquals("2026-10-15 08:00:00.000000", vantixJdbc.queryForObject(
                "SELECT DATE_FORMAT(expire_at, '%Y-%m-%d %H:%i:%s.%f') FROM service_account WHERE id = 71001",
                String.class));
    }
}

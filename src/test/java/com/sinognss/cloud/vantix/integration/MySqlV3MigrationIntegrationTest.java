package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
class MySqlV3MigrationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("vantix_migration")
            .withUsername("root")
            .withPassword("test");

    @Test
    void v3BackfillsStableUniqueCodesForExistingDurationRows() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("2")).load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO service_duration_config "
                        + "(service_type, duration_value, duration_unit, code_silence_months, enabled) "
                        + "VALUES ('CORS', 1, 'MONTH', 6, 1), ('SDK', 1, 'MONTH', 12, 1), ('CORS', 1, 'DAY', 0, 1)");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load().migrate();

        List<String> codes = jdbc.queryForList(
                "SELECT spec_code FROM service_duration_config ORDER BY duration_unit, service_type", String.class);
        assertEquals(3, codes.size());
        assertEquals(3, codes.stream().distinct().count());
        assertNotNull(jdbc.queryForObject(
                "SELECT spec_code FROM service_duration_config WHERE service_type = 'CORS' AND duration_unit = 'DAY'",
                String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_duration_config WHERE spec_code = 'D1'", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_duration_config WHERE spec_code LIKE 'M1-S%'", Integer.class));
    }
}
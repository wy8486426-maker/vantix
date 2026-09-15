package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlV9DurationDaysMigrationIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v9_duration_days");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Test
    void addsDaysSchemaWithoutBackfillingHistoricalRows() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("8").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        jdbc.update("INSERT INTO service_duration_config "
                        + "(service_type, duration_value, duration_unit, code_silence_months, enabled, spec_code) "
                        + "VALUES ('CORS', 1, 'MONTH', 12, 1, 'M1')");
        int legacyAccountConfigCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_config", Integer.class);

        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class));
        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_duration_config' "
                        + "AND column_name IN ('display_name', 'duration_days', 'code_silence_days', "
                        + "'account_silence_days')", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_duration_config "
                        + "WHERE spec_code = 'M1' AND duration_value = 1 AND duration_unit = 'MONTH' "
                        + "AND code_silence_months = 12 AND duration_days IS NULL "
                        + "AND code_silence_days IS NULL AND account_silence_days IS NULL", Integer.class));
        assertEquals(legacyAccountConfigCount, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_config", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_duration_config' "
                        + "AND index_name = 'uk_service_duration_spec_code'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_duration_config' "
                        + "AND index_name = 'uk_service_duration_display_name'", Integer.class));

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE service_duration_config SET duration_days = 30 WHERE spec_code = 'M1'"));
    }
}

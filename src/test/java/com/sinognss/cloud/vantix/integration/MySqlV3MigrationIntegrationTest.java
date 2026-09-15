package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlV3MigrationIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v3_migration");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Test
    void v3BackfillsCanonicalSpecsAddsGlobalDurationUniquenessAndNoForeignKeys() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("2")).load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO service_duration_config "
                        + "(service_type, duration_value, duration_unit, code_silence_months, enabled) "
                        + "VALUES ('CORS', 1, 'MONTH', 6, 1), ('SDK', 3, 'MONTH', 12, 1), "
                        + "('CORS', 1, 'DAY', 0, 1), ('SDK', 1, 'YEAR', 12, 1), "
                        + "('CORS', 12, 'MONTH', 12, 1)");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load().migrate();

        List<String> codes = jdbc.queryForList(
                "SELECT spec_code FROM service_duration_config", String.class);
        assertEquals(Set.of("M1", "M3", "D1", "Y1", "M12"), new HashSet<>(codes));
        assertFalse(codes.stream().anyMatch(code -> code.contains("-S")));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                        + "AND table_name = 'service_duration_config' "
                        + "AND index_name = 'uk_service_duration_value_unit'", Integer.class));

        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO service_duration_config "
                        + "(service_type, duration_value, duration_unit, code_silence_months, enabled, spec_code) "
                        + "VALUES ('OTHER', 1, 'MONTH', 0, 1, 'OTHER-M1')"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = 'service_code_generate_order'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'service_code_generate_batch' AND column_name = 'generate_order_id'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                        + "AND table_name = 'service_code_generate_batch' "
                        + "AND index_name = 'uk_service_code_generate_order_spec'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('service_code_generate_order', 'service_code_generate_batch') "
                        + "AND referenced_table_name IS NOT NULL", Integer.class));        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_code' "
                        + "AND referenced_table_name IS NOT NULL", Integer.class));
    }
}

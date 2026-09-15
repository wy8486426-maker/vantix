package com.sinognss.cloud.vantix.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MySqlV6MigrationIntegrationTest {
    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v6_migration");

    @Test
    void appliesAllMigrationsThroughV6WithExpectedForceActivationIndex() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("6").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        assertEquals(List.of("1", "2", "3", "4", "5", "6"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_account' "
                        + "AND index_name = 'idx_service_account_force_activation'",
                Integer.class));
        assertEquals(List.of("cors_activation_status", "force_activate_at", "status_sync_next_at", "id"),
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.statistics "
                                + "WHERE table_schema = DATABASE() AND table_name = 'service_account' "
                                + "AND index_name = 'idx_service_account_force_activation' ORDER BY seq_in_index",
                        String.class));
    }
}

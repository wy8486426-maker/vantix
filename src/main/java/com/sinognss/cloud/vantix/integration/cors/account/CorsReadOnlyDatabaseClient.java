package com.sinognss.cloud.vantix.integration.cors.account;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Objects;

/** Fixed-purpose CORS read pool; it is intentionally not exposed as a Spring DataSource bean. */
public final class CorsReadOnlyDatabaseClient implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;

    public CorsReadOnlyDatabaseClient(String jdbcUrl, String username, String password,
                                      String driverClassName, int maximumPoolSize, int minimumIdle) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || username == null || username.isBlank()) {
            throw new IllegalArgumentException("CORS DB URL and username must be configured when enabled");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(Objects.requireNonNullElse(password, ""));
        config.setDriverClassName(driverClassName);
        config.setReadOnly(true);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setPoolName("VantixCorsReadOnlyPool");
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(1_000L);
        this.dataSource = new HikariDataSource(config);
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public NamedParameterJdbcTemplate jdbc() { return jdbc; }

    @Override
    public void close() { dataSource.close(); }
}

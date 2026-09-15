package com.sinognss.cloud.vantix.integration;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only isolated schema backed by developer-provided MySQL. */
final class LocalMySqlTestDatabase implements AutoCloseable {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String schema;
    private final String jdbcUrl;
    private final AtomicBoolean closed = new AtomicBoolean();

    private LocalMySqlTestDatabase(String host, int port, String username, String password, String schema) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.schema = schema;
        this.jdbcUrl = url(schema);
    }

    static LocalMySqlTestDatabase create(String prefix) {
        String host = required("VANTIX_TEST_MYSQL_HOST");
        int port = Integer.parseInt(required("VANTIX_TEST_MYSQL_PORT"));
        String user = required("VANTIX_TEST_MYSQL_USER");
        String password = required("VANTIX_TEST_MYSQL_PASSWORD");
        String safePrefix = prefix.substring(0, Math.min(prefix.length(), 24));
        String schema = (safePrefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)).toLowerCase();
        LocalMySqlTestDatabase database = new LocalMySqlTestDatabase(host, port, user, password, schema);
        try (Connection connection = database.adminDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "`");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create isolated MySQL test schema " + schema, exception);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(database::close, "drop-" + schema));
        return database;
    }

    String getJdbcUrl() { return jdbcUrl; }
    String getUsername() { return username; }
    String getPassword() { return password; }
    String getSchema() { return schema; }
    DataSource dataSource() { return new DriverManagerDataSource(jdbcUrl, username, password); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try (Connection connection = adminDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not drop isolated MySQL test schema " + schema, exception);
        }
    }

    private DataSource adminDataSource() {
        return new DriverManagerDataSource(url("mysql"), username, password);
    }

    private String url(String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for local MySQL integration tests");
        }
        return value;
    }
}

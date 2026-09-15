package com.sinognss.cloud.vantix.integration;

import org.opentest4j.TestAbortedException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only isolated MySQL database with local-server, Docker, and skip fallback. */
final class LocalMySqlTestDatabase implements AutoCloseable {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String schema;
    private final MySQLContainer<?> container;
    private final AtomicBoolean closed = new AtomicBoolean();

    private LocalMySqlTestDatabase(String host, int port, String username, String password,
                                   String schema, MySQLContainer<?> container) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.schema = schema;
        this.container = container;
    }

    static LocalMySqlTestDatabase create(String prefix) {
        String schema = schemaName(prefix);
        String host = env("VANTIX_TEST_MYSQL_HOST");
        String portValue = env("VANTIX_TEST_MYSQL_PORT");
        String username = env("VANTIX_TEST_MYSQL_USER");
        String password = env("VANTIX_TEST_MYSQL_PASSWORD");
        if (nonblank(host) && nonblank(portValue) && nonblank(username) && password != null) {
            return createLocal(host, Integer.parseInt(portValue), username, password, schema);
        }

        MySQLContainer<?> container;
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new IllegalStateException("Docker is unavailable");
            }
            container = new MySQLContainer<>("mysql:5.7.44")
                    .withDatabaseName(schema).withUsername("test").withPassword("test");
            container.start();
        } catch (RuntimeException dockerUnavailable) {
            throw new TestAbortedException(
                    "No complete VANTIX_TEST_MYSQL_* settings and Docker is unavailable", dockerUnavailable);
        }
        return new LocalMySqlTestDatabase(container.getHost(), container.getMappedPort(3306),
                container.getUsername(), container.getPassword(), schema, container);
    }

    private static LocalMySqlTestDatabase createLocal(String host, int port, String username,
                                                       String password, String schema) {
        LocalMySqlTestDatabase database = new LocalMySqlTestDatabase(host, port, username, password, schema, null);
        try (Connection connection = database.adminDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "`");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create isolated MySQL test schema " + schema, exception);
        }
        return database;
    }

    String getJdbcUrl() { return container == null ? url(schema) : container.getJdbcUrl(); }
    String getUsername() { return username; }
    String getPassword() { return password; }
    String getSchema() { return schema; }
    DataSource dataSource() { return new DriverManagerDataSource(getJdbcUrl(), username, password); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (container != null) {
            container.stop();
            return;
        }
        try (Connection connection = adminDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not drop isolated MySQL test schema " + schema, exception);
        }
    }

    private DataSource adminDataSource() { return new DriverManagerDataSource(url("mysql"), username, password); }
    private String url(String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }
    private static String schemaName(String prefix) {
        String safePrefix = prefix.substring(0, Math.min(prefix.length(), 24));
        return (safePrefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)).toLowerCase();
    }
    private static String env(String name) { return System.getenv(name); }
    private static boolean nonblank(String value) { return value != null && !value.isBlank(); }

    public static boolean isAvailable() {
        if (nonblank(env("VANTIX_TEST_MYSQL_HOST")) && nonblank(env("VANTIX_TEST_MYSQL_PORT"))
                && nonblank(env("VANTIX_TEST_MYSQL_USER")) && env("VANTIX_TEST_MYSQL_PASSWORD") != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}

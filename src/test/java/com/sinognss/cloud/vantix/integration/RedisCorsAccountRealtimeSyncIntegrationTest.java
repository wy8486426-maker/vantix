package com.sinognss.cloud.vantix.integration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(properties = {
        "vantix.cors-db.enabled=true",
        "vantix.cors-db.status-sync.enabled=false",
        "vantix.cors-redis.enabled=true",
        "vantix.cors-redis.confirmation-delay=500ms",
        "vantix.cors-redis.queue-capacity=10",
        "vantix.cors-operation.enabled=false"
})
@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.RedisCorsAccountRealtimeSyncIntegrationTest#isAvailable")
class RedisCorsAccountRealtimeSyncIntegrationTest {
    private static LocalMySqlTestDatabase VANTIX;
    private static LocalMySqlTestDatabase CORS;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private JdbcTemplate vantixJdbc;
    private static JdbcTemplate corsJdbc;
    private static RedisClient redisClient;
    private static RedisCommands<String, String> redis;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ensureDatabases();
        registry.add("spring.datasource.url", VANTIX::getJdbcUrl);
        registry.add("spring.datasource.username", VANTIX::getUsername);
        registry.add("spring.datasource.password", VANTIX::getPassword);
        registry.add("vantix.cors-db.jdbc-url", CORS::getJdbcUrl);
        registry.add("vantix.cors-db.username", CORS::getUsername);
        registry.add("vantix.cors-db.password", CORS::getPassword);
        registry.add("vantix.cors-redis.host", REDIS::getHost);
        registry.add("vantix.cors-redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeAll
    static void prepare() {
        ensureDatabases();
        Flyway.configure().dataSource(VANTIX.getJdbcUrl(), VANTIX.getUsername(), VANTIX.getPassword())
                .locations("classpath:db/migration").load().migrate();
        corsJdbc = new JdbcTemplate(new DriverManagerDataSource(CORS.getJdbcUrl(), CORS.getUsername(), CORS.getPassword()));
        corsJdbc.execute("CREATE TABLE userinfo (ID BIGINT PRIMARY KEY, name VARCHAR(128) NOT NULL, "
                + "password VARCHAR(128), secret VARCHAR(128), phone VARCHAR(32), email VARCHAR(128), "
                + "currentIP VARCHAR(64), active_status INT, account_status INT, active_time DATETIME, "
                + "expiredate DATETIME, lastupdatetime DATETIME)");
        redisClient = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redis = redisClient.connect().sync();
    }

    @AfterAll
    static void close() {
        if (redisClient != null) redisClient.shutdown();
        CORS.close();
        VANTIX.close();
    }

    @Test
    void realPublishRefreshesActiveDisableExpireUnknownAndUpdatePass() {
        assertPublishedState(501, "active", 0, 0, "ACTIVE", "ENABLED");
        assertPublishedState(502, "disable", 0, 1, "ACTIVE", "DISABLED");
        assertPublishedState(503, "expire", 2, 0, "EXPIRED", "ENABLED");
        assertPublishedState(504, "futureAction", 0, 0, "ACTIVE", "ENABLED");
        assertPublishedState(505, "updatePass", 0, 0, "ACTIVE", "ENABLED");
    }

    @Test
    void confirmationFixesPublishBeforeCommitWithSecondAuthoritativeRead() throws Exception {
        prepareAccount(506, 506, 1, 0, "DISABLED", "WAITING_ACTIVATION", null);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 15, 12, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 10, 15, 12, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 9, 15, 12, 1);
        DataSource source = CORS.dataSource();
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE userinfo SET active_status=0, account_status=0, active_time=?, expiredate=?, lastupdatetime=? WHERE ID=506")) {
                statement.setObject(1, t2);
                statement.setObject(2, t3);
                statement.setObject(3, t4);
                statement.executeUpdate();
            }
            publish(506, "active");
            Thread.sleep(100);
            connection.commit();
        }

        awaitTrue(() -> "ACTIVE".equals(localValue(506, "cors_activation_status"))
                && "ENABLED".equals(localValue(506, "cors_status"))
                && t4.equals(localDate(506, "cors_updated_at")), Duration.ofSeconds(5));
        assertEquals(t2, localDate(506, "activated_at"));
        assertEquals(t3, localDate(506, "expire_at"));
    }

    private void assertPublishedState(long id, String action, int activeStatus, int accountStatus,
                                      String expectedActivation, String expectedAccountStatus) {
        prepareAccount(id, id, activeStatus, accountStatus, "DISABLED", "WAITING_ACTIVATION", null);
        publish(id, action);
        awaitTrue(() -> expectedActivation.equals(localValue(id, "cors_activation_status"))
                && expectedAccountStatus.equals(localValue(id, "cors_status")), Duration.ofSeconds(5));
    }

    private void prepareAccount(long id, long corsId, int activeStatus, int accountStatus,
                                String localStatus, String localActivation, LocalDateTime updatedAt) {
        vantixJdbc.update("DELETE FROM service_account WHERE id=?", id);
        vantixJdbc.update("DELETE FROM service_code WHERE id=?", id + 10000);
        corsJdbc.update("DELETE FROM userinfo WHERE ID=?", corsId);
        corsJdbc.update("INSERT INTO userinfo (ID,name,password,secret,active_status,account_status,"
                        + "active_time,expiredate,lastupdatetime) VALUES (?,?,?,?,?,?,NULL,NULL,?)",
                corsId, "account" + corsId, "NEVER_READ", "NEVER_READ", activeStatus, accountStatus,
                updatedAt == null ? LocalDateTime.of(2026, 9, 15, 9, 0) : updatedAt);
        vantixJdbc.update("INSERT INTO service_code (id,code,owner_company_id,spec_code,service_type,duration_days,"
                        + "code_silence_days,expire_at,status,version) VALUES (?,?,1,'REDIS','CORS',1,0,?,'PENDING',0)",
                id + 10000, "REDIS-CODE-" + id, LocalDateTime.of(2027, 1, 1, 0, 0));
        vantixJdbc.update("INSERT INTO service_account (id,cors_account_id,account,owner_company_id,"
                        + "source_service_code_id,spec_code,display_name,service_type,duration_days,account_silence_days,"
                        + "cors_status,cors_activation_status,version) VALUES (?,?,?,1,?,'REDIS','Redis规格','CORS',1,0,?,?,0)",
                id, String.valueOf(corsId), "account" + corsId, id + 10000, localStatus, localActivation);
    }

    private void publish(long id, String action) {
        redis.publish("LW_WEBCORS_PLATUPDATE", "{\"userName\":\"account" + id + "\",\"action\":\"" + action + "\"}");
    }

    private String localValue(long id, String column) {
        return vantixJdbc.queryForObject("SELECT " + column + " FROM service_account WHERE id=?", String.class, id);
    }

    private LocalDateTime localDate(long id, String column) {
        return vantixJdbc.queryForObject("SELECT " + column + " FROM service_account WHERE id=?", LocalDateTime.class, id);
    }

    private static void awaitTrue(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(50); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new AssertionError(interrupted);
            }
        }
        throw new AssertionError("Timed out waiting for Redis realtime refresh");
    }

    static boolean isAvailable() {
        return LocalMySqlTestDatabase.isAvailable();
    }

    private static void ensureDatabases() {
        if (VANTIX == null) VANTIX = LocalMySqlTestDatabase.create("vantix_redis_sync");
        if (CORS == null) CORS = LocalMySqlTestDatabase.create("cors_redis_userinfo");
    }

}

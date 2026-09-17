package com.sinognss.cloud.vantix.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetCommand;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetReservation;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetReserveTransaction;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.condition.EnabledIf("com.sinognss.cloud.vantix.integration.LocalMySqlTestDatabase#isAvailable")
class MySqlV8PasswordResetReservationConcurrencyIntegrationTest {
    private static final long SERVICE_ACCOUNT_ID = 81_001L;

    static final LocalMySqlTestDatabase MYSQL = LocalMySqlTestDatabase.create("vantix_v8_password_reset_concurrency");

    @org.junit.jupiter.api.AfterAll
    static void closeDatabase() { MYSQL.close(); }

    @Test
    void concurrentDifferentRequestsSerializeAndSameRequestIdReplays() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertAccount(jdbc);

        SqlSessionFactory sessionFactory = sqlSessionFactory(dataSource);
        SqlSessionTemplate sessions = new SqlSessionTemplate(sessionFactory);
        AccountPasswordResetReserveTransaction target = new AccountPasswordResetReserveTransaction(
                sessions.getMapper(AccountPasswordActionMapper.class),
                sessions.getMapper(CorsOperationMapper.class),
                sessions.getMapper(ServiceAccountMapper.class),
                java.time.Clock.systemUTC());
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        AccountPasswordResetReserveTransaction transactionalReserve =
                (AccountPasswordResetReserveTransaction) proxyFactory.getProxy();

        List<Attempt> attempts = reserveConcurrently(transactionalReserve,
                new AccountPasswordResetCommand("PWD-RS-RACE-A", SERVICE_ACCOUNT_ID),
                new AccountPasswordResetCommand("PWD-RS-RACE-B", SERVICE_ACCOUNT_ID));
        List<Attempt> succeeded = attempts.stream().filter(attempt -> attempt.reservation() != null).toList();
        List<Attempt> rejected = attempts.stream().filter(attempt -> attempt.failure() != null).toList();
        assertEquals(1, succeeded.size(), "only one unresolved reset may reserve the account");
        assertEquals(1, rejected.size());
        assertEquals(ErrorCode.PASSWORD_RESET_IN_PROGRESS, rejected.get(0).failure().getVantixErrorCode());

        AccountPasswordResetReservation original = succeeded.get(0).reservation();
        AccountPasswordResetReservation replay = transactionalReserve.reserve(
                new AccountPasswordResetCommand(original.requestId(), SERVICE_ACCOUNT_ID),
                globalScope(), operator());
        assertEquals(original, replay, "same requestId and account must return the original reservation");

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_password_action WHERE service_account_id = ? AND action_type = 'RESET'",
                Integer.class, SERVICE_ACCOUNT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cors_operation WHERE service_account_id = ? "
                        + "AND operation_type = 'RESET_ACCOUNT_PASSWORD' AND biz_type = 'ACCOUNT_PASSWORD_RESET'",
                Integer.class, SERVICE_ACCOUNT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_password_action WHERE request_id = ?",
                Integer.class, original.requestId()));
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM account_password_action WHERE request_id = ?", String.class, original.requestId()));
        assertNotNull(jdbc.queryForObject(
                "SELECT active_reset_account_id FROM account_password_action WHERE request_id = ?",
                Long.class, original.requestId()));

        assertThrows(DuplicateKeyException.class, () -> jdbc.update("INSERT INTO account_password_action "
                        + "(request_id, action_type, service_account_id, owner_company_id, cors_account_id, account, "
                        + "status, version, active_reset_account_id) VALUES (?, 'RESET', ?, 801, ?, ?, 'PROCESSING', 0, ?)",
                "PWD-RS-RACE-DIRECT-DUP", SERVICE_ACCOUNT_ID, "cors-account-" + SERVICE_ACCOUNT_ID,
                "account-" + SERVICE_ACCOUNT_ID, SERVICE_ACCOUNT_ID));

        Long actionId = jdbc.queryForObject(
                "SELECT id FROM account_password_action WHERE request_id = ?", Long.class, original.requestId());
        LocalDateTime completedAt = LocalDateTime.now().withNano(0);
        assertEquals(1, sessions.getMapper(AccountPasswordActionMapper.class).transitionFromProcessing(
                actionId, 0L, "SUCCEEDED", null, null, completedAt, completedAt, null));
        assertNull(jdbc.queryForObject(
                "SELECT active_reset_account_id FROM account_password_action WHERE id = ?", Long.class, actionId));

        AccountPasswordResetReservation next = transactionalReserve.reserve(
                new AccountPasswordResetCommand("PWD-RS-RACE-C", SERVICE_ACCOUNT_ID),
                globalScope(), operator());
        assertEquals("PWD-RS-RACE-C", next.requestId());
        assertEquals(SERVICE_ACCOUNT_ID, jdbc.queryForObject(
                "SELECT active_reset_account_id FROM account_password_action WHERE request_id = ?",
                Long.class, next.requestId()));
    }

    private List<Attempt> reserveConcurrently(AccountPasswordResetReserveTransaction reserve,
                                             AccountPasswordResetCommand first,
                                             AccountPasswordResetCommand second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Attempt> firstResult = executor.submit(() -> reserveOne(reserve, first, ready, start));
        Future<Attempt> secondResult = executor.submit(() -> reserveOne(reserve, second, ready, start));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both reserve workers should be ready");
            start.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "reserve workers should terminate");
        }
    }

    private Attempt reserveOne(AccountPasswordResetReserveTransaction reserve,
                               AccountPasswordResetCommand command,
                               CountDownLatch ready,
                               CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out waiting for concurrent reserve start");
        }
        try {
            return new Attempt(command.requestId(), reserve.reserve(command, globalScope(), operator()), null);
        } catch (BusinessException exception) {
            return new Attempt(command.requestId(), null, exception);
        }
    }

    private void insertAccount(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO service_account (id, cors_account_id, account, owner_company_id, assigned_user_id, "
                        + "source_service_code_id, spec_code, display_name, service_type, duration_days, account_silence_days) "
                        + "VALUES (?, ?, ?, 801, 1101, ?, 'PASSWORD', '密码规格', 'CORS', 30, 180)",
                SERVICE_ACCOUNT_ID, "cors-account-" + SERVICE_ACCOUNT_ID,
                "account-" + SERVICE_ACCOUNT_ID, SERVICE_ACCOUNT_ID);
    }

    private SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AccountPasswordActionMapper.class);
        configuration.addMapper(CorsOperationMapper.class);
        configuration.addMapper(ServiceAccountMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    private static UserScope globalScope() {
        return new UserScope(null, null);
    }

    private static OperatorIdentity operator() {
        return new OperatorIdentity(1101L, "integration-test");
    }

    private record Attempt(String requestId,
                           AccountPasswordResetReservation reservation,
                           BusinessException failure) {
    }
}

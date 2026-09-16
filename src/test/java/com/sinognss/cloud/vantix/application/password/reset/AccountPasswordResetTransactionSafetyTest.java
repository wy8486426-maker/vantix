package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.config.CorsAccountPasswordProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResetResult;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountPasswordResetTransactionSafetyTest {
    @Test
    void preflightQueryAndResetGatewayCallsRunOutsideCallerTransaction() {
        PlatformTransactionManager transactionManager = transactionManager("passwordResetTxBoundary");
        AccountPasswordResetClaimService claimService = mock(AccountPasswordResetClaimService.class);
        AccountPasswordResetStateService stateService = mock(AccountPasswordResetStateService.class);
        AccountPasswordResetFinalizeService finalizeService = mock(AccountPasswordResetFinalizeService.class);
        CorsAccountStatusGateway statusGateway = mock(CorsAccountStatusGateway.class);
        CorsAccountPasswordGateway passwordGateway = mock(CorsAccountPasswordGateway.class);
        CorsOperation operation = operation("CLAIMED", 1L);
        AccountPasswordAction action = action(0L);
        when(claimService.claim(17L)).thenReturn(new ClaimedAccountPasswordReset(operation, action, false));
        when(statusGateway.getAccount("cors-31")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return CorsAccountStatusResult.success(snapshot());
        });
        when(passwordGateway.resetPassword(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new CorsPasswordResetResult(CorsOutcome.SUCCESS, "PWD-RS-17", "cors-31", null);
        });

        AccountPasswordResetProcessor target = new AccountPasswordResetProcessor(
                claimService, stateService, finalizeService, statusGateway, passwordGateway);
        AccountPasswordResetProcessor processor = transactionalProxy(target, transactionManager,
                AccountPasswordResetProcessor.class);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        outer.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            processor.process(17L);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });

        verify(statusGateway).getAccount("cors-31");
        verify(passwordGateway).resetPassword(any());
        verify(finalizeService).finalizeSuccess(operation, action,
                new CorsPasswordResetResult(CorsOutcome.SUCCESS, "PWD-RS-17", "cors-31", null));
    }

    @Test
    void recoveryQueryGatewayCallRunsOutsideCallerTransaction() {
        PlatformTransactionManager transactionManager = transactionManager("passwordResetQueryTxBoundary");
        AccountPasswordResetClaimService claimService = mock(AccountPasswordResetClaimService.class);
        AccountPasswordResetStateService stateService = mock(AccountPasswordResetStateService.class);
        AccountPasswordResetFinalizeService finalizeService = mock(AccountPasswordResetFinalizeService.class);
        CorsAccountStatusGateway statusGateway = mock(CorsAccountStatusGateway.class);
        CorsAccountPasswordGateway passwordGateway = mock(CorsAccountPasswordGateway.class);
        CorsOperation operation = operation("CLAIMED", 1L);
        AccountPasswordAction action = action(0L);
        when(claimService.claim(17L)).thenReturn(new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset("PWD-RS-17")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new CorsPasswordResetResult(CorsOutcome.UNKNOWN, "PWD-RS-17", "cors-31", "untrusted");
        });

        AccountPasswordResetProcessor target = new AccountPasswordResetProcessor(
                claimService, stateService, finalizeService, statusGateway, passwordGateway);
        AccountPasswordResetProcessor processor = transactionalProxy(target, transactionManager,
                AccountPasswordResetProcessor.class);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        outer.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            processor.process(17L);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });

        verify(passwordGateway).queryPasswordReset("PWD-RS-17");
        verify(statusGateway, org.mockito.Mockito.never()).getAccount(anyString());
        verify(passwordGateway, org.mockito.Mockito.never()).resetPassword(any());
        verify(stateService).retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.QUERY_UNKNOWN);
    }

    @Test
    void operationInsertFailureRollsBackReservedAuditAction() {
        PlatformTransactionManager transactionManager = transactionManager("passwordReserveRollback");
        JdbcTemplate jdbc = jdbc(transactionManager);
        jdbc.execute("CREATE TABLE password_action_probe (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                + "request_id VARCHAR(128) NOT NULL)");
        AccountPasswordActionMapper actionMapper = mock(AccountPasswordActionMapper.class);
        CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
        ServiceAccountMapper serviceAccountMapper = mock(ServiceAccountMapper.class);
        when(actionMapper.selectByRequestId("PWD-RS-17")).thenReturn(null);
        when(actionMapper.selectByRequestIdForUpdate("PWD-RS-17")).thenReturn(null);
        when(actionMapper.selectActiveResetByServiceAccountId(31L)).thenReturn(null);
        when(serviceAccountMapper.selectByIdForUpdate(31L)).thenReturn(serviceAccount());
        doAnswer(invocation -> {
            AccountPasswordAction action = invocation.getArgument(0);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO password_action_probe (request_id) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, action.getRequestId());
                return statement;
            }, keyHolder);
            action.setId(keyHolder.getKey().longValue());
            return 1;
        }).when(actionMapper).insert(any(AccountPasswordAction.class));
        doThrow(new IllegalStateException("injected operation insert failure"))
                .when(operationMapper).insert(any(CorsOperation.class));

        AccountPasswordResetReserveTransaction target = new AccountPasswordResetReserveTransaction(
                actionMapper, operationMapper, serviceAccountMapper, Clock.systemUTC());
        AccountPasswordResetReserveTransaction transaction = transactionalProxy(target, transactionManager,
                AccountPasswordResetReserveTransaction.class);

        assertThrows(IllegalStateException.class, () -> transaction.reserve(
                new AccountPasswordResetCommand("PWD-RS-17", 31L),
                new com.sinognss.cloud.vantix.common.user.UserScope(11L, 7L),
                new com.sinognss.cloud.vantix.common.user.OperatorIdentity(11L, "operator")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_action_probe", Integer.class));
    }

    @Test
    void localFinalizeFailureRollsBackSuccessAndMovesBothRowsToManualReview() {
        PlatformTransactionManager transactionManager = transactionManager("passwordFinalizeRollback");
        JdbcTemplate jdbc = jdbc(transactionManager);
        jdbc.execute("CREATE TABLE account_password_action (id BIGINT PRIMARY KEY, status VARCHAR(32), "
                + "version BIGINT, last_error_code VARCHAR(64), last_error_message VARCHAR(512), "
                + "completed_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE cors_operation (id BIGINT PRIMARY KEY, status VARCHAR(32), version BIGINT)");
        jdbc.update("INSERT INTO account_password_action (id, status, version) VALUES (23, 'PROCESSING', 0)");
        jdbc.update("INSERT INTO cors_operation (id, status, version) VALUES (17, 'CLAIMED', 1)");

        AccountPasswordActionMapper actionMapper = mock(AccountPasswordActionMapper.class);
        CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
        ServiceAccountMapper serviceAccountMapper = mock(ServiceAccountMapper.class);
        CorsOperation operation = operation("CLAIMED", 1L);
        AccountPasswordAction action = action(0L);
        when(operationMapper.selectByIdForUpdate(17L)).thenReturn(operation);
        when(actionMapper.selectByIdForUpdate(23L)).thenReturn(action);
        when(serviceAccountMapper.selectByIdForUpdate(31L)).thenReturn(serviceAccount());
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            Long expectedVersion = invocation.getArgument(1);
            String nextStatus = invocation.getArgument(2);
            String code = invocation.getArgument(3);
            String message = invocation.getArgument(4);
            Object completedAt = invocation.getArgument(5);
            Object now = invocation.getArgument(6);
            return jdbc.update("UPDATE account_password_action SET status = ?, version = version + 1, "
                            + "last_error_code = ?, last_error_message = ?, completed_at = ?, updated_at = ? "
                            + "WHERE id = ? AND status = 'PROCESSING' AND version = ?",
                    nextStatus, code, message, completedAt, now, id, expectedVersion);
        }).when(actionMapper).transitionFromProcessing(any(), any(), anyString(), any(), any(), any(), any(), any());
        doThrow(new IllegalStateException("injected success update failure"))
                .when(operationMapper).markSucceeded(17L, 1L, LocalDateTimeHolder.NOW);
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            Long version = invocation.getArgument(1);
            return jdbc.update("UPDATE cors_operation SET status = 'MANUAL_REVIEW', version = version + 1 "
                    + "WHERE id = ? AND status = 'CLAIMED' AND version = ?", id, version);
        }).when(operationMapper).markManualReview(any(), any(), anyString(), anyString(), any());

        AccountPasswordResetFinalizeService finalizeTarget = new AccountPasswordResetFinalizeService(
                operationMapper, actionMapper, serviceAccountMapper, Clock.fixed(
                Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC));
        AccountPasswordResetFinalizeService finalizeService = transactionalProxy(finalizeTarget, transactionManager,
                AccountPasswordResetFinalizeService.class);
        CorsPasswordResetResult success = new CorsPasswordResetResult(CorsOutcome.SUCCESS,
                "PWD-RS-17", "cors-31", null);

        assertThrows(IllegalStateException.class, () -> finalizeService.finalizeSuccess(operation, action, success));
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM account_password_action WHERE id = 23", String.class));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT version FROM account_password_action WHERE id = 23", Long.class));
        assertEquals("CLAIMED", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE id = 17", String.class));

        AccountPasswordResetStateService stateTarget = new AccountPasswordResetStateService(operationMapper,
                actionMapper, new CorsAccountPasswordProperties(), Clock.fixed(
                Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC));
        AccountPasswordResetStateService stateService = transactionalProxy(stateTarget, transactionManager,
                AccountPasswordResetStateService.class);
        stateService.markManualReviewAfterFinalizeFailure(operation, action);

        assertEquals("MANUAL_REVIEW", jdbc.queryForObject(
                "SELECT status FROM account_password_action WHERE id = 23", String.class));
        assertEquals("MANUAL_REVIEW", jdbc.queryForObject(
                "SELECT status FROM cors_operation WHERE id = 17", String.class));
    }

    @Test
    void resetResultToStringDoesNotIncludeUntrustedErrorMetadata() {
        CorsPasswordResetResult result = new CorsPasswordResetResult(CorsOutcome.UNKNOWN,
                "request-id", "account-id", "secret-like-untrusted-value");
        assertFalse(result.toString().contains("secret-like-untrusted-value"));
    }

    private static PlatformTransactionManager transactionManager(String databaseName) {
        return new DataSourceTransactionManager(new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    private static JdbcTemplate jdbc(PlatformTransactionManager transactionManager) {
        return new JdbcTemplate(((DataSourceTransactionManager) transactionManager).getDataSource());
    }

    @SuppressWarnings("unchecked")
    private static <T> T transactionalProxy(T target, PlatformTransactionManager transactionManager, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    private static CorsAccountSnapshot snapshot() {
        return new CorsAccountSnapshot("cors-31", "account-31", "DISABLED", "WAITING_ACTIVATION",
                null, null, OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                OffsetDateTime.parse("2026-09-14T12:00:00Z"));
    }

    private static CorsOperation operation(String status, long version) {
        CorsOperation operation = new CorsOperation();
        operation.setId(17L);
        operation.setRequestId("PWD-RS-17");
        operation.setOperationType(AccountPasswordResetConstants.OPERATION_TYPE);
        operation.setBizType(AccountPasswordResetConstants.BIZ_TYPE);
        operation.setBizId(23L);
        operation.setServiceAccountId(31L);
        operation.setStatus(status);
        operation.setRetryCount(0);
        operation.setVersion(version);
        return operation;
    }

    private static AccountPasswordAction action(long version) {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(23L);
        action.setRequestId("PWD-RS-17");
        action.setActionType("RESET");
        action.setServiceAccountId(31L);
        action.setCorsAccountId("cors-31");
        action.setAccount("account-31");
        action.setStatus("PROCESSING");
        action.setVersion(version);
        return action;
    }

    private static ServiceAccount serviceAccount() {
        ServiceAccount account = new ServiceAccount();
        account.setId(31L);
        account.setCorsAccountId("cors-31");
        account.setAccount("account-31");
        account.setOwnerCompanyId(7L);
        account.setAssignedUserId(11L);
        return account;
    }

    private static final class LocalDateTimeHolder {
        private static final java.time.LocalDateTime NOW = java.time.LocalDateTime.of(2026, 9, 14, 12, 0);
    }
}

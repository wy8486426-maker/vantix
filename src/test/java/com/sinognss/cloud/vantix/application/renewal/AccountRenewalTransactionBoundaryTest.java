package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRenewalTransactionBoundaryTest {
    @Test
    void preflightQueryAndRenewCallsRunOutsideAnActiveDatabaseTransaction() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:accountRenewalTxBoundary;DB_CLOSE_DELAY=-1", "sa", "");
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        AccountRenewalClaimService claimService = mock(AccountRenewalClaimService.class);
        AccountRenewalStateService stateService = mock(AccountRenewalStateService.class);
        AccountRenewalFinalizeService finalizeService = mock(AccountRenewalFinalizeService.class);
        ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
        CorsAccountStatusGateway statusGateway = mock(CorsAccountStatusGateway.class);
        CorsAccountRenewalGateway renewalGateway = mock(CorsAccountRenewalGateway.class);

        AccountRenewal operationRenewal = renewal();
        CorsOperation operation = operation();
        ServiceAccount account = account();
        when(claimService.claim(41L)).thenReturn(new ClaimedAccountRenewal(operation, operationRenewal, true));
        when(accountMapper.selectById(61L)).thenReturn(account);
        when(renewalGateway.queryRenewal("RN-41")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return CorsAccountRenewalResult.notFound("RN-41", "NOT_FOUND", "not found");
        });
        when(statusGateway.getAccount("cors-61")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return CorsAccountStatusResult.success(snapshot());
        });
        when(renewalGateway.renew(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return CorsAccountRenewalResult.definitiveReject("RN-41", "REJECTED", "rejected");
        });

        AccountRenewalProcessor target = new AccountRenewalProcessor(claimService, stateService, finalizeService,
                accountMapper, statusGateway, renewalGateway);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        AccountRenewalProcessor proxy = (AccountRenewalProcessor) proxyFactory.getProxy();

        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        outerTransaction.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            proxy.process(41L);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "the caller transaction resumes after processor completion");
            return null;
        });

        verify(renewalGateway).queryRenewal("RN-41");
        verify(statusGateway).getAccount("cors-61");
        verify(renewalGateway).renew(any());
        verify(stateService).definitiveFail(any(), any(), any(), any());
    }

    private static CorsAccountSnapshot snapshot() {
        return new CorsAccountSnapshot("cors-61", "account-61", "ACTIVE", "ACTIVE",
                OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8)),
                OffsetDateTime.of(2027, 8, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8)),
                OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8)),
                OffsetDateTime.of(2026, 9, 14, 12, 0, 0, 0, ZoneOffset.ofHours(8)));
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId("RN-41");
        operation.setOperationType(AccountRenewalConstants.OPERATION_TYPE);
        operation.setBizType(AccountRenewalConstants.BIZ_TYPE);
        operation.setBizId(51L);
        operation.setServiceAccountId(61L);
        operation.setStatus(AccountRenewalConstants.CLAIMED);
        operation.setVersion(1L);
        return operation;
    }

    private static AccountRenewal renewal() {
        AccountRenewal renewal = new AccountRenewal();
        renewal.setId(51L);
        renewal.setServiceAccountId(61L);
        renewal.setServiceCodeId(71L);
        renewal.setRequestId("RN-41");
        renewal.setServiceType("CORS");
        renewal.setSpecCode("SPEC-1");
        renewal.setDurationDays(90);
        renewal.setCodeSilenceDays(180);
        renewal.setStatus(AccountRenewalConstants.PROCESSING);
        renewal.setVersion(0L);
        return renewal;
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(61L);
        account.setCorsAccountId("cors-61");
        account.setAccount("account-61");
        account.setServiceType("CORS");
        account.setVersion(4L);
        return account;
    }
}

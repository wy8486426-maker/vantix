package com.sinognss.cloud.vantix.application.password.reveal;

import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordRevealResult;
import com.sinognss.cloud.vantix.integration.cors.account.PasswordRevealSecret;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class AccountPasswordRevealTransactionBoundaryTest {
    @Test
    void remoteRevealCallRunsOutsideCallerTransaction() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:accountPasswordRevealTxBoundary;DB_CLOSE_DELAY=-1", "sa", "");
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        AccountPasswordRevealAuditService audit = mock(AccountPasswordRevealAuditService.class);
        CorsAccountPasswordGateway gateway = mock(CorsAccountPasswordGateway.class);
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(51L);
        action.setRequestId("RV-41");
        action.setServiceAccountId(41L);
        action.setCorsAccountId("cors-41");
        action.setAccount("account-41");
        action.setVersion(0L);
        when(audit.reserve(41L, "RV-41")).thenReturn(action);
        when(gateway.revealPassword(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new CorsPasswordRevealResult(CorsOutcome.SUCCESS, "RV-41", "cors-41",
                    new PasswordRevealSecret("one-request-only"), null);
        });

        AccountPasswordRevealService target = new AccountPasswordRevealService(audit, gateway);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        AccountPasswordRevealService proxy = (AccountPasswordRevealService) proxyFactory.getProxy();

        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        outerTransaction.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            proxy.reveal(41L, "RV-41");
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });

        verify(gateway).revealPassword(any());
        verify(audit).complete(51L, 0L);
    }
}

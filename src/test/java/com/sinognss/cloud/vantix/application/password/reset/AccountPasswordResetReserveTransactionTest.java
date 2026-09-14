package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordResetReserveTransactionTest {
    private static final long SERVICE_ACCOUNT_ID = 31L;
    private static final long ACTION_ID = 23L;
    private static final long OPERATION_ID = 17L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);
    private static final String REQUEST_ID = "PWD-RS-17";
    private static final String CORS_ACCOUNT_ID = "cors-account-31";
    private static final String ACCOUNT = "account-31";

    @Mock private AccountPasswordActionMapper actionMapper;
    @Mock private CorsOperationMapper operationMapper;
    @Mock private ServiceAccountMapper serviceAccountMapper;

    private AccountPasswordResetReserveTransaction transaction;
    private ServiceAccount serviceAccount;

    @BeforeEach
    void setUp() {
        transaction = new AccountPasswordResetReserveTransaction(actionMapper, operationMapper, serviceAccountMapper,
                Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC));
        serviceAccount = new ServiceAccount();
        serviceAccount.setId(SERVICE_ACCOUNT_ID);
        serviceAccount.setCorsAccountId(CORS_ACCOUNT_ID);
        serviceAccount.setAccount(ACCOUNT);
        serviceAccount.setOwnerCompanyId(7L);
        serviceAccount.setAssignedUserId(11L);
        when(actionMapper.selectByRequestId(REQUEST_ID)).thenReturn(null);
        when(actionMapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(null);
        when(serviceAccountMapper.selectByIdForUpdate(SERVICE_ACCOUNT_ID)).thenReturn(serviceAccount);
        when(actionMapper.selectActiveResetByServiceAccountId(SERVICE_ACCOUNT_ID)).thenReturn(null);
        doAnswer(invocation -> {
            AccountPasswordAction action = invocation.getArgument(0);
            action.setId(ACTION_ID);
            return 1;
        }).when(actionMapper).insert(any(AccountPasswordAction.class));
        doAnswer(invocation -> {
            CorsOperation operation = invocation.getArgument(0);
            operation.setId(OPERATION_ID);
            return 1;
        }).when(operationMapper).insert(any(CorsOperation.class));
    }

    @Test
    void reservesAuditAndOperationWithSameIdentityAndNoCredentialData() {
        AccountPasswordResetReservation result = transaction.reserve(
                new AccountPasswordResetCommand(REQUEST_ID, SERVICE_ACCOUNT_ID),
                new UserScope(11L, 7L), new OperatorIdentity(11L, "operator"));

        assertEquals(new AccountPasswordResetReservation(REQUEST_ID, SERVICE_ACCOUNT_ID, "PROCESSING"), result);
        ArgumentCaptor<AccountPasswordAction> actionCaptor = ArgumentCaptor.forClass(AccountPasswordAction.class);
        ArgumentCaptor<CorsOperation> operationCaptor = ArgumentCaptor.forClass(CorsOperation.class);
        verify(actionMapper).insert(actionCaptor.capture());
        verify(operationMapper).insert(operationCaptor.capture());
        AccountPasswordAction action = actionCaptor.getValue();
        CorsOperation operation = operationCaptor.getValue();
        assertEquals("RESET", action.getActionType());
        assertEquals("PROCESSING", action.getStatus());
        assertEquals(SERVICE_ACCOUNT_ID, action.getServiceAccountId());
        assertEquals(CORS_ACCOUNT_ID, action.getCorsAccountId());
        assertEquals(ACCOUNT, action.getAccount());
        assertEquals(11L, action.getOperatorUserId());
        assertEquals(NOW, action.getCreatedAt());
        assertEquals(REQUEST_ID, operation.getRequestId());
        assertEquals("RESET_ACCOUNT_PASSWORD", operation.getOperationType());
        assertEquals("ACCOUNT_PASSWORD_RESET", operation.getBizType());
        assertEquals(ACTION_ID, operation.getBizId());
        assertEquals(SERVICE_ACCOUNT_ID, operation.getServiceAccountId());
        assertEquals("PENDING", operation.getStatus());
        assertEquals(0, operation.getRetryCount());
    }
}

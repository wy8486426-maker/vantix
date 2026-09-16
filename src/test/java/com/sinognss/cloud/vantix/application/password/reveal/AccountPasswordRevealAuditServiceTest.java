package com.sinognss.cloud.vantix.application.password.reveal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class AccountPasswordRevealAuditServiceTest {
    private final AccountPasswordActionMapper actionMapper = mock(AccountPasswordActionMapper.class);
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final AccountPasswordRevealAuditService audit = new AccountPasswordRevealAuditService(
            actionMapper, accountMapper, userHolder,
            Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void insertsOnlyAuditMetadataAfterCheckingAccessAndIdentity() {
        ServiceAccount account = account();
        when(accountMapper.selectByIdForUpdate(41L)).thenReturn(account);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(9L, "operator"));
        when(actionMapper.selectByRequestId("RV-41")).thenReturn(null);
        doAnswer(invocation -> {
            AccountPasswordAction inserted = invocation.getArgument(0);
            inserted.setId(51L);
            return 1;
        }).when(actionMapper).insert(any(AccountPasswordAction.class));

        AccountPasswordAction reserved = audit.reserve(41L, " RV-41 ");

        assertEquals(51L, reserved.getId());
        ArgumentCaptor<AccountPasswordAction> captor = ArgumentCaptor.forClass(AccountPasswordAction.class);
        verify(actionMapper).insert(captor.capture());
        AccountPasswordAction persisted = captor.getValue();
        assertEquals("REVEAL", persisted.getActionType());
        assertEquals("PROCESSING", persisted.getStatus());
        assertEquals(41L, persisted.getServiceAccountId());
        assertEquals("cors-41", persisted.getCorsAccountId());
        assertEquals("account-41", persisted.getAccount());
        assertNull(persisted.getActiveResetAccountId());
        assertEquals(9L, persisted.getOperatorUserId());
        assertEquals(0L, persisted.getVersion());
    }

    @Test
    void unauthorizedPersonalScopeCannotCreateAuditOrCallOut() {
        when(accountMapper.selectByIdForUpdate(41L)).thenReturn(account());
        when(userHolder.getUserScope()).thenReturn(new UserScope(22L, 8L));

        assertThrows(BusinessException.class, () -> audit.reserve(41L, "RV-41"));

        verify(actionMapper, never()).insert(any(AccountPasswordAction.class));
        verify(userHolder, never()).getOperator();
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(41L);
        account.setCorsAccountId("cors-41");
        account.setAccount("account-41");
        account.setOwnerCompanyId(7L);
        account.setAssignedUserId(11L);
        return account;
    }
}

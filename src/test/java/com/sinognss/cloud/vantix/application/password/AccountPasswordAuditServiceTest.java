package com.sinognss.cloud.vantix.application.password;

import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordAuditServiceTest {
    @Mock private AccountPasswordActionMapper actionMapper;
    @Mock private ServiceAccountMapper accountMapper;
    @Mock private UserHolderBridge userHolder;

    private AccountPasswordAuditService service;
    private ServiceAccount account;

    @BeforeEach
    void setUp() {
        service = new AccountPasswordAuditService(actionMapper, accountMapper, userHolder,
                Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC));
        account = account();
        when(accountMapper.selectByIdForUpdate(11L)).thenReturn(account);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(23L, "operator"));
        when(actionMapper.insert(any(AccountPasswordAction.class))).thenAnswer(invocation -> {
            ((AccountPasswordAction) invocation.getArgument(0)).setId(101L);
            return 1;
        });
    }

    @Test
    void testAndHistoryImportSourcesRemainEligibleForPasswordOperations() {
        account.setAccountSource(AccountSource.TEST);
        AccountPasswordAction testAction = service.reserve(AccountPasswordActionConstants.CUSTOM, 11L);
        assertNotNull(testAction);
        assertEquals("11", testAction.getCorsAccountId());

        account.setAccountSource(AccountSource.HISTORY_IMPORT);
        AccountPasswordAction historyAction = service.reserve(AccountPasswordActionConstants.RESET, 11L);
        assertNotNull(historyAction);
        assertEquals("11", historyAction.getCorsAccountId());
    }

    @Test
    void passwordAuditUsesExistingAccountAccessPolicyAndStoresNoCredential() {
        AccountPasswordAction action = service.reserve(AccountPasswordActionConstants.CUSTOM, 11L);

        assertEquals(AccountPasswordActionConstants.CUSTOM, action.getActionType());
        assertEquals(11L, action.getServiceAccountId());
        assertEquals(23L, action.getOperatorUserId());
        assertEquals(null, action.getLastErrorMessage());
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(11L);
        account.setCorsAccountId("11");
        account.setAccount("account-11");
        account.setOwnerCompanyId(7L);
        account.setAssignedUserId(23L);
        account.setServiceType("CORS");
        account.setAccountSource(AccountSource.EXCHANGE);
        return account;
    }
}

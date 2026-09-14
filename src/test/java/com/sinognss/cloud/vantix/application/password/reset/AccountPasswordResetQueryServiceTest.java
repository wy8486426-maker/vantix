package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordResetQueryServiceTest {
    private static final String REQUEST_ID = "PWD-RS-17";

    @Mock private AccountPasswordActionMapper actionMapper;
    @Mock private UserHolderBridge userHolder;

    private AccountPasswordResetQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AccountPasswordResetQueryService(actionMapper, userHolder);
    }

    @Test
    void returnsOnlyLocalNonSecretResetStatusForAuthorizedUser() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 12, 0);
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(23L);
        action.setRequestId(REQUEST_ID);
        action.setActionType("RESET");
        action.setServiceAccountId(31L);
        action.setOwnerCompanyId(7L);
        action.setAssignedUserId(11L);
        action.setAccount("account-31");
        action.setStatus("PROCESSING");
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        when(actionMapper.selectByRequestId(REQUEST_ID)).thenReturn(action);
        when(userHolder.getUserScope()).thenReturn(new UserScope(11L, 7L));

        AccountPasswordResetView view = queryService.get(REQUEST_ID);

        assertEquals(REQUEST_ID, view.requestId());
        assertEquals(31L, view.serviceAccountId());
        assertEquals("account-31", view.account());
        assertEquals("PROCESSING", view.status());
        assertEquals(now, view.createdAt());
        verify(actionMapper).selectByRequestId(REQUEST_ID);
    }

    @Test
    void refusesCrossUserResetStatusRead() {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setRequestId(REQUEST_ID);
        action.setActionType("RESET");
        action.setServiceAccountId(31L);
        action.setOwnerCompanyId(7L);
        action.setAssignedUserId(11L);
        when(actionMapper.selectByRequestId(REQUEST_ID)).thenReturn(action);
        when(userHolder.getUserScope()).thenReturn(new UserScope(12L, 7L));

        BusinessException exception = assertThrows(BusinessException.class, () -> queryService.get(REQUEST_ID));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
    }

    @Test
    void revealActionIsNotExposedAsResetQuery() {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setActionType("REVEAL");
        when(actionMapper.selectByRequestId(REQUEST_ID)).thenReturn(action);

        BusinessException exception = assertThrows(BusinessException.class, () -> queryService.get(REQUEST_ID));

        assertEquals(ErrorCode.NOT_FOUND, exception.getVantixErrorCode());
        verify(userHolder, never()).getUserScope();
    }
}

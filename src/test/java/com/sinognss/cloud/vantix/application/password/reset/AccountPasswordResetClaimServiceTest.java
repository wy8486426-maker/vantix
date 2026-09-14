package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.config.CorsAccountPasswordProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordResetClaimServiceTest {
    private static final long OPERATION_ID = 17L;
    private static final long ACTION_ID = 23L;
    private static final long SERVICE_ACCOUNT_ID = 31L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock private CorsOperationMapper operationMapper;
    @Mock private AccountPasswordActionMapper actionMapper;
    @Mock private AccountPasswordResetStateService stateService;

    private AccountPasswordResetClaimService claimService;

    @BeforeEach
    void setUp() {
        CorsAccountPasswordProperties properties = new CorsAccountPasswordProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        claimService = new AccountPasswordResetClaimService(operationMapper, actionMapper, stateService,
                properties, clock);
    }

    @Test
    void pendingClaimDoesNotQueryFirst() {
        CorsOperation pending = operation("PENDING", null, 4L);
        CorsOperation claimed = operation("CLAIMED", null, 5L);
        AccountPasswordAction action = action();
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(pending);
        when(operationMapper.claim(OPERATION_ID, "PENDING", 4L, NOW)).thenReturn(1);
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(claimed);
        when(actionMapper.selectByIdForUpdate(ACTION_ID)).thenReturn(action);

        ClaimedAccountPasswordReset result = claimService.claim(OPERATION_ID);

        assertFalse(result.queryFirst());
        assertEquals(claimed, result.operation());
        assertEquals(action, result.action());
    }

    @Test
    void retryWaitClaimsQueryFirst() {
        CorsOperation retry = operation("RETRY_WAIT", NOW.minusSeconds(1), 6L);
        CorsOperation claimed = operation("CLAIMED", NOW.minusSeconds(1), 7L);
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(retry);
        when(operationMapper.claim(OPERATION_ID, "RETRY_WAIT", 6L, NOW)).thenReturn(1);
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(claimed);
        when(actionMapper.selectByIdForUpdate(ACTION_ID)).thenReturn(action());

        ClaimedAccountPasswordReset result = claimService.claim(OPERATION_ID);

        assertTrue(result.queryFirst());
        assertEquals("CLAIMED", result.operation().getStatus());
    }

    @Test
    void retryWaitNotDueIsNotClaimed() {
        when(operationMapper.selectById(OPERATION_ID))
                .thenReturn(operation("RETRY_WAIT", NOW.plusSeconds(1), 6L));

        assertNull(claimService.claim(OPERATION_ID));

        verify(operationMapper, never()).claim(anyLong(), anyString(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void otherOperationTypeIsNotClaimed() {
        CorsOperation other = operation("PENDING", null, 4L);
        other.setOperationType("RENEW_ACCOUNT");
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(other);

        assertNull(claimService.claim(OPERATION_ID));

        verify(operationMapper, never()).claim(anyLong(), anyString(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dueAndStaleQueriesUsePasswordSpecificMapperMethods() {
        CorsOperation stale = operation("CLAIMED", NOW.minusMinutes(10), 9L);
        when(operationMapper.selectDuePasswordResetIds(NOW, 500)).thenReturn(List.of(OPERATION_ID));
        when(operationMapper.selectStalePasswordResetClaimed(NOW.minusMinutes(2), 100)).thenReturn(List.of(stale));
        when(stateService.recoverStaleClaim(stale)).thenReturn(true);

        assertEquals(List.of(OPERATION_ID), claimService.findDueOperationIds(900));
        assertEquals(1, claimService.recoverStaleClaims());

        verify(operationMapper).selectDuePasswordResetIds(NOW, 500);
        verify(operationMapper).selectStalePasswordResetClaimed(NOW.minusMinutes(2), 100);
    }

    private static CorsOperation operation(String status, LocalDateTime retryAt, long version) {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId("PWD-RS-17");
        operation.setOperationType(AccountPasswordResetConstants.OPERATION_TYPE);
        operation.setBizType(AccountPasswordResetConstants.BIZ_TYPE);
        operation.setBizId(ACTION_ID);
        operation.setServiceAccountId(SERVICE_ACCOUNT_ID);
        operation.setStatus(status);
        operation.setNextRetryAt(retryAt);
        operation.setRetryCount(1);
        operation.setVersion(version);
        return operation;
    }

    private static AccountPasswordAction action() {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(ACTION_ID);
        action.setRequestId("PWD-RS-17");
        action.setActionType("RESET");
        action.setServiceAccountId(SERVICE_ACCOUNT_ID);
        action.setStatus("PROCESSING");
        action.setVersion(2L);
        return action;
    }
}

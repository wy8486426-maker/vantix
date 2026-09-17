package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.config.CorsAccountRenewalProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRenewalClaimServiceTest {
    private static final long OPERATION_ID = 31L;
    private static final long RENEWAL_ID = 51L;
    private static final long ACCOUNT_ID = 61L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock private CorsOperationMapper operationMapper;
    @Mock private AccountRenewalMapper renewalMapper;
    @Mock private AccountRenewalStateService stateService;

    private CorsAccountRenewalProperties properties;
    private AccountRenewalClaimService claimService;

    @BeforeEach
    void setUp() {
        properties = new CorsAccountRenewalProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        claimService = new AccountRenewalClaimService(operationMapper, renewalMapper, stateService,
                properties, clock);
    }

    @Test
    void pendingClaimsReturnTheOriginalRenewalIdentity() {
        CorsOperation pending = operation("PENDING", null, 2L, 0);
        CorsOperation claimed = operation("CLAIMED", null, 3L, 0);
        AccountRenewal renewal = renewal(8L);
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(pending);
        when(operationMapper.claim(OPERATION_ID, "PENDING", 2L, NOW)).thenReturn(1);
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(claimed);
        when(renewalMapper.selectByIdForUpdate(RENEWAL_ID)).thenReturn(renewal);

        ClaimedAccountRenewal result = claimService.claim(OPERATION_ID);

        assertEquals("CLAIMED", result.operation().getStatus());
        assertEquals(renewal, result.renewal());
        verify(operationMapper).claim(OPERATION_ID, "PENDING", 2L, NOW);
    }

    @Test
    void retryWaitClaimsReturnTheOriginalRenewalIdentity() {
        CorsOperation retry = operation("RETRY_WAIT", NOW.minusSeconds(1), 4L, 2);
        CorsOperation claimed = operation("CLAIMED", NOW.minusSeconds(1), 5L, 2);
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(retry);
        when(operationMapper.claim(OPERATION_ID, "RETRY_WAIT", 4L, NOW)).thenReturn(1);
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(claimed);
        when(renewalMapper.selectByIdForUpdate(RENEWAL_ID)).thenReturn(renewal(9L));

        ClaimedAccountRenewal result = claimService.claim(OPERATION_ID);

        assertEquals("CLAIMED", result.operation().getStatus());
    }

    @Test
    void retryWaitNotYetDueCannotBeClaimed() {
        when(operationMapper.selectById(OPERATION_ID))
                .thenReturn(operation("RETRY_WAIT", NOW.plusSeconds(1), 4L, 0));

        assertNull(claimService.claim(OPERATION_ID));

        verify(operationMapper, never()).claim(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedCasDoesNotReturnClaim() {
        when(operationMapper.selectById(OPERATION_ID))
                .thenReturn(operation("PENDING", null, 2L, 0));
        when(operationMapper.claim(OPERATION_ID, "PENDING", 2L, NOW)).thenReturn(0);

        assertNull(claimService.claim(OPERATION_ID));
        verify(renewalMapper, never()).selectByIdForUpdate(RENEWAL_ID);
    }

    @Test
    void unrelatedOperationCannotBeClaimed() {
        CorsOperation other = operation("PENDING", null, 2L, 0);
        other.setOperationType("BATCH_CREATE_ACCOUNT");
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(other);

        assertNull(claimService.claim(OPERATION_ID));

        verify(operationMapper, never()).claim(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dueIdsAreBoundedAndStaleRecoveryIsDelegated() {
        when(operationMapper.selectDueRenewalIds(NOW, 500)).thenReturn(List.of(OPERATION_ID));
        CorsOperation stale = operation("CLAIMED", NOW.minusMinutes(10), 8L, 0);
        when(operationMapper.selectStaleRenewalClaimed(NOW.minusMinutes(2), 100)).thenReturn(List.of(stale));
        when(stateService.recoverStaleClaim(stale)).thenReturn(true);

        assertEquals(List.of(OPERATION_ID), claimService.findDueOperationIds(900));
        assertEquals(1, claimService.recoverStaleClaims());

        verify(operationMapper).selectDueRenewalIds(NOW, 500);
        verify(stateService).recoverStaleClaim(stale);
    }

    private static CorsOperation operation(String status, LocalDateTime retryAt, long version, int retryCount) {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId("RN-test-31");
        operation.setOperationType("RENEW_ACCOUNT");
        operation.setBizType("ACCOUNT_RENEWAL");
        operation.setBizId(RENEWAL_ID);
        operation.setServiceAccountId(ACCOUNT_ID);
        operation.setStatus(status);
        operation.setRetryCount(retryCount);
        operation.setNextRetryAt(retryAt);
        operation.setVersion(version);
        return operation;
    }

    private static AccountRenewal renewal(long version) {
        AccountRenewal renewal = new AccountRenewal();
        renewal.setId(RENEWAL_ID);
        renewal.setServiceAccountId(ACCOUNT_ID);
        renewal.setServiceCodeId(71L);
        renewal.setRequestId("RN-test-31");
        renewal.setStatus("PROCESSING");
        renewal.setVersion(version);
        return renewal;
    }
}

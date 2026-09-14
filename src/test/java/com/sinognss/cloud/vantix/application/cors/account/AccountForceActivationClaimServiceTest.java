package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountForceActivationClaimServiceTest {
    private static final long OPERATION_ID = 31L;
    private static final long ACCOUNT_ID = 51L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock
    private CorsOperationMapper operationMapper;

    private CorsForceActivationProperties properties;
    private AccountForceActivationClaimService claimService;

    @BeforeEach
    void setUp() {
        properties = new CorsForceActivationProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        claimService = new AccountForceActivationClaimService(operationMapper, properties, clock);
    }

    @Test
    void pendingClaimsWithoutQueryFirst() {
        CorsOperation pending = operation("PENDING", null, 2L);
        CorsOperation claimed = operation("CLAIMED", null, 3L);
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(pending, claimed);
        when(operationMapper.claim(OPERATION_ID, "PENDING", 2L, NOW)).thenReturn(1);

        ClaimedAccountForceActivation result = claimService.claim(OPERATION_ID);

        assertFalse(result.queryFirst());
        assertEquals("CLAIMED", result.operation().getStatus());
        verify(operationMapper).claim(OPERATION_ID, "PENDING", 2L, NOW);
    }

    @Test
    void retryWaitClaimsQueryFirst() {
        CorsOperation retry = operation("RETRY_WAIT", NOW.minusSeconds(1), 4L);
        CorsOperation claimed = operation("CLAIMED", NOW.minusSeconds(1), 5L);
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(retry, claimed);
        when(operationMapper.claim(OPERATION_ID, "RETRY_WAIT", 4L, NOW)).thenReturn(1);

        ClaimedAccountForceActivation result = claimService.claim(OPERATION_ID);

        assertTrue(result.queryFirst());
        assertEquals("CLAIMED", result.operation().getStatus());
    }

    @Test
    void retryWaitNotYetDueCannotBeClaimed() {
        CorsOperation retry = operation("RETRY_WAIT", NOW.plusSeconds(1), 4L);
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(retry);

        assertNull(claimService.claim(OPERATION_ID));
    }

    @Test
    void failedCasDoesNotReturnClaim() {
        CorsOperation pending = operation("PENDING", null, 2L);
        when(operationMapper.selectById(OPERATION_ID)).thenReturn(pending);
        when(operationMapper.claim(OPERATION_ID, "PENDING", 2L, NOW)).thenReturn(0);

        assertNull(claimService.claim(OPERATION_ID));
    }

    @Test
    void staleClaimRecoversIntoRetryWaitAndIsQueryFirstEligible() {
        CorsOperation stale = operation("CLAIMED", NOW.minusMinutes(10), 8L);
        when(operationMapper.selectStaleForceActivationClaimed(NOW.minusMinutes(2), 100))
                .thenReturn(List.of(stale));
        when(operationMapper.recoverClaimed(eq(OPERATION_ID), eq(8L), eq("RETRY_WAIT"), eq(1),
                eq(NOW), eq("CLAIM_TIMEOUT"), any(), eq(NOW))).thenReturn(1);

        assertEquals(1, claimService.recoverStaleClaims());

        verify(operationMapper).recoverClaimed(OPERATION_ID, 8L, "RETRY_WAIT", 1, NOW,
                "CLAIM_TIMEOUT",
                "Stale force-activation claim recovered; next attempt will query requestId", NOW);
    }

    @Test
    void staleClaimAtRetryLimitMovesToManualReview() {
        properties.setMaxRetries(0);
        CorsOperation stale = operation("CLAIMED", NOW.minusMinutes(10), 8L);
        when(operationMapper.selectStaleForceActivationClaimed(NOW.minusMinutes(2), 100))
                .thenReturn(List.of(stale));
        when(operationMapper.recoverClaimed(eq(OPERATION_ID), eq(8L), eq("MANUAL_REVIEW"), eq(1),
                eq(null), eq("CLAIM_TIMEOUT_EXHAUSTED"), any(), eq(NOW))).thenReturn(1);

        assertEquals(1, claimService.recoverStaleClaims());

        verify(operationMapper).recoverClaimed(OPERATION_ID, 8L, "MANUAL_REVIEW", 1, null,
                "CLAIM_TIMEOUT_EXHAUSTED",
                "Force activation requires manual review after claim timeout", NOW);
    }

    private static CorsOperation operation(String status, LocalDateTime nextRetryAt, long version) {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId("FA-test-31");
        operation.setOperationType("FORCE_ACTIVATE_ACCOUNT");
        operation.setBizType("ACCOUNT_FORCE_ACTIVATION");
        operation.setBizId(ACCOUNT_ID);
        operation.setServiceAccountId(ACCOUNT_ID);
        operation.setStatus(status);
        operation.setRetryCount(0);
        operation.setNextRetryAt(nextRetryAt);
        operation.setVersion(version);
        return operation;
    }
}

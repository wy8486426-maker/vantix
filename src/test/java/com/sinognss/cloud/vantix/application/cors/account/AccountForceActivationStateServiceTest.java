package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AccountForceActivationStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    private CorsOperationMapper operationMapper;
    private CorsForceActivationProperties properties;
    private AccountForceActivationStateService stateService;

    @BeforeEach
    void setUp() {
        operationMapper = mock(CorsOperationMapper.class);
        properties = new CorsForceActivationProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        stateService = new AccountForceActivationStateService(operationMapper, properties, clock);
    }

    @Test
    void retryUsesExponentialBackoffAndSanitizesRemoteError() {
        CorsOperation claimed = claimedOperation(4, 2);
        when(operationMapper.selectById(4L)).thenReturn(claimed);
        when(operationMapper.scheduleRetry(eq(4L), eq(7L), eq(3),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), eq(NOW))).thenReturn(1);

        assertTrue(stateService.retryOrMarkManualReview(claimed, "UPSTREAM TIMEOUT", "a".repeat(1100)));

        ArgumentCaptor<LocalDateTime> nextRetryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> errorCode = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorMessage = ArgumentCaptor.forClass(String.class);
        verify(operationMapper).scheduleRetry(eq(4L), eq(7L), eq(3), nextRetryAt.capture(),
                errorCode.capture(), errorMessage.capture(), eq(NOW));
        assertEquals(NOW.plusMinutes(2), nextRetryAt.getValue());
        assertEquals("FORCE_ACTIVATION_UNKNOWN", errorCode.getValue());
        assertEquals(1024, errorMessage.getValue().length());
        verify(operationMapper, never()).markManualReview(eq(4L), eq(7L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(NOW));
    }

    @Test
    void exhaustedRetriesMoveToManualReview() {
        properties.setMaxRetries(2);
        CorsOperation claimed = claimedOperation(5, 2);
        when(operationMapper.selectById(5L)).thenReturn(claimed);
        when(operationMapper.markManualReview(5L, 7L, "REMOTE_UNKNOWN",
                "remote result unknown", NOW)).thenReturn(1);

        assertTrue(stateService.retryOrMarkManualReview(
                claimed, "REMOTE_UNKNOWN", "remote result unknown"));

        verify(operationMapper).markManualReview(5L, 7L, "REMOTE_UNKNOWN",
                "remote result unknown", NOW);
        verify(operationMapper, never()).scheduleRetry(eq(5L), eq(7L),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(NOW));
    }

    @Test
    void staleClaimVersionCannotChangeOperation() {
        CorsOperation claimed = claimedOperation(6, 0);
        CorsOperation current = claimedOperation(6, 0);
        current.setVersion(8L);
        when(operationMapper.selectById(6L)).thenReturn(current);

        assertFalse(stateService.markSucceeded(claimed));

        verify(operationMapper).selectById(6L);
        verifyNoMoreInteractions(operationMapper);
    }

    @Test
    void currentClaimCanBeMarkedSucceeded() {
        CorsOperation claimed = claimedOperation(7, 0);
        when(operationMapper.selectById(7L)).thenReturn(claimed);
        when(operationMapper.markSucceeded(7L, 7L, NOW)).thenReturn(1);

        assertTrue(stateService.markSucceeded(claimed));

        verify(operationMapper).markSucceeded(7L, 7L, NOW);
    }

    private static CorsOperation claimedOperation(long id, int retryCount) {
        CorsOperation operation = new CorsOperation();
        operation.setId(id);
        operation.setRequestId("FA-request-" + id);
        operation.setOperationType("FORCE_ACTIVATE_ACCOUNT");
        operation.setBizType("ACCOUNT_FORCE_ACTIVATION");
        operation.setBizId(40L + id);
        operation.setServiceAccountId(40L + id);
        operation.setStatus("CLAIMED");
        operation.setRetryCount(retryCount);
        operation.setVersion(7L);
        return operation;
    }
}

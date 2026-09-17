package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CorsOperationClaimServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 8, 0);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final ExchangeBatchMapper batchMapper = mock(ExchangeBatchMapper.class);
    private final CorsOperationProperties properties = new CorsOperationProperties();
    private final CorsOperationClaimService service = new CorsOperationClaimService(operationMapper,
            batchMapper, properties, Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"),
            ZoneId.of("Asia/Shanghai")));

    @Test
    void firstClaimPersistsFirstAttemptAtOnlyOnce() {
        CorsOperation pending = operation("PENDING", null);
        CorsOperation claimed = operation("CLAIMED", NOW);
        when(operationMapper.selectById(41L)).thenReturn(pending, claimed);
        when(operationMapper.claim(41L, "PENDING", 3L, NOW)).thenReturn(1);

        ClaimedCorsOperation result = service.claim(41L);

        assertEquals(claimed, result.operation());
        verify(operationMapper).claim(41L, "PENDING", 3L, NOW);
        verify(operationMapper, never()).markPendingManualReview(anyLong(), anyLong(), anyString(),
                anyString(), any());
    }

    @Test
    void expiredRetryIsMovedToManualReviewWithoutAnotherCorsCallClaim() {
        CorsOperation expired = operation("RETRY_WAIT", NOW.minusMinutes(2));
        when(operationMapper.selectById(41L)).thenReturn(expired);
        when(operationMapper.markPendingManualReview(eq(41L), eq(3L),
                eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW))).thenReturn(1);
        when(batchMapper.markManualReview(eq(9L), eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW)))
                .thenReturn(1);

        assertNull(service.claim(41L));

        verify(operationMapper, never()).claim(anyLong(), anyString(), anyLong(), any());
        verify(operationMapper).markPendingManualReview(eq(41L), eq(3L),
                eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW));
        verify(batchMapper).markManualReview(eq(9L), eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW));
    }

    @Test
    void competingWorkersOnlyOneWinsTheCasClaim() {
        CorsOperation pending = operation("PENDING", null);
        CorsOperation claimed = operation("CLAIMED", NOW);
        when(operationMapper.selectById(41L)).thenReturn(pending, claimed, pending);
        when(operationMapper.claim(41L, "PENDING", 3L, NOW)).thenReturn(1, 0);

        assertNotNull(service.claim(41L));
        assertNull(service.claim(41L));

        verify(operationMapper, times(2)).claim(41L, "PENDING", 3L, NOW);
    }

    private static CorsOperation operation(String status, LocalDateTime firstAttemptAt) {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId("EXCHANGE-cors-1");
        operation.setOperationType("BATCH_CREATE_ACCOUNT");
        operation.setBizType("EXCHANGE_BATCH");
        operation.setBizId(9L);
        operation.setStatus(status);
        operation.setRetryCount(0);
        operation.setNextRetryAt(null);
        operation.setFirstAttemptAt(firstAttemptAt);
        operation.setVersion(3L);
        return operation;
    }
}

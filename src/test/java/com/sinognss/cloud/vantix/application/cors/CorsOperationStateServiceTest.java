package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CorsOperationStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 8, 0);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final ExchangeBatchMapper batchMapper = mock(ExchangeBatchMapper.class);
    private final ExchangeDetailMapper detailMapper = mock(ExchangeDetailMapper.class);
    private final ServiceCodeMapper serviceCodeMapper = mock(ServiceCodeMapper.class);
    private final CorsOperationProperties properties = new CorsOperationProperties();
    private final CorsOperationStateService service;
    private final CorsOperation claimed = operation();

    CorsOperationStateServiceTest() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        properties.setRetryBaseDelay(Duration.ofSeconds(15));
        service = new CorsOperationStateService(operationMapper, batchMapper, detailMapper,
                serviceCodeMapper, properties, clock);
    }

    @BeforeEach
    void setUp() {
        reset(operationMapper, batchMapper, detailMapper, serviceCodeMapper);
        when(operationMapper.selectById(41L)).thenReturn(claimed);
    }

    @Test
    void unknownOutcomeSchedulesRetryAndLeavesReservedCodesUntouched() {
        when(operationMapper.scheduleRetry(eq(41L), eq(3L), eq(1), any(LocalDateTime.class),
                eq("CORS_TIMEOUT"), eq("unknown"), any(LocalDateTime.class))).thenReturn(1);

        boolean changed = service.retryOrMarkManualReview(claimed, "CORS_TIMEOUT", "unknown");

        assertEquals(true, changed);
        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(operationMapper).scheduleRetry(eq(41L), eq(3L), eq(1), retryAt.capture(),
                eq("CORS_TIMEOUT"), eq("unknown"), eq(NOW));
        assertEquals(NOW.plusSeconds(15), retryAt.getValue());
        verify(operationMapper, never()).markManualReview(anyLong(), anyLong(), anyString(),
                anyString(), any(LocalDateTime.class));
        verify(serviceCodeMapper, never()).releaseExchangeCodes(anyList(), anyString(),
                any(LocalDateTime.class));
        verifyNoInteractions(batchMapper, detailMapper);
    }

    @Test
    void definitiveRejectReleasesAllReservedCodesAndFailsBatchDetailsOperation() {
        ExchangeBatch batch = new ExchangeBatch();
        batch.setId(9L);
        batch.setRequestId("request-1");
        batch.setStatus(ExchangeStatus.PROCESSING);
        batch.setQuantity(2);
        ExchangeDetail first = detail(101L, 1);
        ExchangeDetail second = detail(102L, 2);
        when(batchMapper.selectByRequestIdForUpdate("request-1")).thenReturn(batch);
        when(detailMapper.selectByBatchId(9L)).thenReturn(List.of(first, second));
        when(serviceCodeMapper.releaseExchangeCodes(List.of(101L, 102L), "request-1", NOW)).thenReturn(2);
        when(detailMapper.failByBatchId(9L, "INVALID_ARGUMENT", "no side effect", NOW)).thenReturn(2);
        when(batchMapper.fail(9L, "INVALID_ARGUMENT", "no side effect", NOW)).thenReturn(1);
        when(operationMapper.markFailed(41L, 3L, "INVALID_ARGUMENT", "no side effect", NOW)).thenReturn(1);

        boolean changed = service.failDefinitively(claimed, "INVALID_ARGUMENT", "no side effect");

        assertEquals(true, changed);
        verify(serviceCodeMapper).releaseExchangeCodes(List.of(101L, 102L), "request-1", NOW);
        verify(detailMapper).failByBatchId(9L, "INVALID_ARGUMENT", "no side effect", NOW);
        verify(batchMapper).fail(9L, "INVALID_ARGUMENT", "no side effect", NOW);
        verify(operationMapper).markFailed(41L, 3L, "INVALID_ARGUMENT", "no side effect", NOW);
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId("request-1");
        operation.setBizType("EXCHANGE_BATCH");
        operation.setBizId(9L);
        operation.setStatus("CLAIMED");
        operation.setRetryCount(0);
        operation.setVersion(3L);
        return operation;
    }

    private static ExchangeDetail detail(Long serviceCodeId, int index) {
        ExchangeDetail detail = new ExchangeDetail();
        detail.setExchangeBatchId(9L);
        detail.setDetailIndex(index);
        detail.setServiceCodeId(serviceCodeId);
        detail.setStatus(ExchangeStatus.PROCESSING);
        return detail;
    }
}

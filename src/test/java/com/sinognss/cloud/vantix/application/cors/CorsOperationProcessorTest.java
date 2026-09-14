package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeFinalizeService;
import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CorsOperationProcessorTest {
    private final CorsOperationClaimService claimService = mock(CorsOperationClaimService.class);
    private final CorsOperationStateService stateService = mock(CorsOperationStateService.class);
    private final ServiceCodeExchangeFinalizeService finalizeService =
            mock(ServiceCodeExchangeFinalizeService.class);
    private final ExchangeBatchMapper batchMapper = mock(ExchangeBatchMapper.class);
    private final CorsAccountGateway gateway = mock(CorsAccountGateway.class);
    private final CorsOperationProperties properties = new CorsOperationProperties();

    private CorsOperation operation;
    private ExchangeBatch batch;
    private CorsOperationProcessor processor;

    @BeforeEach
    void setUp() {
        operation = operation();
        batch = batch();
        when(claimService.claim(41L)).thenReturn(new ClaimedCorsOperation(operation, false));
        when(batchMapper.selectByRequestId("request-1")).thenReturn(batch);
        processor = new CorsOperationProcessor(claimService, stateService, finalizeService,
                batchMapper, gateway, properties);
    }

    @Test
    void unknownCreateResponseSchedulesRetryAndNeverReleasesReservation() {
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(
                CorsBatchResult.outcome(CorsOutcome.UNKNOWN, "request-1", "TIMEOUT", "response timed out"));

        processor.process(41L);

        verify(stateService).retryOrMarkManualReview(operation, "TIMEOUT", "response timed out");
        verify(stateService, never()).failDefinitively(any(), any(), any());
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void retryQueriesFirstThenPostsWithTheOriginalRequestIdWhenNotFound() {
        when(claimService.claim(41L)).thenReturn(new ClaimedCorsOperation(operation, true));
        CorsBatchResult success = new CorsBatchResult(CorsOutcome.SUCCESS, "request-1",
                List.of(), null, null);
        when(gateway.queryBatch("request-1")).thenReturn(
                CorsBatchResult.outcome(CorsOutcome.NOT_FOUND, "request-1", null, null));
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(success);

        processor.process(41L);

        InOrder order = inOrder(gateway);
        order.verify(gateway).queryBatch("request-1");
        ArgumentCaptor<CorsBatchCreateRequest> request = ArgumentCaptor.forClass(CorsBatchCreateRequest.class);
        order.verify(gateway).createBatch(request.capture());
        assertEquals("request-1", request.getValue().requestId());
        assertEquals(2, request.getValue().quantity());
        assertEquals(DurationUnit.MONTH, request.getValue().durationUnit());
        verify(finalizeService).finalizeSuccess(41L, operation.getVersion(), success);
        verify(stateService, never()).failDefinitively(any(), any(), any());
    }

    @Test
    void definitiveRejectDelegatesToAtomicFailureAndReleasePath() {
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(
                CorsBatchResult.outcome(CorsOutcome.DEFINITIVE_REJECT, "request-1",
                        "INVALID_ARGUMENT", "invalid prefix"));

        processor.process(41L);

        verify(stateService).failDefinitively(operation, "INVALID_ARGUMENT", "invalid prefix");
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), any());
    }

    @Test
    void localFinalizeFailureMovesClaimedOperationToManualReview() {
        CorsBatchResult success = new CorsBatchResult(CorsOutcome.SUCCESS, "request-1",
                List.of(), null, null);
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(success);
        when(finalizeService.finalizeSuccess(41L, operation.getVersion(), success))
                .thenThrow(new IllegalStateException("database unavailable"));

        processor.process(41L);

        verify(stateService).markManualReview(operation, "LOCAL_FINALIZE_FAILED",
                "CORS 账号已创建，但 Vantix 本地入账失败，需要人工复核");
        verify(stateService, never()).failDefinitively(any(), any(), any());
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

    private static ExchangeBatch batch() {
        ExchangeBatch batch = new ExchangeBatch();
        batch.setId(9L);
        batch.setRequestId("request-1");
        batch.setStatus(ExchangeStatus.PROCESSING);
        batch.setDurationValue(1);
        batch.setDurationUnit("MONTH");
        batch.setQuantity(2);
        batch.setAccountPrefix("demo");
        return batch;
    }
}

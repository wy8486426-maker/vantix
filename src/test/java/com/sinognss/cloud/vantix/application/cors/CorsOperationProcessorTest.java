package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeFinalizeService;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsAddAccountData;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CorsOperationProcessorTest {
    private static final String CORS_REQUEST_ID = "EXCHANGE-cors-1";
    private final CorsOperationClaimService claimService = mock(CorsOperationClaimService.class);
    private final CorsOperationStateService stateService = mock(CorsOperationStateService.class);
    private final ServiceCodeExchangeFinalizeService finalizeService =
            mock(ServiceCodeExchangeFinalizeService.class);
    private final ExchangeBatchMapper batchMapper = mock(ExchangeBatchMapper.class);
    private final CorsAccountGateway gateway = mock(CorsAccountGateway.class);

    private CorsOperation operation;
    private ExchangeBatch batch;
    private CorsOperationProcessor processor;

    @BeforeEach
    void setUp() {
        operation = operation();
        batch = batch();
        when(claimService.claim(41L)).thenReturn(new ClaimedCorsOperation(operation));
        when(batchMapper.selectById(9L)).thenReturn(batch);
        processor = new CorsOperationProcessor(claimService, stateService, finalizeService,
                batchMapper, gateway);
    }

    @Test
    void buildsTheConfirmedRequestFromTheFrozenBatchSnapshot() {
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(
                CorsBatchResult.outcome(CorsOutcome.UNKNOWN, CORS_REQUEST_ID, "TIMEOUT", "response timed out"));

        processor.process(41L);

        ArgumentCaptor<CorsBatchCreateRequest> request = ArgumentCaptor.forClass(CorsBatchCreateRequest.class);
        verify(gateway).createBatch(request.capture());
        CorsBatchCreateRequest value = request.getValue();
        assertEquals(CORS_REQUEST_ID, value.requestId());
        assertEquals(2, value.addNum());
        assertEquals(0, value.accountType());
        assertEquals(30, value.durationType());
        assertEquals("AB12", value.accountName());
        assertEquals(0, value.nameType());
        assertEquals(12, value.silenceType());
        assertEquals(1, value.activeType());
        assertEquals(123L, value.dealerId());
        assertEquals(0, value.normalType());
        verify(stateService).retryOrMarkManualReview(operation, "TIMEOUT", "response timed out");
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void completeCorsNamesFinalizeTheExchange() {
        CorsBatchResult success = CorsBatchResult.success(CORS_REQUEST_ID,
                new CorsAddAccountData("corsAdd", List.of("AB12000001", "AB12000002")));
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(success);

        processor.process(41L);

        verify(finalizeService).finalizeSuccess(41L, operation.getVersion(), success);
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), any());
        verify(stateService, never()).failDefinitively(any(), any(), any());
    }

    @Test
    void codeZeroWithNullDataStaysRetryable() {
        when(gateway.createBatch(any(CorsBatchCreateRequest.class)))
                .thenReturn(CorsBatchResult.success(CORS_REQUEST_ID, null));

        processor.process(41L);

        verify(stateService).retryOrMarkManualReview(operation, "CORS_RESULT_PENDING",
                "CORS 创建请求已成功接受，但结果详情暂未获取到");
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void incompleteOrBlankCorsNamesStayRetryableAndCannotFinalize() {
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(
                CorsBatchResult.success(CORS_REQUEST_ID,
                        new CorsAddAccountData("corsAdd", List.of("AB12000001", " "))));

        processor.process(41L);

        verify(stateService).retryOrMarkManualReview(operation, "CORS_RESULT_INCOMPLETE",
                "CORS 返回的 corsNameList 不完整或包含空账号名");
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void definitiveRejectDelegatesToAtomicFailureAndReleasePath() {
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(
                CorsBatchResult.outcome(CorsOutcome.DEFINITIVE_REJECT, CORS_REQUEST_ID,
                        "5302", "用户名称重复"));

        processor.process(41L);

        verify(stateService).failDefinitively(operation, "5302", "用户名称重复");
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), any());
    }

    @Test
    void retryReusesThePersistedCorsRequestIdAndCompletesOnlyOnce() {
        CorsBatchResult success = CorsBatchResult.success(CORS_REQUEST_ID,
                new CorsAddAccountData("corsAdd", List.of("AB12000001", "AB12000002")));
        when(claimService.claim(41L)).thenReturn(
                new ClaimedCorsOperation(operation), new ClaimedCorsOperation(operation));
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(
                CorsBatchResult.outcome(CorsOutcome.UNKNOWN, CORS_REQUEST_ID, "TRANSPORT_ERROR", "timeout"),
                success);

        processor.process(41L);
        processor.process(41L);

        ArgumentCaptor<CorsBatchCreateRequest> requests = ArgumentCaptor.forClass(CorsBatchCreateRequest.class);
        verify(gateway, times(2)).createBatch(requests.capture());
        assertEquals(CORS_REQUEST_ID, requests.getAllValues().get(0).requestId());
        assertEquals(CORS_REQUEST_ID, requests.getAllValues().get(1).requestId());
        verify(finalizeService, times(1)).finalizeSuccess(41L, operation.getVersion(), success);
    }

    @Test
    void localFinalizeFailureMovesClaimedOperationToManualReview() {
        CorsBatchResult success = CorsBatchResult.success(CORS_REQUEST_ID,
                new CorsAddAccountData("corsAdd", List.of("AB12000001", "AB12000002")));
        when(gateway.createBatch(any(CorsBatchCreateRequest.class))).thenReturn(success);
        when(finalizeService.finalizeSuccess(41L, operation.getVersion(), success))
                .thenThrow(new IllegalStateException("database unavailable"));

        processor.process(41L);

        verify(stateService).markManualReview(operation, "LOCAL_FINALIZE_FAILED",
                "CORS 账号已创建，但 Vantix 本地入账失败，需要人工复核");
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId(CORS_REQUEST_ID);
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
        batch.setRequestId("external-exchange-request");
        batch.setOwnerCompanyId(123L);
        batch.setStatus(ExchangeStatus.PROCESSING);
        batch.setSpecCode("SPEC-1");
        batch.setServiceType("STANDARD");
        batch.setDurationDays(30);
        batch.setAccountSilenceDays(12);
        batch.setQuantity(2);
        batch.setAccountPrefix("AB12");
        return batch;
    }
}

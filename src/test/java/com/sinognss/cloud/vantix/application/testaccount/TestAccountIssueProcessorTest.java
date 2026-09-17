package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TestAccountIssueProcessorTest {
    private final TestAccountIssueClaimService claimService = mock(TestAccountIssueClaimService.class);
    private final TestAccountIssueStateService stateService = mock(TestAccountIssueStateService.class);
    private final TestAccountIssueFinalizeService finalizeService = mock(TestAccountIssueFinalizeService.class);
    private final TestAccountIssueBatchMapper batchMapper = mock(TestAccountIssueBatchMapper.class);
    private final CorsAccountGateway gateway = mock(CorsAccountGateway.class);
    private final TestAccountIssueProcessor processor = new TestAccountIssueProcessor(
            claimService, stateService, finalizeService, batchMapper, gateway);

    @Test
    void buildsCorsRequestFromBatchSnapshotAndReusesStableCorsRequestId() {
        CorsOperation operation = operation();
        TestAccountIssueBatch batch = batch();
        when(claimService.claim(41L)).thenReturn(new ClaimedTestAccountIssue(operation));
        when(batchMapper.selectById(9L)).thenReturn(batch);
        when(gateway.createBatch(any())).thenReturn(CorsBatchResult.outcome(
                CorsOutcome.UNKNOWN, "TEST_cors-1", "TIMEOUT", "timeout"));

        processor.process(41L);
        processor.process(41L);

        var captor = org.mockito.ArgumentCaptor.forClass(CorsBatchCreateRequest.class);
        verify(gateway, times(2)).createBatch(captor.capture());
        assertEquals(List.of("TEST_cors-1", "TEST_cors-1"),
                captor.getAllValues().stream().map(CorsBatchCreateRequest::requestId).toList());
        CorsBatchCreateRequest request = captor.getAllValues().get(0);
        assertEquals(2, request.addNum());
        assertEquals(365, request.durationType());
        assertEquals("TEST", request.accountName());
        assertEquals(30, request.silenceType());
        assertEquals(123L, request.dealerId());
        verify(stateService, times(2)).retryOrMarkManualReview(eq(operation), eq("TIMEOUT"), eq("timeout"));
        verifyNoInteractions(finalizeService);
    }

    @Test
    void codeZeroNullDataStaysInRetryWithoutLocalAccounts() {
        CorsOperation operation = operation();
        when(claimService.claim(41L)).thenReturn(new ClaimedTestAccountIssue(operation));
        when(batchMapper.selectById(9L)).thenReturn(batch());
        when(gateway.createBatch(any())).thenReturn(CorsBatchResult.success("TEST_cors-1", null));

        processor.process(41L);

        verify(stateService).retryOrMarkManualReview(operation, "CORS_RESULT_PENDING",
                "CORS 创建请求已成功接受，但结果详情暂未获取到");
        verifyNoInteractions(finalizeService);
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId("TEST_cors-1");
        operation.setOperationType(TestAccountIssueConstants.OPERATION_TYPE);
        operation.setBizType(TestAccountIssueConstants.BIZ_TYPE);
        operation.setBizId(9L);
        operation.setStatus(TestAccountIssueConstants.CLAIMED);
        operation.setVersion(3L);
        return operation;
    }

    private static TestAccountIssueBatch batch() {
        TestAccountIssueBatch batch = new TestAccountIssueBatch();
        batch.setId(9L);
        batch.setOwnerCompanyId(123L);
        batch.setSpecCode("S1");
        batch.setDisplayName("测试规格");
        batch.setServiceType("STANDARD");
        batch.setDurationDays(365);
        batch.setAccountSilenceDays(30);
        batch.setQuantity(2);
        batch.setAccountPrefix("TEST");
        batch.setStatus(TestAccountIssueConstants.PROCESSING);
        return batch;
    }
}

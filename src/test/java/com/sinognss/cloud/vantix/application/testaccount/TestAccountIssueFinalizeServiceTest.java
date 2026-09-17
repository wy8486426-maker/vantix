package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAddAccountData;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsCreatedAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TestAccountIssueFinalizeServiceTest {
    private final TestAccountIssueBatchMapper batchMapper = mock(TestAccountIssueBatchMapper.class);
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final TestAccountIssueFinalizeService service = new TestAccountIssueFinalizeService(
            batchMapper, accountMapper, operationMapper,
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void finalizesTestAccountsWithoutExchangeProvenanceOrServiceCodeSideEffects() {
        CorsOperation operation = operation();
        when(operationMapper.selectById(41L)).thenReturn(operation);
        when(operationMapper.selectByIdForUpdate(41L)).thenReturn(operation);
        when(batchMapper.selectByIdForUpdate(9L)).thenReturn(batch());
        when(accountMapper.insertBatch(anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());
        when(batchMapper.complete(eq(9L), any())).thenReturn(1);
        when(operationMapper.markSucceeded(eq(41L), eq(3L), any())).thenReturn(1);

        assertTrue(service.finalizeSuccess(41L, 3L, CorsBatchResult.success("TEST_cors-1",
                new CorsAddAccountData(List.of(new CorsCreatedAccount(10001L, "TEST000001"),
                        new CorsCreatedAccount(10002L, "TEST000002"))))));

        ArgumentCaptor<List<ServiceAccount>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(accountMapper).insertBatch(captor.capture());
        assertEquals(List.of(AccountSource.TEST, AccountSource.TEST),
                captor.getValue().stream().map(ServiceAccount::getAccountSource).toList());
        assertTrue(captor.getValue().stream().allMatch(account -> account.getSourceServiceCodeId() == null
                && account.getExchangeBatchId() == null && account.getExchangeDetailId() == null
                && account.getExchangeAt() == null && account.getTestIssueBatchId().equals(9L)
                && account.getHistoryImportBatchId() == null));
        verify(batchMapper).complete(eq(9L), any());
        verify(operationMapper).markSucceeded(eq(41L), eq(3L), any());
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId("TEST_cors-1");
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
        batch.setServiceType("STANDARD");
        batch.setDurationDays(365);
        batch.setAccountSilenceDays(30);
        batch.setQuantity(2);
        batch.setStatus(TestAccountIssueConstants.PROCESSING);
        return batch;
    }
}

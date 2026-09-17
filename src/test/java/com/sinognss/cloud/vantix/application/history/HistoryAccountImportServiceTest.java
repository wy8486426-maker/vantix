package com.sinognss.cloud.vantix.application.history;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.account.HistoryAccountImportBatch;
import com.sinognss.cloud.vantix.infrastructure.mapper.HistoryAccountImportBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryAccountImportServiceTest {
    private final HistoryAccountImportTransaction transaction = mock(HistoryAccountImportTransaction.class);
    private final HistoryAccountImportBatchMapper batchMapper = mock(HistoryAccountImportBatchMapper.class);
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final GenerationProperties generationProperties = new GenerationProperties();
    private HistoryAccountImportService service;

    @BeforeEach
    void setUp() {
        service = new HistoryAccountImportService(transaction, batchMapper, accountMapper,
                userHolder, generationProperties);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
    }

    @Test
    void identityDuplicateFromDifferentRequestIsMappedToBusinessError() {
        HistoryAccountImportCommand command = command("H-identity");
        when(batchMapper.selectByRequestId(command.requestId())).thenReturn(null);
        when(transaction.importBatch(eq(command), any(String.class), any())).thenThrow(
                new DuplicateKeyException("uk_service_account_cors_id"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.importAccounts(command));

        assertEquals(ErrorCode.HISTORY_IMPORT_IDENTITY_CONFLICT, exception.getVantixErrorCode());
        verify(batchMapper, times(2)).selectByRequestId(command.requestId());
    }

    @Test
    void sameRequestAndPayloadDuplicateReturnsConcurrentBatch() {
        HistoryAccountImportCommand command = command("H-same");
        String payloadHash = HistoryImportPayloadHash.calculate(command);
        HistoryAccountImportBatch concurrent = batch(command.requestId(), payloadHash, "PROCESSING");
        when(batchMapper.selectByRequestId(command.requestId())).thenReturn(null, concurrent);
        when(transaction.importBatch(eq(command), eq(payloadHash), any())).thenThrow(
                new DuplicateKeyException("uk_history_account_import_request"));

        HistoryAccountImportView result = service.importAccounts(command);

        assertEquals(command.requestId(), result.requestId());
        assertEquals(concurrent.getImportBatchNo(), result.importBatchNo());
    }

    @Test
    void sameRequestWithDifferentPayloadRemainsIdempotencyConflict() {
        HistoryAccountImportCommand command = command("H-conflict");
        HistoryAccountImportBatch concurrent = batch(command.requestId(), "different-payload", "PROCESSING");
        when(batchMapper.selectByRequestId(command.requestId())).thenReturn(null, concurrent);
        when(transaction.importBatch(eq(command), any(String.class), any())).thenThrow(
                new DuplicateKeyException("uk_history_account_import_request"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.importAccounts(command));

        assertEquals(ErrorCode.HISTORY_IMPORT_IDEMPOTENCY_CONFLICT, exception.getVantixErrorCode());
    }

    private static HistoryAccountImportCommand command(String requestId) {
        return new HistoryAccountImportCommand(requestId, 123L, "OLD",
                List.of(new HistoryAccountIdentity(10001L, "legacy-1")));
    }

    private static HistoryAccountImportBatch batch(String requestId, String payloadHash, String status) {
        HistoryAccountImportBatch batch = new HistoryAccountImportBatch();
        batch.setId(8L);
        batch.setRequestId(requestId);
        batch.setImportBatchNo("HISTORY-8");
        batch.setPayloadHash(payloadHash);
        batch.setStatus(status);
        return batch;
    }
}

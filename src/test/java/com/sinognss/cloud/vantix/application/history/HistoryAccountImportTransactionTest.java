package com.sinognss.cloud.vantix.application.history;

import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.account.HistoryAccountImportBatch;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.HistoryAccountImportBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HistoryAccountImportTransactionTest {
    private final HistoryAccountImportBatchMapper batchMapper = mock(HistoryAccountImportBatchMapper.class);
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final ServiceDurationConfigMapper durationMapper = mock(ServiceDurationConfigMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final HistoryAccountImportTransaction transaction = new HistoryAccountImportTransaction(
            batchMapper, accountMapper, durationMapper, companyMapper,
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void allowsDisabledHistoricalSpecAndImportsWithoutCorsOperation() {
        when(batchMapper.selectByRequestId("H-1")).thenReturn(null);
        when(companyMapper.selectCount(any())).thenReturn(1L);
        ServiceDurationConfig spec = new ServiceDurationConfig();
        spec.setSpecCode("OLD");
        spec.setServiceType("STANDARD");
        spec.setDurationDays(30);
        spec.setAccountSilenceDays(12);
        spec.setEnabled(false);
        when(durationMapper.selectBySpecCodes(List.of("OLD"))).thenReturn(List.of(spec));
        when(batchMapper.insert(any(HistoryAccountImportBatch.class))).thenAnswer(invocation -> {
            ((HistoryAccountImportBatch) invocation.getArgument(0)).setId(8L);
            return 1;
        });
        when(accountMapper.selectByCorsAccountIds(anyList())).thenReturn(List.of());
        when(accountMapper.selectByAccountNames(anyList())).thenReturn(List.of());
        when(accountMapper.insertBatch(anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());
        when(batchMapper.complete(eq(8L), any())).thenReturn(1);

        HistoryAccountImportBatch result = transaction.importBatch(command(), "hash",
                new OperatorIdentity(7L, "operator"));

        assertEquals(8L, result.getId());
        ArgumentCaptor<List<ServiceAccount>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(accountMapper).insertBatch(captor.capture());
        ServiceAccount account = captor.getValue().get(0);
        assertEquals(AccountSource.HISTORY_IMPORT, account.getAccountSource());
        assertEquals("10001", account.getCorsAccountId());
        assertEquals("legacy-1", account.getAccount());
        assertEquals(8L, account.getHistoryImportBatchId());
        assertEquals(null, account.getExchangeAt());
    }

    @Test
    void rejectsDuplicateIdentityInsideOneImportBatchBeforeDatabaseInsert() {
        when(batchMapper.selectByRequestId("H-1")).thenReturn(null);
        when(companyMapper.selectCount(any())).thenReturn(1L);
        ServiceDurationConfig spec = new ServiceDurationConfig();
        spec.setSpecCode("OLD");
        spec.setServiceType("STANDARD");
        spec.setDurationDays(30);
        spec.setAccountSilenceDays(12);
        when(durationMapper.selectBySpecCodes(List.of("OLD"))).thenReturn(List.of(spec));

        HistoryAccountImportCommand duplicate = new HistoryAccountImportCommand("H-1", 123L, "OLD",
                List.of(new HistoryAccountIdentity(10001L, "legacy-1"),
                        new HistoryAccountIdentity(10001L, "legacy-2")));

        assertThrows(RuntimeException.class, () -> transaction.importBatch(duplicate, "hash",
                new OperatorIdentity(7L, "operator")));
        verify(accountMapper, never()).insertBatch(anyList());
    }

    private static HistoryAccountImportCommand command() {
        return new HistoryAccountImportCommand("H-1", 123L, "OLD",
                List.of(new HistoryAccountIdentity(10001L, "legacy-1"),
                        new HistoryAccountIdentity(10002L, "legacy-2")));
    }
}

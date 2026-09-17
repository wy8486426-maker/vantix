package com.sinognss.cloud.vantix.application.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAddAccountData;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsCreatedAccount;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ServiceCodeExchangeFinalizeServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 8, 0);
    private static final String CORS_REQUEST_ID = "EXCHANGE-cors-1";
    private final ExchangeBatchMapper batchMapper = mock(ExchangeBatchMapper.class);
    private final ExchangeDetailMapper detailMapper = mock(ExchangeDetailMapper.class);
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final ServiceCodeMapper serviceCodeMapper = mock(ServiceCodeMapper.class);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ServiceCodeExchangeFinalizeService service;
    private CorsOperation operation;
    private ExchangeBatch batch;
    private ExchangeDetail firstDetail;
    private ExchangeDetail secondDetail;

    ServiceCodeExchangeFinalizeServiceTest() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ServiceCodeExchangeFinalizeService(batchMapper, detailMapper, accountMapper,
                serviceCodeMapper, operationMapper, objectMapper, clock);
    }

    @BeforeEach
    void setUp() throws Exception {
        operation = operation();
        batch = batch();
        firstDetail = detail(101L, 1001L, 1,
                new ExchangeCodeSnapshot(101L, "CODE-101", 7L, 55L, "PRO", "STANDARD", 30, 360,
                        LocalDateTime.of(2027, 1, 1, 0, 0)));
        secondDetail = detail(102L, 1002L, 2,
                new ExchangeCodeSnapshot(102L, "CODE-102", 7L, 55L, "PRO", "STANDARD", 30, 360,
                        LocalDateTime.of(2027, 1, 1, 0, 0)));
        when(operationMapper.selectById(41L)).thenReturn(operation);
        when(batchMapper.selectByIdForUpdate(9L)).thenReturn(batch);
        when(detailMapper.selectByBatchId(9L)).thenReturn(List.of(firstDetail, secondDetail));
        when(accountMapper.insertBatch(anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());
        when(detailMapper.completeBatchDetails(eq(9L), anyList(), eq(NOW))).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(1)).size());
        when(serviceCodeMapper.consumeForExchange(anyList(), eq("external-exchange-request"), eq(NOW)))
                .thenReturn(2);
        when(batchMapper.complete(9L, NOW)).thenReturn(1);
        when(operationMapper.markSucceeded(41L, 3L, NOW)).thenReturn(1);
    }

    @Test
    void persistsTheCompleteCorsAccountsAndConsumesTheExchangeOnce() {
        CorsBatchResult response = success("AB12000002", "AB12000001");

        assertTrue(service.finalizeSuccess(41L, 3L, response));

        ArgumentCaptor<List<ServiceAccount>> accountCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(accountMapper).insertBatch(accountCaptor.capture());
        List<ServiceAccount> accounts = accountCaptor.getValue();
        assertEquals(List.of("AB12000002", "AB12000001"),
                accounts.stream().map(ServiceAccount::getAccount).toList());
        assertEquals(List.of("10001", "10002"),
                accounts.stream().map(ServiceAccount::getCorsAccountId).toList());
        assertEquals(List.of(AccountSource.EXCHANGE, AccountSource.EXCHANGE),
                accounts.stream().map(ServiceAccount::getAccountSource).toList());
        assertEquals(List.of("专业版", "专业版"),
                accounts.stream().map(ServiceAccount::getDisplayName).toList());

        ArgumentCaptor<List<ExchangeDetailMapper.CompletedAccountRow>> detailCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(detailMapper).completeBatchDetails(eq(9L), detailCaptor.capture(), eq(NOW));
        assertEquals("10001", detailCaptor.getValue().get(0).getAccountId());
        assertEquals("10002", detailCaptor.getValue().get(1).getAccountId());
        assertEquals("AB12000002", detailCaptor.getValue().get(0).getAccount());
        assertEquals("AB12000001", detailCaptor.getValue().get(1).getAccount());
        verify(serviceCodeMapper).consumeForExchange(List.of(101L, 102L),
                "external-exchange-request", NOW);
        verify(batchMapper).complete(9L, NOW);
        verify(operationMapper).markSucceeded(41L, 3L, NOW);
    }

    @Test
    void responseWithWrongAccountCountIsRejectedBeforeLocalMutation() {
        CorsBatchResult response = CorsBatchResult.success(CORS_REQUEST_ID,
                new CorsAddAccountData(List.of(
                        new CorsCreatedAccount(10001L, "AB12000001"))));

        assertThrows(RuntimeException.class, () -> service.finalizeSuccess(41L, 3L, response));

        verify(detailMapper, never()).selectByBatchId(anyLong());
        verify(accountMapper, never()).insertBatch(anyList());
        verify(serviceCodeMapper, never()).consumeForExchange(anyList(), anyString(), any());
        verify(batchMapper, never()).complete(anyLong(), any());
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), any());
    }

    @Test
    void responseWithBlankAccountNameIsRejectedBeforeLocalMutation() {
        CorsBatchResult response = CorsBatchResult.success(CORS_REQUEST_ID,
                new CorsAddAccountData(List.of(
                        new CorsCreatedAccount(10001L, "AB12000001"),
                        new CorsCreatedAccount(10002L, " "))));

        assertThrows(RuntimeException.class, () -> service.finalizeSuccess(41L, 3L, response));

        verify(detailMapper, never()).selectByBatchId(anyLong());
        verify(accountMapper, never()).insertBatch(anyList());
        verify(serviceCodeMapper, never()).consumeForExchange(anyList(), anyString(), any());
    }

    private CorsBatchResult success(String... names) {
        List<CorsCreatedAccount> accounts = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            accounts.add(new CorsCreatedAccount(10001L + i, names[i]));
        }
        return CorsBatchResult.success(CORS_REQUEST_ID, new CorsAddAccountData(accounts));
    }

    private ExchangeDetail detail(Long serviceCodeId, Long detailId, int index,
                                  ExchangeCodeSnapshot snapshot) throws Exception {
        ExchangeDetail detail = new ExchangeDetail();
        detail.setId(detailId);
        detail.setExchangeBatchId(9L);
        detail.setDetailIndex(index);
        detail.setServiceCodeId(serviceCodeId);
        detail.setServiceCodeSnapshot(objectMapper.writeValueAsString(snapshot));
        detail.setStatus(ExchangeStatus.PROCESSING);
        return detail;
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId(CORS_REQUEST_ID);
        operation.setBizType("EXCHANGE_BATCH");
        operation.setBizId(9L);
        operation.setStatus("CLAIMED");
        operation.setVersion(3L);
        return operation;
    }

    private static ExchangeBatch batch() {
        ExchangeBatch batch = new ExchangeBatch();
        batch.setId(9L);
        batch.setRequestId("external-exchange-request");
        batch.setOwnerCompanyId(7L);
        batch.setAssignedUserId(55L);
        batch.setQuantity(2);
        batch.setSpecCode("PRO");
        batch.setDisplayName("专业版");
        batch.setServiceType("STANDARD");
        batch.setDurationDays(30);
        batch.setAccountSilenceDays(12);
        batch.setStatus(ExchangeStatus.PROCESSING);
        return batch;
    }
}

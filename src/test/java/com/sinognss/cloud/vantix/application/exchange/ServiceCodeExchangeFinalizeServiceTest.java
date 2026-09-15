package com.sinognss.cloud.vantix.application.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsCreatedAccount;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServiceCodeExchangeFinalizeServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 8, 0);
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
                new ExchangeCodeSnapshot(101L, "CODE-101", 7L, 55L, "PRO", 1, "MONTH", 12,
                        LocalDateTime.of(2027, 1, 1, 0, 0)));
        secondDetail = detail(102L, 1002L, 2,
                new ExchangeCodeSnapshot(102L, "CODE-102", 7L, 55L, "PRO", 1, "MONTH", 12,
                        LocalDateTime.of(2027, 1, 1, 0, 0)));
        when(operationMapper.selectById(41L)).thenReturn(operation);
        when(batchMapper.selectByRequestIdForUpdate("request-1")).thenReturn(batch);
        when(detailMapper.selectByBatchId(9L)).thenReturn(List.of(firstDetail, secondDetail));
        when(accountMapper.insertBatch(anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());
        when(detailMapper.completeBatchDetails(eq(9L), anyList(), eq(NOW))).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(1)).size());
        when(serviceCodeMapper.consumeForExchange(anyList(), eq("request-1"), eq(NOW))).thenReturn(2);
        when(batchMapper.complete(9L, NOW)).thenReturn(1);
        when(operationMapper.markSucceeded(41L, 3L, NOW)).thenReturn(1);
    }

    @Test
    void responseIndexMapsAccountsToMatchingDetailsEvenWhenCorsReturnsReverseOrder() {
        CorsBatchResult response = success(
                corsAccount(2, "cors-102", "account-102", "ACTIVE", "ACTIVE",
                        "2026-04-02T00:00:00Z", "2026-04-01T01:00:00Z"),
                corsAccount(1, "cors-101", "account-101", "ACTIVE", "WAITING_ACTIVATION",
                        null, "2026-04-01T00:00:00Z"));

        assertTrue(service.finalizeSuccess(41L, 3L, response));

        ArgumentCaptor<List<ServiceAccount>> accountCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(accountMapper).insertBatch(accountCaptor.capture());
        List<ServiceAccount> accounts = accountCaptor.getValue();
        assertEquals(2, accounts.size());
        assertEquals("cors-101", accounts.get(0).getCorsAccountId());
        assertEquals("account-101", accounts.get(0).getAccount());
        assertEquals(101L, accounts.get(0).getSourceServiceCodeId());
        assertEquals(1001L, accounts.get(0).getExchangeDetailId());
        assertEquals(55L, accounts.get(0).getAssignedUserId());
        assertEquals("cors-102", accounts.get(1).getCorsAccountId());
        assertEquals(102L, accounts.get(1).getSourceServiceCodeId());
        assertEquals(LocalDateTime.of(2026, 4, 2, 8, 0), accounts.get(1).getActivatedAt());
        assertEquals(LocalDateTime.of(2026, 4, 1, 9, 0), accounts.get(1).getExpireAt());

        ArgumentCaptor<List<ExchangeDetailMapper.CompletedAccountRow>> detailCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(detailMapper).completeBatchDetails(eq(9L), detailCaptor.capture(), eq(NOW));
        assertEquals("cors-101", detailCaptor.getValue().get(0).getAccountId());
        assertEquals("cors-102", detailCaptor.getValue().get(1).getAccountId());
        verify(serviceCodeMapper).consumeForExchange(List.of(101L, 102L), "request-1", NOW);
        verify(batchMapper).complete(9L, NOW);
        verify(operationMapper).markSucceeded(41L, 3L, NOW);
    }

    @Test
    void invalidCorsIndexIsRejectedBeforeAnyLocalAccountOrCodeMutation() throws Exception {
        CorsCreatedAccount duplicateFirst = corsAccount(1, "cors-1", "account-1",
                "ACTIVE", "ACTIVE", null, "2026-04-01T00:00:00Z");
        CorsCreatedAccount duplicateIndex = corsAccount(1, "cors-2", "account-2",
                "ACTIVE", "ACTIVE", null, "2026-04-01T00:00:00Z");
        CorsBatchResult response = success(duplicateFirst, duplicateIndex);

        assertThrows(RuntimeException.class, () -> service.finalizeSuccess(41L, 3L, response));

        verify(detailMapper, never()).selectByBatchId(anyLong());
        verify(accountMapper, never()).insertBatch(anyList());
        verify(serviceCodeMapper, never()).consumeForExchange(anyList(), anyString(), any());
        verify(batchMapper, never()).complete(anyLong(), any());
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), any());
    }

    private CorsBatchResult success(CorsCreatedAccount... accounts) {
        return new CorsBatchResult(CorsOutcome.SUCCESS, "request-1", List.of(accounts), null, null);
    }

    private static CorsCreatedAccount corsAccount(int index, String id, String account,
                                                   String status, String activationStatus,
                                                   String activatedAt, String expireAt) {
        return new CorsCreatedAccount(index, id, account, status, activationStatus,
                activatedAt == null ? null : OffsetDateTime.parse(activatedAt),
                expireAt == null ? null : OffsetDateTime.parse(expireAt),
                OffsetDateTime.parse("2026-03-30T12:00:00Z"),
                OffsetDateTime.parse("2026-03-31T12:00:00Z"));
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
        operation.setRequestId("request-1");
        operation.setBizType("EXCHANGE_BATCH");
        operation.setBizId(9L);
        operation.setStatus("CLAIMED");
        operation.setVersion(3L);
        return operation;
    }

    private static ExchangeBatch batch() {
        ExchangeBatch batch = new ExchangeBatch();
        batch.setId(9L);
        batch.setRequestId("request-1");
        batch.setOwnerCompanyId(7L);
        batch.setAssignedUserId(55L);
        batch.setQuantity(2);
        batch.setSpecCode("PRO");
        batch.setServiceType("STANDARD");
        batch.setDurationDays(30);
        batch.setAccountSilenceDays(12);
        batch.setStatus(ExchangeStatus.PROCESSING);
        return batch;
    }
}

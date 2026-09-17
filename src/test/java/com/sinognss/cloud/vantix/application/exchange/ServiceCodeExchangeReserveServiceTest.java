package com.sinognss.cloud.vantix.application.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.CompanyExchangeConfig;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CompanyExchangeConfigMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeExchangeReserveServiceTest {
    private final ExchangeBatchMapper batchMapper = mock(ExchangeBatchMapper.class);
    private final ExchangeDetailMapper detailMapper = mock(ExchangeDetailMapper.class);
    private final CompanyExchangeConfigMapper exchangeConfigMapper = mock(CompanyExchangeConfigMapper.class);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final ServiceCodeMapper serviceCodeMapper = mock(ServiceCodeMapper.class);
    private final ServiceDurationConfigMapper durationMapper = mock(ServiceDurationConfigMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final GenerationProperties generationProperties = new GenerationProperties();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private ServiceCodeExchangeReserveService service;

    @BeforeEach
    void setUp() {
        service = new ServiceCodeExchangeReserveService(batchMapper, detailMapper, exchangeConfigMapper, operationMapper,
                serviceCodeMapper, durationMapper, companyMapper, userHolder,
                generationProperties, objectMapper, clock);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(7L, "tester"));
        when(companyMapper.selectCount(any())).thenReturn(1L);
        when(exchangeConfigMapper.selectByCompanyId(anyLong())).thenReturn(exchangeConfig());
        when(durationMapper.selectBySpecCodes(anyList())).thenReturn(List.of(spec()));
        when(batchMapper.insert(any(ExchangeBatch.class))).thenAnswer(invocation -> {
            ((ExchangeBatch) invocation.getArgument(0)).setId(400L);
            return 1;
        });
        when(detailMapper.insertBatch(anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());
        when(serviceCodeMapper.reserveForExchange(anyList(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(operationMapper.insert(any(CorsOperation.class))).thenAnswer(invocation -> {
            ((CorsOperation) invocation.getArgument(0)).setId(800L);
            return 1;
        });
    }

    @Test
    void payloadHashIsStableAndExcludesTheServerSidePrefix() {
        ServiceCodeExchangeCommand command = command("one", 3, "acct");
        String hash = ExchangePayloadHash.calculate(command, null);
        assertNotEquals(hash, ExchangePayloadHash.calculate(command, 88L));

        assertEquals(hash, ExchangePayloadHash.calculate(command("different-request", 3, "acct"), null));
        assertNotEquals(hash, ExchangePayloadHash.calculate(
                new ServiceCodeExchangeCommand("one", 2L, "SPEC-1", GenerationSource.B2B, 3), null));
        assertNotEquals(hash, ExchangePayloadHash.calculate(
                new ServiceCodeExchangeCommand("one", 1L, "SPEC-2", GenerationSource.B2B, 3), null));
        assertNotEquals(hash, ExchangePayloadHash.calculate(
                new ServiceCodeExchangeCommand("one", 1L, "SPEC-1", GenerationSource.OFFLINE, 3), null));
        assertNotEquals(hash, ExchangePayloadHash.calculate(
                new ServiceCodeExchangeCommand("one", 1L, "SPEC-1", GenerationSource.B2B, 4), null));
        assertEquals(hash, ExchangePayloadHash.calculate(command("one", 3, "other"), null));
    }

    @Test
    void requestIdWithSamePayloadReturnsExistingReservationWithoutSelectingCodes() {
        ServiceCodeExchangeCommand command = command("stable-request", 2, null);
        ExchangeBatch existing = new ExchangeBatch();
        existing.setId(41L);
        existing.setDisplayName("已冻结规格");
        existing.setPayloadHash(ExchangePayloadHash.calculate(command, null));
        CorsOperation operation = new CorsOperation();
        operation.setId(52L);
        when(batchMapper.selectByRequestId("stable-request")).thenReturn(existing);
        when(operationMapper.selectByBusiness("EXCHANGE_BATCH", 41L)).thenReturn(operation);

        ExchangeReservation result = service.reserve(command);

        assertEquals(new ExchangeReservation(41L, 52L, false), result);
        verify(serviceCodeMapper, never()).selectAvailableForExchange(anyLong(), anyString(),
                anyString(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(batchMapper, never()).insert(any(ExchangeBatch.class));
        verify(durationMapper, never()).selectBySpecCodes(anyList());
        verify(operationMapper, never()).insert(any(CorsOperation.class));
    }

    @Test
    void requestIdWithDifferentPayloadIsRejectedBeforeSelectingCodes() {
        ServiceCodeExchangeCommand original = command("reused-request", 2, "a");
        ExchangeBatch existing = new ExchangeBatch();
        existing.setId(41L);
        existing.setPayloadHash(ExchangePayloadHash.calculate(original, null));
        when(batchMapper.selectByRequestId("reused-request")).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reserve(command("reused-request", 3, "a")));

        assertEquals(ErrorCode.EXCHANGE_IDEMPOTENCY_CONFLICT, exception.getVantixErrorCode());
        verify(serviceCodeMapper, never()).selectAvailableForExchange(anyLong(), anyString(),
                anyString(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void quantityAboveConfigured5000LimitIsRejectedBeforeDatabaseAccess() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reserve(command("too-many", 5001, null)));

        assertEquals(ErrorCode.INVALID_ARGUMENT, exception.getVantixErrorCode());
        verify(userHolder, never()).getUserScope();
        verify(batchMapper, never()).selectByRequestId(anyString());
        verify(serviceCodeMapper, never()).selectAvailableForExchange(anyLong(), anyString(),
                anyString(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void quantity5000IsAcceptedAndReservedAsOneCompleteOrderedBatch() {
        List<ServiceCode> earliestFirst = new ArrayList<>(5000);
        for (long id = 1; id <= 5000; id++) {
            earliestFirst.add(code(id, LocalDateTime.of(2026, 2, 1, 0, 0).plusMinutes(id)));
        }
        when(serviceCodeMapper.selectAvailableForExchange(eq(1L), eq("SPEC-1"), eq("B2B"),
                any(), eq(5000))).thenReturn(earliestFirst);

        ExchangeReservation result = service.reserve(command("max-quantity", 5000, "acct"));

        assertTrue(result.created());
        assertEquals(400L, result.batchId());
        assertEquals(800L, result.operationId());
        ArgumentCaptor<List<ExchangeDetail>> detailCaptor = ArgumentCaptor.forClass(List.class);
        verify(detailMapper, times(10)).insertBatch(detailCaptor.capture());
        List<ExchangeDetail> details = detailCaptor.getAllValues().stream()
                .flatMap(List::stream).toList();
        assertEquals(5000, details.size());
        assertEquals(1, details.get(0).getDetailIndex());
        assertEquals(5000, details.get(4999).getDetailIndex());
        assertEquals(1L, details.get(0).getServiceCodeId());
        assertEquals(5000L, details.get(4999).getServiceCodeId());
        ArgumentCaptor<List<Long>> codeIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(serviceCodeMapper).reserveForExchange(codeIdsCaptor.capture(), eq(1L), eq("max-quantity"), any());
        assertEquals(5000, codeIdsCaptor.getValue().size());
        assertEquals(1L, codeIdsCaptor.getValue().get(0));
        assertEquals(5000L, codeIdsCaptor.getValue().get(4999));
    }

    @Test
    void createsAndPersistsASeparateCorsRequestIdForTheBatchSideEffect() {
        when(serviceCodeMapper.selectAvailableForExchange(eq(1L), eq("SPEC-1"), eq("B2B"),
                any(), eq(1))).thenReturn(List.of(code(101L, LocalDateTime.of(2026, 2, 1, 0, 0))));

        assertTrue(service.reserve(command("external-exchange-request", 1, null)).created());

        ArgumentCaptor<CorsOperation> operationCaptor = ArgumentCaptor.forClass(CorsOperation.class);
        verify(operationMapper).insert(operationCaptor.capture());
        CorsOperation operation = operationCaptor.getValue();
        assertTrue(operation.getRequestId().startsWith("EXCHANGE_"));
        assertNotEquals("external-exchange-request", operation.getRequestId());
        assertEquals(400L, operation.getBizId());
        assertEquals(null, operation.getFirstAttemptAt());
    }

    @Test
    void selectedCodesAreReservedInEarliestExpiryThenIdOrderAndDetailsUseOneBasedIndexes() {
        // The mapper's ordered result represents expiry ASC, then id ASC, including ties.
        List<ServiceCode> ordered = List.of(
                code(2L, LocalDateTime.of(2026, 2, 1, 0, 0)),
                code(10L, LocalDateTime.of(2026, 2, 1, 0, 0)),
                code(5L, LocalDateTime.of(2026, 2, 2, 0, 0)));
        when(serviceCodeMapper.selectAvailableForExchange(eq(1L), eq("SPEC-1"), eq("B2B"),
                any(), eq(3))).thenReturn(ordered);

        ExchangeReservation reservation = service.reserve(command("ordered", 3, null));

        assertTrue(reservation.created());
        ArgumentCaptor<List<ExchangeDetail>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertBatch(detailsCaptor.capture());
        List<ExchangeDetail> details = detailsCaptor.getValue();
        assertEquals(List.of(2L, 10L, 5L), details.stream().map(ExchangeDetail::getServiceCodeId).toList());
        assertEquals(List.of(2L, 10L, 5L), details.stream().map(ExchangeDetail::getActiveServiceCodeId).toList());
        assertEquals(List.of(1, 2, 3), details.stream().map(ExchangeDetail::getDetailIndex).toList());
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(serviceCodeMapper).reserveForExchange(idsCaptor.capture(), eq(1L), eq("ordered"), any());
        assertEquals(List.of(2L, 10L, 5L), idsCaptor.getValue());
        verify(serviceCodeMapper).selectAvailableForExchange(eq(1L), eq("SPEC-1"), eq("B2B"),
                any(), eq(3));
    }

    @Test
    void personalReservationFreezesOwnershipAndRejectsSameCompanyDifferentUserRetry() throws Exception {
        ServiceCodeExchangeCommand command = command("personal-request", 1, null);
        when(userHolder.getUserScope()).thenReturn(new UserScope(88L, 1L));
        when(serviceCodeMapper.selectAvailableForExchange(eq(1L), eq("SPEC-1"), eq("B2B"), any(), eq(1)))
                .thenReturn(List.of(code(101L, LocalDateTime.of(2026, 2, 1, 0, 0))));

        assertTrue(service.reserve(command).created());

        ArgumentCaptor<ExchangeBatch> batchCaptor = ArgumentCaptor.forClass(ExchangeBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        ExchangeBatch batch = batchCaptor.getValue();
        assertEquals(88L, batch.getAssignedUserId());
        assertEquals("标准规格", batch.getDisplayName());
        assertEquals(ExchangePayloadHash.calculate(command, 88L), batch.getPayloadHash());

        ArgumentCaptor<List<ExchangeDetail>> detailCaptor = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertBatch(detailCaptor.capture());
        ExchangeCodeSnapshot snapshot = objectMapper.readValue(
                detailCaptor.getValue().get(0).getServiceCodeSnapshot(), ExchangeCodeSnapshot.class);
        assertEquals(batch.getAssignedUserId(), snapshot.assignedUserId());

        when(batchMapper.selectByRequestId("personal-request")).thenReturn(batch);
        when(userHolder.getUserScope()).thenReturn(new UserScope(89L, 1L));
        BusinessException reserveException = assertThrows(BusinessException.class,
                () -> service.reserve(command));
        assertEquals(ErrorCode.EXCHANGE_IDEMPOTENCY_CONFLICT, reserveException.getVantixErrorCode());
        BusinessException lookupException = assertThrows(BusinessException.class,
                () -> service.findExisting(command));
        assertEquals(ErrorCode.EXCHANGE_IDEMPOTENCY_CONFLICT, lookupException.getVantixErrorCode());
        verify(serviceCodeMapper, times(1)).selectAvailableForExchange(eq(1L), eq("SPEC-1"),
                eq("B2B"), any(), eq(1));
    }

    @Test
    void companyAndGlobalReservationsKeepBatchAssignedUserNull() {
        when(serviceCodeMapper.selectAvailableForExchange(eq(1L), eq("SPEC-1"), eq("B2B"), any(), eq(1)))
                .thenReturn(List.of(code(101L, LocalDateTime.of(2026, 2, 1, 0, 0))));
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 1L));
        assertTrue(service.reserve(command("company-request", 1, null)).created());

        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        assertTrue(service.reserve(command("global-request", 1, null)).created());

        ArgumentCaptor<ExchangeBatch> batchCaptor = ArgumentCaptor.forClass(ExchangeBatch.class);
        verify(batchMapper, times(2)).insert(batchCaptor.capture());
        assertTrue(batchCaptor.getAllValues().stream()
                .allMatch(batch -> batch.getAssignedUserId() == null));
    }
    private ServiceCodeExchangeCommand command(String requestId, int quantity, String prefix) {
        return new ServiceCodeExchangeCommand(requestId, 1L, "SPEC-1",
                GenerationSource.B2B, quantity);
    }

    private CompanyExchangeConfig exchangeConfig() {
        CompanyExchangeConfig config = new CompanyExchangeConfig();
        config.setCompanyId(1L);
        config.setAccountPrefix("AB12");
        return config;
    }

    private ServiceDurationConfig spec() {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setSpecCode("SPEC-1");
        config.setDisplayName("标准规格");
        config.setServiceType("STANDARD");
        config.setDurationDays(30);
        config.setCodeSilenceDays(0);
        config.setAccountSilenceDays(30);
        config.setEnabled(false);
        return config;
    }

    private ServiceCode code(Long id, LocalDateTime expireAt) {
        ServiceCode code = new ServiceCode();
        code.setId(id);
        code.setCode("CODE-" + id);
        code.setGenerateBatchId(25L);
        code.setOwnerCompanyId(1L);
        code.setSpecCode("SPEC-1");
        code.setServiceType("STANDARD");
        code.setDurationDays(30);
        code.setCodeSilenceDays(0);
        code.setExpireAt(expireAt);
        return code;
    }
}

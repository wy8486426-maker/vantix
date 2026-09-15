package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateOrder;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateOrderMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeGenerateServiceTest {
    private final ServiceCodeGenerateOrderMapper orderMapper = mock(ServiceCodeGenerateOrderMapper.class);
    private final ServiceCodeGenerateBatchMapper batchMapper = mock(ServiceCodeGenerateBatchMapper.class);
    private final ServiceDurationConfigMapper configMapper = mock(ServiceDurationConfigMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final ServiceCodeBatchGenerateService batchService = mock(ServiceCodeBatchGenerateService.class);
    private final GenerationProperties properties = new GenerationProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private ServiceCodeOrderGenerateService service;

    @BeforeEach
    void setUp() {
        when(orderMapper.insert(any(ServiceCodeGenerateOrder.class))).thenAnswer(invocation -> {
            ((ServiceCodeGenerateOrder) invocation.getArgument(0)).setId(900L);
            return 1;
        });
        when(orderMapper.complete(anyLong(), any())).thenReturn(1);
        when(companyMapper.selectCount(any())).thenReturn(1L);
        when(configMapper.selectBySpecCodes(any())).thenReturn(List.of(spec("M1"), spec("W1")));
        when(batchMapper.selectByGenerateOrderId(900L)).thenReturn(List.of(batch("M1", 6), batch("W1", 5)));
        service = new ServiceCodeOrderGenerateService(orderMapper, batchMapper, configMapper,
                companyMapper, batchService, properties, clock);
    }

    @Test
    void generatesAllSortedItemsAsOneOrderAndUsesOneSpecLookup() {
        var result = service.generate(command("REQ-1", List.of(item("W1", 5), item("M1", 6))),
                new OperatorIdentity(7L, "operator"));

        assertFalse(result.idempotent());
        assertEquals(2, result.itemCount());
        assertEquals(11, result.totalQuantity());
        assertEquals(List.of("M1", "W1"), result.items().stream().map(ServiceCodeGenerateOrderItemView::specCode).toList());
        verify(configMapper, times(1)).selectBySpecCodes(List.of("M1", "W1"));
        ArgumentCaptor<Set> generatedCodes = ArgumentCaptor.forClass(Set.class);
        verify(batchService, times(2)).generateBatch(anyLong(), any(), any(), any(), any(), generatedCodes.capture());
        assertSame(generatedCodes.getAllValues().get(0), generatedCodes.getAllValues().get(1));
        verify(orderMapper).complete(900L, LocalDateTime.now(clock));
    }

    @Test
    void payloadHashIgnoresItemOrderingButChangesWhenQuantityChanges() {
        String first = ServiceCodeOrderGenerateService.payloadHash(
                command("REQ-A", List.of(item("W1", 5), item("M1", 6))));
        String reordered = ServiceCodeOrderGenerateService.payloadHash(
                command("REQ-A", List.of(item("M1", 6), item("W1", 5))));
        String changed = ServiceCodeOrderGenerateService.payloadHash(
                command("REQ-A", List.of(item("M1", 7), item("W1", 5))));

        assertEquals(first, reordered);
        assertTrue(!first.equals(changed));
    }

    @Test
    void sameRequestAndSameOrderWithAnotherRequestAreIdempotent() {
        var command = command("REQ-1", List.of(item("M1", 6), item("W1", 5)));
        ServiceCodeGenerateOrder existing = existing(command);
        when(orderMapper.selectByRequestId("REQ-1")).thenReturn(existing);

        var replay = service.generate(command, new OperatorIdentity(7L, "operator"));
        assertTrue(replay.idempotent());

        when(orderMapper.selectByRequestId("REQ-2")).thenReturn(null);
        when(orderMapper.selectByBusinessKey("B2B", 100L, "ORDER-1")).thenReturn(existing);
        var otherRequest = service.generate(command("REQ-2", List.of(item("W1", 5), item("M1", 6))),
                new OperatorIdentity(7L, "operator"));
        assertTrue(otherRequest.idempotent());
        verify(orderMapper, never()).insert(any(ServiceCodeGenerateOrder.class));
    }

    @Test
    void sameRequestWithChangedPayloadConflictsBeforeAnyWrite() {
        var original = command("REQ-1", List.of(item("M1", 6), item("W1", 5)));
        when(orderMapper.selectByRequestId("REQ-1")).thenReturn(existing(original));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.generate(
                command("REQ-1", List.of(item("M1", 7), item("W1", 5))),
                new OperatorIdentity(7L, "operator")));

        assertEquals(ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT, exception.getVantixErrorCode());
        verify(orderMapper, never()).insert(any(ServiceCodeGenerateOrder.class));
        verify(batchService, never()).generateBatch(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateOrUnavailableSpecsBeforeCreatingTheOrder() {
        assertThrows(BusinessException.class, () -> service.generate(
                command("REQ-DUP", List.of(item("M1", 1), item("M1", 2))),
                new OperatorIdentity(7L, "operator")));
        verify(orderMapper, never()).insert(any(ServiceCodeGenerateOrder.class));

        when(configMapper.selectBySpecCodes(any())).thenReturn(List.of(spec("M1")));
        assertThrows(BusinessException.class, () -> service.generate(
                command("REQ-INVALID", List.of(item("M1", 1), item("W1", 1))),
                new OperatorIdentity(7L, "operator")));
        verify(batchService, never()).generateBatch(anyLong(), any(), any(), any(), any(), any());
    }

    private GenerateServiceCodeOrderCommand command(String requestId,
                                                     List<GenerateServiceCodeItemCommand> items) {
        return new GenerateServiceCodeOrderCommand(GenerationSource.B2B, requestId, "ORDER-1",
                null, 100L, items);
    }

    private GenerateServiceCodeItemCommand item(String specCode, int quantity) {
        return new GenerateServiceCodeItemCommand(specCode, quantity, null);
    }

    private ServiceCodeGenerateOrder existing(GenerateServiceCodeOrderCommand command) {
        ServiceCodeGenerateOrder order = new ServiceCodeGenerateOrder();
        order.setId(900L);
        order.setRequestId(command.requestId());
        order.setGenerationSource(GenerationSource.B2B);
        order.setSourceOrderNo("ORDER-1");
        order.setOwnerCompanyId(100L);
        order.setPayloadHash(ServiceCodeOrderGenerateService.payloadHash(command));
        order.setItemCount(2);
        order.setTotalQuantity(11);
        order.setStatus("COMPLETED");
        return order;
    }

    private ServiceDurationConfig spec(String specCode) {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setSpecCode(specCode);
        config.setServiceType("CORS");
        config.setDisplayName("30天");
        config.setDurationDays(30);
        config.setCodeSilenceDays(180);
        config.setAccountSilenceDays(360);
        config.setEnabled(true);
        return config;
    }

    private ServiceCodeGenerateBatch batch(String specCode, int quantity) {
        ServiceCodeGenerateBatch batch = new ServiceCodeGenerateBatch();
        batch.setBatchNo("GB-" + specCode);
        batch.setRequestId("BATCH-" + specCode);
        batch.setGenerationSource(GenerationSource.B2B);
        batch.setSourceOrderNo("ORDER-1");
        batch.setOwnerCompanyId(100L);
        batch.setSpecCode(specCode);
        batch.setDisplayName("30天");
        batch.setDurationDays(30);
        batch.setCodeSilenceDays(180);
        batch.setQuantity(quantity);
        batch.setGeneratedCount(quantity);
        batch.setStatus("COMPLETED");
        return batch;
    }
}

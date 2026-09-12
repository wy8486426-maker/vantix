package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.ServiceCodeGenerator;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeGenerateServiceTest {
    private final ServiceCodeGenerateBatchMapper batchMapper = mock(ServiceCodeGenerateBatchMapper.class);
    private final ServiceCodeMapper codeMapper = mock(ServiceCodeMapper.class);
    private final ServiceDurationConfigMapper configMapper = mock(ServiceDurationConfigMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final ServiceCodeGenerator codeGenerator = mock(ServiceCodeGenerator.class);
    private final GenerationProperties properties = new GenerationProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final List<ServiceCode> insertedCodes = new ArrayList<>();
    private ServiceCodeGenerateService service;

    @BeforeEach
    void setUp() {
        insertedCodes.clear();
        AtomicInteger codeSequence = new AtomicInteger();
        when(codeGenerator.generate(any(LocalDate.class)))
                .thenAnswer(invocation -> "VX260101" + String.format("%020d", codeSequence.incrementAndGet()));
        when(codeGenerator.generateBatchNo(any(LocalDate.class))).thenReturn("GB260101ABCDEFGHIJKLMNOPQRST");
        when(batchMapper.insert(any(ServiceCodeGenerateBatch.class))).thenAnswer(invocation -> {
            ((ServiceCodeGenerateBatch) invocation.getArgument(0)).setId(900L);
            return 1;
        });
        when(codeMapper.insert(any(ServiceCode.class))).thenAnswer(invocation -> {
            ServiceCode code = invocation.getArgument(0);
            code.setId((long) insertedCodes.size() + 1);
            insertedCodes.add(code);
            return 1;
        });
        when(batchMapper.complete(any(), any(Integer.class), any())).thenReturn(1);
        when(companyMapper.selectCount(any())).thenReturn(1L);
        when(configMapper.selectEnabledBySpecCode("M1")).thenReturn(spec(true));
        properties.setMaxQuantityPerRequest(100);
        service = new ServiceCodeGenerateService(batchMapper, codeMapper, configMapper,
                companyMapper, codeGenerator, properties, clock);
    }

    @Test
    void createsBatchAndSnapshotsEnabledSpecForEveryPendingCode() {
        GenerateServiceCodeResult result = service.generate(command("REQ-1", "ORDER-1", 3), IntegrationActor.B2B.operatorIdentity());

        assertEquals(3, result.batch().generatedCount());
        assertEquals(3, result.serviceCodes().size());
        assertFalse(result.idempotent());
        assertEquals(3, result.serviceCodes().stream().distinct().count());
        assertTrue(result.serviceCodes().stream().allMatch(code -> code.matches("^VX260101[A-Z0-9]{20}$")));
        assertEquals(100L, insertedCodes.get(0).getOwnerCompanyId());
        assertEquals(900L, insertedCodes.get(0).getGenerateBatchId());
        assertEquals(ServiceCodeStatus.PENDING, insertedCodes.get(0).getStatus());
        assertEquals(12, insertedCodes.get(0).getCodeSilenceMonths());
        assertEquals(insertedCodes.get(0).getCreatedAt().plusMonths(12), insertedCodes.get(0).getExpireAt());
        assertEquals("CORS", insertedCodes.get(0).getServiceType());
        ArgumentCaptor<ServiceCodeGenerateBatch> batch = ArgumentCaptor.forClass(ServiceCodeGenerateBatch.class);
        verify(batchMapper).insert(batch.capture());
        assertEquals("M1", batch.getValue().getSpecCode());
        verify(batchMapper).complete(org.mockito.ArgumentMatchers.eq(900L), org.mockito.ArgumentMatchers.eq(3), any());
    }

    @Test
    void returnsExistingBatchForRepeatedRequestIdWithoutGeneratingAgain() {
        ServiceCodeGenerateBatch existing = batch("REQ-1", "ORDER-1", 3);
        when(batchMapper.selectByRequestIdForUpdate("REQ-1")).thenReturn(existing);
        when(codeMapper.selectByGenerateBatchId(77L)).thenReturn(List.of(code("A"), code("B"), code("C")));

        GenerateServiceCodeResult result = service.generate(command("REQ-1", "ORDER-1", 3), IntegrationActor.B2B.operatorIdentity());

        assertTrue(result.idempotent());
        assertEquals(existing.getBatchNo(), result.batch().batchNo());
        assertEquals(3, result.serviceCodes().size());
        verify(batchMapper, never()).insert(any(ServiceCodeGenerateBatch.class));
        verify(codeMapper, never()).insert(any(ServiceCode.class));
    }

    @Test
    void rejectsSameRequestIdWithChangedQuantity() {
        when(batchMapper.selectByRequestIdForUpdate("REQ-1")).thenReturn(batch("REQ-1", "ORDER-1", 3));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.generate(command("REQ-1", "ORDER-1", 4), IntegrationActor.B2B.operatorIdentity()));

        assertEquals(ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT, exception.getVantixErrorCode());
    }

    @Test
    void returnsPriorBatchWhenDifferentRequestRepeatsSameBusinessKey() {
        ServiceCodeGenerateBatch existing = batch("REQ-OLD", "ORDER-1", 3);
        when(batchMapper.selectByBusinessKeyForUpdate(anyString(), any(), anyString())).thenReturn(existing);
        when(codeMapper.selectByGenerateBatchId(77L)).thenReturn(List.of(code("A"), code("B"), code("C")));

        GenerateServiceCodeResult result = service.generate(command("REQ-NEW", "ORDER-1", 3), IntegrationActor.B2B.operatorIdentity());

        assertTrue(result.idempotent());
        assertEquals("REQ-OLD", existing.getRequestId());
        verify(batchMapper, never()).insert(any(ServiceCodeGenerateBatch.class));
    }

    @Test
    void rejectsDisabledSpecAndUnknownCompanyBeforeWriting() {
        when(configMapper.selectEnabledBySpecCode("M1")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.generate(command("REQ-1", "ORDER-1", 1), IntegrationActor.B2B.operatorIdentity()));
        verify(batchMapper, never()).insert(any(ServiceCodeGenerateBatch.class));

        when(companyMapper.selectCount(any())).thenReturn(0L);
        when(configMapper.selectEnabledBySpecCode("M1")).thenReturn(spec(true));
        assertThrows(BusinessException.class,
                () -> service.generate(command("REQ-2", "ORDER-2", 1), IntegrationActor.B2B.operatorIdentity()));
        verify(configMapper, times(1)).selectEnabledBySpecCode("M1");
    }

    @Test
    void rejectsInvalidQuantityBeforeDatabaseWrites() {
        assertThrows(BusinessException.class,
                () -> service.generate(command("REQ-1", "ORDER-1", 0), IntegrationActor.B2B.operatorIdentity()));
        verify(batchMapper, never()).insert(any(ServiceCodeGenerateBatch.class));
    }

    private GenerateServiceCodeCommand command(String requestId, String orderNo, int quantity) {
        return new GenerateServiceCodeCommand(GenerationSource.B2B, requestId, orderNo, null,
                100L, "M1", quantity, null);
    }

    private ServiceDurationConfig spec(boolean enabled) {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setSpecCode("M1");
        config.setServiceType("CORS");
        config.setDurationValue(1);
        config.setDurationUnit(DurationUnit.MONTH);
        config.setCodeSilenceMonths(12);
        config.setEnabled(enabled);
        return config;
    }

    private ServiceCodeGenerateBatch batch(String requestId, String orderNo, int quantity) {
        ServiceCodeGenerateBatch batch = new ServiceCodeGenerateBatch();
        batch.setId(77L);
        batch.setBatchNo("GB-OLD");
        batch.setRequestId(requestId);
        batch.setGenerationSource(GenerationSource.B2B);
        batch.setSourceOrderNo(orderNo);
        batch.setOwnerCompanyId(100L);
        batch.setSpecCode("M1");
        batch.setDurationValue(1);
        batch.setDurationUnit("MONTH");
        batch.setCodeSilenceMonths(12);
        batch.setQuantity(quantity);
        batch.setGeneratedCount(quantity);
        batch.setStatus("COMPLETED");
        batch.setBusinessKeyHash(ServiceCodeGenerateService.businessKeyHash(
                GenerationSource.B2B, 100L, orderNo, "M1"));
        return batch;
    }

    private ServiceCode code(String value) {
        ServiceCode code = new ServiceCode();
        code.setCode(value);
        return code;
    }
}
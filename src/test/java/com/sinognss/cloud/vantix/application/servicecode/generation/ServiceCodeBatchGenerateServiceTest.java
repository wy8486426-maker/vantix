package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.ServiceCodeGenerator;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeBatchGenerateServiceTest {
    private final ServiceCodeGenerateBatchMapper batchMapper = mock(ServiceCodeGenerateBatchMapper.class);
    private final ServiceCodeMapper codeMapper = mock(ServiceCodeMapper.class);
    private final ServiceCodeGenerator codeGenerator = mock(ServiceCodeGenerator.class);
    private final GenerationProperties properties = new GenerationProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC);
    private final AtomicInteger generatedCodeSequence = new AtomicInteger();
    private final AtomicInteger batchIdSequence = new AtomicInteger(100);
    private final List<Integer> chunkSizes = new ArrayList<>();
    private final List<ServiceCode> insertedCodes = new ArrayList<>();
    private ServiceCodeBatchGenerateService service;

    @BeforeEach
    void setUp() {
        when(batchMapper.insert(any(ServiceCodeGenerateBatch.class))).thenAnswer(invocation -> {
            ((ServiceCodeGenerateBatch) invocation.getArgument(0)).setId((long) batchIdSequence.incrementAndGet());
            return 1;
        });
        when(batchMapper.complete(anyLong(), anyInt(), any(LocalDateTime.class))).thenReturn(1);
        when(codeGenerator.generateBatchNo(any(LocalDate.class))).thenReturn("GB-TEST");
        when(codeGenerator.generate(any(LocalDate.class)))
                .thenAnswer(invocation -> "CODE-" + generatedCodeSequence.incrementAndGet());
        when(codeMapper.insertBatch(anyList())).thenAnswer(invocation -> {
            List<ServiceCode> chunk = invocation.getArgument(0);
            chunkSizes.add(chunk.size());
            insertedCodes.addAll(chunk);
            return chunk.size();
        });
        service = new ServiceCodeBatchGenerateService(batchMapper, codeMapper, codeGenerator, properties, clock);
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "500, 1", "501, 2", "1200, 3", "5000, 10"})
    void preGeneratesAndInsertsCodesUsingExpectedChunkCount(int quantity, int expectedChunks) {
        service.generateBatch(900L, order("M1", quantity), item("M1", quantity), spec("M1"),
                new OperatorIdentity(7L, "operator"), new HashSet<>());

        assertEquals(expectedChunks, chunkSizes.size());
        assertEquals(quantity, insertedCodes.size());
        assertEquals(quantity, insertedCodes.stream().map(ServiceCode::getCode).distinct().count());
        for (int index = 0; index < expectedChunks; index++) {
            assertEquals(Math.min(500, quantity - index * 500), chunkSizes.get(index));
        }
        verify(codeMapper, times(expectedChunks)).insertBatch(anyList());
        verify(codeMapper, never()).insert(any(ServiceCode.class));
    }

    @Test
    void configuredBatchSizeControlsChunkBoundaries() {
        properties.setBatchInsertSize(2);

        service.generateBatch(900L, order("M1", 5), item("M1", 5), spec("M1"),
                new OperatorIdentity(7L, "operator"), new HashSet<>());

        assertEquals(List.of(2, 2, 1), chunkSizes);
    }

    @Test
    void generatedCodesAreUniqueAcrossDifferentSpecsInOneOrder() {
        when(codeGenerator.generate(any(LocalDate.class))).thenReturn("SHARED-CODE", "SHARED-CODE", "NEXT-CODE");
        Set<String> generatedCodes = new HashSet<>();

        service.generateBatch(900L, order("M1", 1), item("M1", 1), spec("M1"),
                new OperatorIdentity(7L, "operator"), generatedCodes);
        service.generateBatch(900L, order("W1", 1), item("W1", 1), spec("W1"),
                new OperatorIdentity(7L, "operator"), generatedCodes);

        assertEquals(List.of("SHARED-CODE", "NEXT-CODE"),
                insertedCodes.stream().map(ServiceCode::getCode).toList());
        assertEquals(Set.of("SHARED-CODE", "NEXT-CODE"), generatedCodes);
        verify(codeGenerator, times(3)).generate(any(LocalDate.class));
    }

    @Test
    void retriesOrderLocalRandomCollisionAtMostFiveTimes() {
        when(codeGenerator.generate(any(LocalDate.class))).thenReturn("ALREADY-USED");
        Set<String> generatedCodes = new HashSet<>(Set.of("ALREADY-USED"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.generateBatch(
                900L, order("M1", 1), item("M1", 1), spec("M1"),
                new OperatorIdentity(7L, "operator"), generatedCodes));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getVantixErrorCode());
        verify(codeGenerator, times(5)).generate(any(LocalDate.class));
        verify(codeMapper, never()).insertBatch(anyList());
        verify(batchMapper, never()).complete(anyLong(), anyInt(), any(LocalDateTime.class));
    }

    @Test
    void duplicateKeyFromMultiRowInsertIsConvertedWithoutRetry() {
        doThrow(new DuplicateKeyException("duplicate")).when(codeMapper).insertBatch(anyList());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.generateBatch(
                900L, order("M1", 1), item("M1", 1), spec("M1"),
                new OperatorIdentity(7L, "operator"), new HashSet<>()));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getVantixErrorCode());
        verify(codeMapper, times(1)).insertBatch(anyList());
        verify(codeGenerator, times(1)).generate(any(LocalDate.class));
        verify(batchMapper, never()).complete(anyLong(), anyInt(), any(LocalDateTime.class));
    }

    @Test
    void failureInLastChunkDoesNotCompleteBatch() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            List<ServiceCode> chunk = invocation.getArgument(0);
            if (calls.incrementAndGet() == 2) {
                throw new DuplicateKeyException("forced duplicate");
            }
            insertedCodes.addAll(chunk);
            chunkSizes.add(chunk.size());
            return chunk.size();
        }).when(codeMapper).insertBatch(anyList());

        assertThrows(BusinessException.class, () -> service.generateBatch(
                900L, order("M1", 501), item("M1", 501), spec("M1"),
                new OperatorIdentity(7L, "operator"), new HashSet<>()));

        assertEquals(List.of(500), chunkSizes);
        assertEquals(500, insertedCodes.size());
        verify(codeMapper, times(2)).insertBatch(anyList());
        verify(batchMapper, never()).complete(anyLong(), anyInt(), any(LocalDateTime.class));
    }

    @Test
    void batchInsertSizeMustStayWithinSupportedRange() {
        GenerationProperties bounded = new GenerationProperties();
        bounded.setBatchInsertSize(1);
        bounded.setBatchInsertSize(1000);

        assertThrows(IllegalArgumentException.class, () -> bounded.setBatchInsertSize(0));
        assertThrows(IllegalArgumentException.class, () -> bounded.setBatchInsertSize(1001));
    }

    private GenerateServiceCodeOrderCommand order(String specCode, int quantity) {
        return new GenerateServiceCodeOrderCommand(GenerationSource.B2B, "REQ-" + specCode,
                "ORDER-1", null, 100L, List.of(item(specCode, quantity)));
    }

    private GenerateServiceCodeItemCommand item(String specCode, int quantity) {
        return new GenerateServiceCodeItemCommand(specCode, quantity, null);
    }

    private ServiceDurationConfig spec(String specCode) {
        ServiceDurationConfig spec = new ServiceDurationConfig();
        spec.setSpecCode(specCode);
        spec.setServiceType("CORS");
        spec.setDurationValue(1);
        spec.setDurationUnit(DurationUnit.MONTH);
        spec.setCodeSilenceMonths(6);
        spec.setEnabled(true);
        return spec;
    }
}
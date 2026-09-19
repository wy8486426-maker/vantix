package com.sinognss.cloud.vantix.application.servicecode;

import com.sinognss.cloud.vantix.application.company.CompanyTransferTargetService;
import com.sinognss.cloud.vantix.application.config.SystemCompanyResolver;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.config.ServiceCodeTransferProperties;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeTransfer;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeTransferMapper;
import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeTransferServiceTest {
    private final ServiceCodeMapper codeMapper = mock(ServiceCodeMapper.class);
    private final ServiceCodeTransferMapper transferMapper = mock(ServiceCodeTransferMapper.class);
    private final CompanyTransferTargetService transferTargetService = mock(CompanyTransferTargetService.class);
    private final SystemCompanyResolver systemResolver = mock(SystemCompanyResolver.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private ServiceCodeTransferService service;

    @BeforeEach
    void setUp() {
        when(systemResolver.requireId()).thenReturn(999L);
        when(userHolder.getCurrentCompanyId()).thenReturn(1L);
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(7L, "tester"));
        doNothing().when(transferTargetService).validateTarget(anyLong(), anyLong());
        when(codeMapper.transferWithCas(anyLong(), anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(1);
        service = new ServiceCodeTransferService(codeMapper, transferMapper, transferTargetService,
                systemResolver, userHolder, clock, new ServiceCodeTransferProperties());
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void shouldTransferParentToChildAndRecordOperator() {
        ServiceCode code = activeCode(100L, 1L);
        when(codeMapper.selectList(any())).thenReturn(List.of(code));

        TransferResult result = service.transfer(new TransferServiceCodeCommand(2L, List.of(100L), "赠送"));

        assertEquals(1, result.transferredCount());
        verify(codeMapper).transferWithCas(100L, 1L, 2L, 0L,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(transferMapper).insert(any(com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeTransfer.class));
        ArgumentCaptor<ServiceCodeTransfer> captor = ArgumentCaptor.forClass(ServiceCodeTransfer.class);
        verify(transferMapper).insert(captor.capture());
        assertEquals("赠送", captor.getValue().getReason());

    }

    @Test
    void shouldTransferChildToParentAndAllowSystemDirections() {
        ServiceCode childCode = activeCode(100L, 2L);
        when(codeMapper.selectList(any())).thenReturn(List.of(childCode));
        when(userHolder.getCurrentCompanyId()).thenReturn(2L, 1L, 999L);
        TransferResult childToParent = service.transfer(new TransferServiceCodeCommand(1L, List.of(100L), null));
        assertEquals(1, childToParent.transferredCount());

        ServiceCode toSystemCode = activeCode(101L, 1L);
        when(codeMapper.selectList(any())).thenReturn(List.of(toSystemCode));
        assertEquals(1, service.transfer(new TransferServiceCodeCommand(999L, List.of(101L), null)).transferredCount());

        ServiceCode fromSystemCode = activeCode(102L, 999L);
        when(codeMapper.selectList(any())).thenReturn(List.of(fromSystemCode));
        assertEquals(1, service.transfer(new TransferServiceCodeCommand(1L, List.of(102L), null)).transferredCount());
    }

    @Test
    void shouldRejectUnrelatedCompany() {
        doThrow(new BusinessException(ErrorCode.TRANSFER_NOT_ALLOWED, "unrelated"))
                .when(transferTargetService).validateTarget(1L, 3L);
        assertThrows(BusinessException.class,
                () -> service.transfer(new TransferServiceCodeCommand(3L, List.of(100L), null)));
    }

    @Test
    void unsupportedDataTypeDoesNotBlockTransferWhenCompanyIdIsValid() {
        UserHolderBridge actualUserHolder = spy(new UserHolderBridge());
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(7L);
        user.setUserNickname("tester");
        user.setCompanyId(1L);
        user.setDataType(99);
        UserHolder.setUser(user);

        ServiceCodeTransferService serviceWithActualUserHolder = new ServiceCodeTransferService(
                codeMapper, transferMapper, transferTargetService, systemResolver,
                actualUserHolder, clock, new ServiceCodeTransferProperties());
        ServiceCode code = activeCode(100L, 1L);
        when(codeMapper.selectList(any())).thenReturn(List.of(code));

        assertEquals(1, serviceWithActualUserHolder
                .transfer(new TransferServiceCodeCommand(2L, List.of(100L), null))
                .transferredCount());
        verify(actualUserHolder, never()).getUserScope();
    }

    @Test
    void globalUserCanOnlyTransferCodesOwnedByCurrentCompany() {
        when(userHolder.getCurrentCompanyId()).thenReturn(10L);
        ServiceCode code = activeCode(100L, 20L);
        when(codeMapper.selectList(any())).thenReturn(List.of(code));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.transfer(new TransferServiceCodeCommand(30L, List.of(100L), null)));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
        verify(transferTargetService).validateTarget(10L, 30L);
    }

    @Test
    void shouldRollbackWholeBatchWhenOneCodeIsInvalid() {
        ServiceCode valid = activeCode(100L, 1L);
        ServiceCode consumed = activeCode(101L, 1L);
        consumed.setStatus(ServiceCodeStatus.CONSUMED);
        when(codeMapper.selectList(any())).thenReturn(List.of(valid, consumed));

        assertThrows(BusinessException.class,
                () -> service.transfer(new TransferServiceCodeCommand(2L, List.of(100L, 101L), null)));
        verify(codeMapper, org.mockito.Mockito.never())
                .transferWithCas(anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
        verify(transferMapper, org.mockito.Mockito.never())
                .insert(any(com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeTransfer.class));
    }

    @Test
    void shouldTransferBatchWithDifferentSpecsAndDurations() {
        ServiceCode first = activeCode(100L, 1L);
        ServiceCode second = activeCode(101L, 1L);
        first.setSpecCode("SPEC-A");
        first.setDurationDays(30);
        second.setSpecCode("SPEC-B");
        second.setServiceType("OTHER");
        second.setDurationDays(90);
        when(codeMapper.selectList(any())).thenReturn(List.of(first, second));

        TransferResult result = service.transfer(
                new TransferServiceCodeCommand(2L, List.of(100L, 101L), null));

        assertEquals(2, result.transferredCount());
        verify(codeMapper, org.mockito.Mockito.times(2))
                .transferWithCas(anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
        verify(transferMapper, org.mockito.Mockito.times(2)).insert(any(ServiceCodeTransfer.class));
    }

    @Test
    void shouldRejectBatchAboveConfiguredMaximum() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 501).boxed().toList();

        assertThrows(BusinessException.class,
                () -> service.transfer(new TransferServiceCodeCommand(2L, ids, null)));
        verify(codeMapper, org.mockito.Mockito.never()).selectList(any());
    }

    @Test
    void shouldRejectExpiredCodeAndSameCompany() {
        ServiceCode expired = activeCode(100L, 1L);
        expired.setExpireAt(LocalDateTime.of(2025, 12, 31, 23, 59));
        when(codeMapper.selectList(any())).thenReturn(List.of(expired));
        assertThrows(BusinessException.class,
                () -> service.transfer(new TransferServiceCodeCommand(2L, List.of(100L), null)));
        assertThrows(BusinessException.class,
                () -> service.transfer(new TransferServiceCodeCommand(1L, List.of(100L), null)));
    }

    @Test
    void shouldAllowAtMostOneConcurrentTransferWhenCasCompetes() throws Exception {
        ServiceCode code = activeCode(100L, 1L);
        when(codeMapper.selectList(any())).thenReturn(List.of(code));
        AtomicInteger casCalls = new AtomicInteger();
        when(codeMapper.transferWithCas(anyLong(), anyLong(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(invocation -> casCalls.getAndIncrement() == 0 ? 1 : 0);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> transferWithoutPropagating(start));
            Future<Boolean> second = executor.submit(() -> transferWithoutPropagating(start));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean transferWithoutPropagating(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.transfer(new TransferServiceCodeCommand(2L, List.of(100L), null));
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private ServiceCode activeCode(Long id, Long owner) {
        ServiceCode code = new ServiceCode();
        code.setId(id);
        code.setCode("CODE-" + id);
        code.setOwnerCompanyId(owner);
        code.setStatus(ServiceCodeStatus.PENDING);
        code.setExpireAt(LocalDateTime.of(2026, 2, 1, 0, 0));
        code.setVersion(0L);
        return code;
    }

}

package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestAccountIssueServiceTest {
    private final TestAccountIssueBatchMapper batchMapper = mock(TestAccountIssueBatchMapper.class);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final ServiceDurationConfigMapper durationMapper = mock(ServiceDurationConfigMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final TestAccountIssueProcessor processor = mock(TestAccountIssueProcessor.class);
    private final TestAccountIssueQueryService queryService = mock(TestAccountIssueQueryService.class);
    private TestAccountIssueService service;

    @BeforeEach
    void setUp() {
        service = new TestAccountIssueService(batchMapper, operationMapper, durationMapper, companyMapper,
                userHolder, new GenerationProperties(), Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"),
                ZoneId.of("Asia/Shanghai")), mock(PlatformTransactionManager.class), processor, queryService);
    }

    @Test
    void onlyGlobalScopeMayIssueTestAccounts() {
        for (UserScope scope : new UserScope[]{new UserScope(null, 123L), new UserScope(7L, 123L),
                new UserScope(7L, null)}) {
            when(userHolder.getUserScope()).thenReturn(scope);
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.reserve(new TestAccountIssueCommand("T-1", 123L, 1, "S1", "TEST")));
            assertEquals(scope.type() == UserScope.Type.UNSUPPORTED
                            ? ErrorCode.UNSUPPORTED_USER_SCOPE : ErrorCode.GLOBAL_SCOPE_REQUIRED,
                    error.getVantixErrorCode());
        }
        verifyNoInteractions(batchMapper, durationMapper, operationMapper);
    }

    @Test
    void freezesEnabledSpecAndCreatesSeparateCorsRequestOperation() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        when(companyMapper.selectCount(any())).thenReturn(1L);
        ServiceDurationConfig spec = new ServiceDurationConfig();
        spec.setSpecCode("S1");
        spec.setDisplayName("测试规格");
        spec.setServiceType("STANDARD");
        spec.setDurationDays(365);
        spec.setAccountSilenceDays(30);
        when(durationMapper.selectEnabledBySpecCode("S1")).thenReturn(spec);
        when(batchMapper.insert(any(TestAccountIssueBatch.class))).thenAnswer(invocation -> {
            ((TestAccountIssueBatch) invocation.getArgument(0)).setId(9L);
            return 1;
        });
        when(operationMapper.insert(any(CorsOperation.class))).thenAnswer(invocation -> {
            ((CorsOperation) invocation.getArgument(0)).setId(41L);
            return 1;
        });
        when(userHolder.getOperator()).thenReturn(new com.sinognss.cloud.vantix.common.user.OperatorIdentity(7L, "op"));

        TestAccountIssueReservation reservation = service.reserveTransaction(
                TestAccountIssueService.normalize(new TestAccountIssueCommand("API-1", 123L, 2, " S1 ", null)),
                "hash");

        assertEquals(new TestAccountIssueReservation(9L, 41L, true), reservation);
        ArgumentCaptor<TestAccountIssueBatch> batchCaptor = ArgumentCaptor.forClass(TestAccountIssueBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertEquals("TEST", batchCaptor.getValue().getAccountPrefix());
        assertEquals("测试规格", batchCaptor.getValue().getDisplayName());
        assertEquals(365, batchCaptor.getValue().getDurationDays());
        assertEquals(30, batchCaptor.getValue().getAccountSilenceDays());
        ArgumentCaptor<CorsOperation> operationCaptor = ArgumentCaptor.forClass(CorsOperation.class);
        verify(operationMapper).insert(operationCaptor.capture());
        assertEquals("TEST_ACCOUNT_CREATE", operationCaptor.getValue().getOperationType());
        assertEquals("TEST_ACCOUNT_ISSUE_BATCH", operationCaptor.getValue().getBizType());
        org.junit.jupiter.api.Assertions.assertTrue(operationCaptor.getValue().getRequestId().startsWith("TEST_"));
        org.junit.jupiter.api.Assertions.assertNotEquals("API-1", operationCaptor.getValue().getRequestId());
    }
}

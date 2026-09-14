package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeQueryServiceTest {
    private final ExchangeBatchMapper batchMapper = mock(ExchangeBatchMapper.class);
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private ExchangeQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeQueryService(batchMapper, accountMapper, userHolder);
    }

    @Test
    void personalUserCanReadOwnBatchButAnotherUserInSameCompanyCannot() {
        ExchangeBatch batch = batch(100L, 88L, ExchangeStatus.PROCESSING);
        when(batchMapper.selectByRequestId("request-a")).thenReturn(batch);
        when(userHolder.getUserScope()).thenReturn(new UserScope(88L, 100L));

        ServiceCodeExchangeView result = service.get("request-a");

        assertEquals("PROCESSING", result.status());
        when(userHolder.getUserScope()).thenReturn(new UserScope(89L, 100L));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.get("request-a"));
        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
        verify(accountMapper, never()).selectByExchangeBatchId(batch.getId());
    }

    @Test
    void personalUserCannotReadBatchWithoutFrozenOwner() {
        when(batchMapper.selectByRequestId("request-unassigned"))
                .thenReturn(batch(100L, null, ExchangeStatus.PROCESSING));
        when(userHolder.getUserScope()).thenReturn(new UserScope(88L, 100L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.get("request-unassigned"));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
        verify(accountMapper, never()).selectByExchangeBatchId(12L);
    }

    @Test
    void companyScopeCanReadOnlyItsCompanyAndGlobalScopeKeepsCrossCompanyAccess() {
        ExchangeBatch companyBatch = batch(100L, null, ExchangeStatus.PROCESSING);
        ExchangeBatch otherBatch = batch(200L, null, ExchangeStatus.PROCESSING);
        when(batchMapper.selectByRequestId("company")).thenReturn(companyBatch);
        when(batchMapper.selectByRequestId("other")).thenReturn(otherBatch);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 100L));

        assertEquals("PROCESSING", service.get("company").status());
        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, assertThrows(BusinessException.class,
                () -> service.get("other")).getVantixErrorCode());

        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        assertEquals("PROCESSING", service.get("other").status());
    }

    @Test
    void personalCompletedQueryChecksEveryAccountOwnerBeforeReturningAccounts() {
        ExchangeBatch batch = batch(100L, 88L, ExchangeStatus.COMPLETED);
        when(batchMapper.selectByRequestId("completed")).thenReturn(batch);
        when(userHolder.getUserScope()).thenReturn(new UserScope(88L, 100L));
        ServiceAccount ownAccount = account(88L);
        when(accountMapper.selectByExchangeBatchId(batch.getId())).thenReturn(List.of(ownAccount));

        ServiceCodeExchangeView result = service.get("completed");

        assertEquals(1, result.accounts().size());
        ServiceAccount foreignAccount = account(89L);
        when(accountMapper.selectByExchangeBatchId(batch.getId())).thenReturn(List.of(ownAccount, foreignAccount));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.get("completed"));
        assertEquals(ErrorCode.EXCHANGE_STATE_INCONSISTENT, exception.getVantixErrorCode());
    }

    private ExchangeBatch batch(Long companyId, Long assignedUserId, ExchangeStatus status) {
        ExchangeBatch batch = new ExchangeBatch();
        batch.setId(12L);
        batch.setRequestId("test-request");
        batch.setOwnerCompanyId(companyId);
        batch.setAssignedUserId(assignedUserId);
        batch.setStatus(status);
        batch.setSpecCode("M1");
        batch.setGenerationSource("B2B");
        batch.setQuantity(1);
        return batch;
    }

    private ServiceAccount account(Long assignedUserId) {
        ServiceAccount account = new ServiceAccount();
        account.setAssignedUserId(assignedUserId);
        account.setCorsAccountId("cors-account");
        account.setAccount("account");
        account.setCorsStatus("ENABLED");
        account.setCorsActivationStatus("WAITING_ACTIVATION");
        return account;
    }
}

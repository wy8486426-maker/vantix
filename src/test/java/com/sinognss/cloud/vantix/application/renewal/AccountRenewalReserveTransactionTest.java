package com.sinognss.cloud.vantix.application.renewal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRenewalReserveTransactionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);
    private final AccountRenewalMapper renewalMapper = mock(AccountRenewalMapper.class);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final ServiceCodeMapper serviceCodeMapper = mock(ServiceCodeMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final AccountRenewalReserveTransaction service = new AccountRenewalReserveTransaction(
            renewalMapper, operationMapper, accountMapper, serviceCodeMapper,
            new ObjectMapper().registerModule(new JavaTimeModule()), clock);

    @BeforeEach
    void setup() {
        when(renewalMapper.selectByRequestId("RN-1")).thenReturn(null);
        when(renewalMapper.selectByRequestIdForUpdate("RN-1")).thenReturn(null);
        when(renewalMapper.selectActiveByAccount(11L)).thenReturn(null);
        when(renewalMapper.insert(any(AccountRenewal.class))).thenAnswer(invocation -> {
            ((AccountRenewal) invocation.getArgument(0)).setId(101L);
            return 1;
        });
        when(operationMapper.insert(any(CorsOperation.class))).thenAnswer(invocation -> {
            ((CorsOperation) invocation.getArgument(0)).setId(201L);
            return 1;
        });
        when(serviceCodeMapper.reserveForRenewal(eq(21L), eq(7L), eq("RN-1"), eq(4L), any()))
                .thenReturn(1);
    }

    @Test
    void reservesAccountCodeRenewalAndOperationInOneUnitWithImmutableCodeSnapshot() {
        when(accountMapper.selectByIdForUpdate(11L)).thenReturn(account());
        when(serviceCodeMapper.selectByIdForUpdate(21L)).thenReturn(code());

        AccountRenewalReservation result = service.reserve(command(), new UserScope(23L, 7L),
                new OperatorIdentity(23L, "operator"));

        assertEquals(new AccountRenewalReservation(101L, 201L, "RN-1", true), result);
        ArgumentCaptor<AccountRenewal> renewalCaptor = ArgumentCaptor.forClass(AccountRenewal.class);
        verify(renewalMapper).insert(renewalCaptor.capture());
        AccountRenewal renewal = renewalCaptor.getValue();
        assertEquals(11L, renewal.getServiceAccountId());
        assertEquals(21L, renewal.getServiceCodeId());
        assertEquals(7L, renewal.getOwnerCompanyId());
        assertEquals(23L, renewal.getAssignedUserId());
        assertEquals("PROCESSING", renewal.getStatus());
        assertEquals(0L, renewal.getVersion());
        assertNotNull(renewal.getServiceCodeSnapshot());
        assertTrue(renewal.getServiceCodeSnapshot().contains("CODE-21"));
        verify(serviceCodeMapper).reserveForRenewal(21L, 7L, "RN-1", 4L, NOW);

        ArgumentCaptor<CorsOperation> operationCaptor = ArgumentCaptor.forClass(CorsOperation.class);
        verify(operationMapper).insert(operationCaptor.capture());
        CorsOperation operation = operationCaptor.getValue();
        assertEquals(AccountRenewalConstants.OPERATION_TYPE, operation.getOperationType());
        assertEquals(AccountRenewalConstants.BIZ_TYPE, operation.getBizType());
        assertEquals(101L, operation.getBizId());
        assertEquals(11L, operation.getServiceAccountId());
        assertEquals("PENDING", operation.getStatus());
    }

    @Test
    void anExistingUnresolvedRenewalBlocksBeforeReadingOrReservingAnotherCode() {
        when(accountMapper.selectByIdForUpdate(11L)).thenReturn(account());
        AccountRenewal active = new AccountRenewal();
        active.setId(100L);
        when(renewalMapper.selectActiveByAccount(11L)).thenReturn(active);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reserve(command(), new UserScope(23L, 7L), new OperatorIdentity(23L, "operator")));

        assertEquals(ErrorCode.ACCOUNT_RENEWAL_IN_PROGRESS, error.getVantixErrorCode());
        verify(serviceCodeMapper, never()).selectByIdForUpdate(21L);
        verify(renewalMapper, never()).insert(any(AccountRenewal.class));
    }

    @Test
    void requestIdRetryReturnsExistingReservationWithoutTouchingCode() {
        AccountRenewal existing = new AccountRenewal();
        existing.setId(101L);
        existing.setRequestId("RN-1");
        existing.setServiceAccountId(11L);
        existing.setServiceCodeId(21L);
        existing.setOwnerCompanyId(7L);
        existing.setAssignedUserId(23L);
        when(renewalMapper.selectByRequestId("RN-1")).thenReturn(existing);
        CorsOperation operation = new CorsOperation();
        operation.setId(201L);
        operation.setRequestId("RN-1");
        operation.setOperationType(AccountRenewalConstants.OPERATION_TYPE);
        operation.setBizType(AccountRenewalConstants.BIZ_TYPE);
        operation.setBizId(101L);
        operation.setServiceAccountId(11L);
        when(operationMapper.selectByBusiness(AccountRenewalConstants.BIZ_TYPE, 101L)).thenReturn(operation);

        AccountRenewalReservation result = service.reserve(command(), new UserScope(23L, 7L),
                new OperatorIdentity(23L, "operator"));

        assertEquals(new AccountRenewalReservation(101L, 201L, "RN-1", false), result);
        verify(accountMapper, never()).selectByIdForUpdate(11L);
        verify(serviceCodeMapper, never()).selectByIdForUpdate(21L);
        verify(operationMapper, never()).insert(any(CorsOperation.class));
    }

    @Test
    void serviceCodeMustBelongToAccountCompanyAndRemainPending() {
        when(accountMapper.selectByIdForUpdate(11L)).thenReturn(account());
        ServiceCode mismatched = code();
        mismatched.setOwnerCompanyId(8L);
        when(serviceCodeMapper.selectByIdForUpdate(21L)).thenReturn(mismatched);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reserve(command(), new UserScope(23L, 7L), new OperatorIdentity(23L, "operator")));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, error.getVantixErrorCode());
        verify(serviceCodeMapper, never()).reserveForRenewal(any(), any(), any(), any(), any());
        verify(operationMapper, never()).insert(any(CorsOperation.class));
    }

    @Test
    void localAccountMustAlreadyBeActivatedBeforeReservingARenewal() {
        ServiceAccount account = account();
        account.setCorsActivationStatus("WAITING_ACTIVATION");
        when(accountMapper.selectByIdForUpdate(11L)).thenReturn(account);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reserve(command(), new UserScope(23L, 7L), new OperatorIdentity(23L, "operator")));

        assertEquals(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT, error.getVantixErrorCode());
        verify(serviceCodeMapper, never()).selectByIdForUpdate(21L);
        verify(renewalMapper, never()).insert(any(AccountRenewal.class));
    }

    @Test
    void expiredAccountCanReserveButWaitingActivationCannot() {
        ServiceAccount expired = account();
        expired.setCorsActivationStatus("EXPIRED");
        when(accountMapper.selectByIdForUpdate(11L)).thenReturn(expired);
        when(renewalMapper.selectByRequestId(anyString())).thenReturn(null);
        when(renewalMapper.selectByRequestIdForUpdate(anyString())).thenReturn(null);
        when(renewalMapper.selectActiveByAccount(11L)).thenReturn(null);
        when(serviceCodeMapper.selectByIdForUpdate(21L)).thenReturn(code());
        when(renewalMapper.insert(any(AccountRenewal.class))).thenAnswer(invocation -> {
            AccountRenewal renewal = invocation.getArgument(0);
            renewal.setId(51L);
            return 1;
        });
        when(serviceCodeMapper.reserveForRenewal(anyLong(), anyLong(), anyString(), anyLong(), any()))
                .thenReturn(1);
        when(operationMapper.insert(any(CorsOperation.class))).thenReturn(1);

        service.reserve(command(), new UserScope(23L, 7L), new OperatorIdentity(23L, "operator"));
        verify(serviceCodeMapper).reserveForRenewal(eq(21L), eq(7L), eq("RN-1"), eq(4L), any());

        ServiceAccount waiting = account();
        waiting.setCorsActivationStatus("WAITING_ACTIVATION");
        when(accountMapper.selectByIdForUpdate(11L)).thenReturn(waiting);
        assertThrows(BusinessException.class,
                () -> service.reserve(new CreateAccountRenewalCommand("RN-2", 11L, 21L),
                        new UserScope(23L, 7L), new OperatorIdentity(23L, "operator")));
    }

    private static CreateAccountRenewalCommand command() {
        return new CreateAccountRenewalCommand("RN-1", 11L, 21L);
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(11L);
        account.setCorsAccountId("cors-11");
        account.setAccount("account-11");
        account.setOwnerCompanyId(7L);
        account.setAssignedUserId(23L);
        account.setCorsActivationStatus("ACTIVE");
        account.setServiceType("CORS");
        account.setVersion(2L);
        return account;
    }

    private static ServiceCode code() {
        ServiceCode code = new ServiceCode();
        code.setId(21L);
        code.setCode("CODE-21");
        code.setOwnerCompanyId(7L);
        code.setServiceType("CORS");
        code.setSpecCode("SPEC-1");
        code.setDurationDays(90);
        code.setCodeSilenceDays(360);
        code.setExpireAt(NOW.plusDays(1));
        code.setStatus(ServiceCodeStatus.PENDING);
        code.setVersion(4L);
        return code;
    }
}

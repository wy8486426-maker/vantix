package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRenewalQueryServiceTest {
    private static final String REQUEST_ID = "RENEWAL-DETAIL-1";

    private final AccountRenewalMapper renewalMapper = mock(AccountRenewalMapper.class);
    private final AccountRenewalLogQueryMapper logQueryMapper = mock(AccountRenewalLogQueryMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private AccountRenewalQueryService service;

    @BeforeEach
    void setUp() {
        service = new AccountRenewalQueryService(renewalMapper, logQueryMapper, userHolder);
    }

    @Test
    void personalDetailKeepsExistingViewContractAndUsesLocalSnapshotQuery() {
        AccountRenewal renewal = renewal();
        when(renewalMapper.selectByRequestId(REQUEST_ID)).thenReturn(renewal);
        when(userHolder.getUserScope()).thenReturn(new UserScope(7L, 10L));
        AccountRenewalLogQueryRow row = row();
        when(logQueryMapper.detailForFrontend(REQUEST_ID, 10L, 7L)).thenReturn(row);

        AccountRenewalView view = service.get(REQUEST_ID);

        assertEquals(REQUEST_ID, view.requestId());
        assertEquals(11, view.codeSilenceDays());
        assertEquals("历史规格", view.displayName());
        assertEquals("RENEWAL-CODE-1", view.serviceCode());
        assertEquals("账号一", view.accountName());
        verify(logQueryMapper).detailForFrontend(REQUEST_ID, 10L, 7L);
    }

    @Test
    void personalDetailRejectsAnotherAssignedUserBeforeLoadingDetail() {
        AccountRenewal renewal = renewal();
        when(renewalMapper.selectByRequestId(REQUEST_ID)).thenReturn(renewal);
        when(userHolder.getUserScope()).thenReturn(new UserScope(8L, 10L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.get(REQUEST_ID));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
        verify(logQueryMapper, never()).detailForFrontend(eq(REQUEST_ID), eq(10L), eq(8L));
    }

    private AccountRenewal renewal() {
        AccountRenewal renewal = new AccountRenewal();
        renewal.setId(1L);
        renewal.setRequestId(REQUEST_ID);
        renewal.setServiceAccountId(21L);
        renewal.setServiceCodeId(31L);
        renewal.setOwnerCompanyId(10L);
        renewal.setAssignedUserId(7L);
        renewal.setCodeSilenceDays(11);
        return renewal;
    }

    private AccountRenewalLogQueryRow row() {
        AccountRenewalLogQueryRow row = new AccountRenewalLogQueryRow();
        row.setRenewalId(1L);
        row.setRequestId(REQUEST_ID);
        row.setServiceAccountId(21L);
        row.setServiceCodeId(31L);
        row.setSpecCode("S1");
        row.setServiceType("CORS");
        row.setDurationDays(30);
        row.setCodeSilenceDays(11);
        row.setStatus("PROCESSING");
        row.setServiceCode("RENEWAL-CODE-1");
        row.setDisplayName("历史规格");
        row.setAccountName("账号一");
        row.setOwnerCompanyId(10L);
        row.setOwnerCompanyName("公司十");
        row.setAssignedUserId(7L);
        row.setCreatedAt(LocalDateTime.of(2026, 9, 16, 10, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 9, 16, 10, 0));
        return row;
    }
}

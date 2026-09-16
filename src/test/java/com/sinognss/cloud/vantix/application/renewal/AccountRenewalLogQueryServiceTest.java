package com.sinognss.cloud.vantix.application.renewal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRenewalLogQueryServiceTest {
    private final AccountRenewalLogQueryMapper mapper = mock(AccountRenewalLogQueryMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private AccountRenewalLogQueryService service;

    @BeforeEach
    void setUp() { service = new AccountRenewalLogQueryService(mapper, userHolder); }

    @Test
    void personalListPassesCompanyAndAssignedUserScopeAndFilters() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(7L, 10L));
        Page<AccountRenewalLogQueryRow> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of());
        when(mapper.pageForFrontend(any(), eq("account"), eq("COMPLETED"), eq(null), eq(null), eq(null),
                eq(10L), eq(7L))).thenReturn(page);

        service.page(new AccountRenewalLogPageQuery(1, 20, "account", "COMPLETED", null, null, null));

        verify(mapper).pageForFrontend(any(), eq("account"), eq("COMPLETED"), eq(null), eq(null), eq(null),
                eq(10L), eq(7L));
    }

    @Test
    void companyCannotUseOwnerFilterForAnotherCompany() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 10L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.page(new AccountRenewalLogPageQuery(1, 20, null, null, 20L, null, null)));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
    }

    @Test
    void invalidDateRangeIsRejectedBeforeMapper() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.page(new AccountRenewalLogPageQuery(1, 20, null, null, null,
                        java.time.LocalDateTime.of(2026, 9, 2, 0, 0),
                        java.time.LocalDateTime.of(2026, 9, 1, 0, 0))));

        assertEquals(ErrorCode.INVALID_ARGUMENT, exception.getVantixErrorCode());
    }
}

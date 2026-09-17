package com.sinognss.cloud.vantix.application.account;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceAccountQueryServiceTest {
    private final ServiceAccountQueryMapper mapper = mock(ServiceAccountQueryMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private ServiceAccountQueryService service;

    @BeforeEach
    void setUp() {
        service = new ServiceAccountQueryService(mapper, userHolder);
    }

    @Test
    void globalPagePassesNoScopeAndReturnsOnlySafeAccountFields() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        Page<ServiceAccountQueryRow> page = new Page<>(1, 20);
        ServiceAccountQueryRow row = new ServiceAccountQueryRow();
        row.setId(1L);
        row.setAccountName("cors-account");
        row.setStatus("ACTIVE");
        page.setRecords(java.util.List.of(row));
        page.setTotal(1);
        when(mapper.pageForFrontend(any(), eq("account"), eq("ACTIVE"), eq("S1"), eq(30),
                eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(page);

        PageResponse<ServiceAccountView> result = service.page(new ServiceAccountPageQuery(
                1, 20, " account ", "ACTIVE", "S1", 30, null, null));

        assertEquals(1, result.total());
        assertEquals("cors-account", result.records().get(0).accountName());
        assertFalse(Arrays.stream(ServiceAccountView.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("password")));
        verify(mapper).pageForFrontend(any(), eq("account"), eq("ACTIVE"), eq("S1"), eq(30),
                eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void companyCannotExpandToAnotherCompanyAndPersonalScopeIsAssignedUserOnly() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 10L));
        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, assertThrows(BusinessException.class,
                () -> service.page(new ServiceAccountPageQuery(1, 20, null, null, null, null, 20L, null)))
                .getVantixErrorCode());

        when(userHolder.getUserScope()).thenReturn(new UserScope(7L, 10L));
        Page<ServiceAccountQueryRow> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of());
        when(mapper.pageForFrontend(any(), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(10L), eq(7L), eq(null))).thenReturn(page);
        service.page(new ServiceAccountPageQuery(1, 20, null, null, null, null, null, null));
        verify(mapper).pageForFrontend(any(), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(10L), eq(7L), eq(null));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, assertThrows(BusinessException.class,
                () -> service.page(new ServiceAccountPageQuery(1, 20, null, null, null, null, null, 8L)))
                .getVantixErrorCode());
    }

    @Test
    void statisticsUsesCommonFiltersWithoutListStatusFilter() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        ServiceAccountStatisticsRow row = new ServiceAccountStatisticsRow();
        row.setTotal(4L);
        row.setWaiting(1L);
        row.setActive(1L);
        row.setExpired(1L);
        row.setDisabled(1L);
        when(mapper.statistics(eq("x"), eq("S1"), eq(30), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(row);

        ServiceAccountStatistics result = service.statistics(new ServiceAccountStatisticsQuery(
                "x", "S1", 30, null, null));

        assertEquals(new ServiceAccountStatistics(4, 1, 1, 1, 1), result);
        verify(mapper).statistics(eq("x"), eq("S1"), eq(30), eq(null), eq(null), eq(null), eq(null));
        verify(mapper, never()).detailForFrontend(any(), any(), any());
    }

    @Test
    void pageCanFilterByAccountSourceAndReturnsItInTheView() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        Page<ServiceAccountQueryRow> page = new Page<>(1, 20);
        ServiceAccountQueryRow row = new ServiceAccountQueryRow();
        row.setAccountSource(AccountSource.HISTORY_IMPORT);
        row.setExchangeAt(null);
        row.setExchangeBatchNo(null);
        page.setRecords(java.util.List.of(row));
        when(mapper.pageForFrontend(any(), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq(null), eq(AccountSource.HISTORY_IMPORT))).thenReturn(page);

        ServiceAccountView result = service.page(new ServiceAccountPageQuery(1, 20, null, null, null, null,
                null, null, AccountSource.HISTORY_IMPORT)).records().get(0);

        assertEquals(AccountSource.HISTORY_IMPORT, result.accountSource());
        assertEquals(null, result.exchangeAt());
        assertEquals(null, result.exchangeBatchNo());
        verify(mapper).pageForFrontend(any(), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq(null), eq(AccountSource.HISTORY_IMPORT));
    }
}

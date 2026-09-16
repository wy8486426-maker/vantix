package com.sinognss.cloud.vantix.application.exchange;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeLogQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeLogQueryServiceTest {
    private final ExchangeLogQueryMapper mapper = mock(ExchangeLogQueryMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private ExchangeLogQueryService service;

    @BeforeEach
    void setUp() { service = new ExchangeLogQueryService(mapper, userHolder); }

    @Test
    void personalListUsesCompanyAssetScopeAndPreservesAggregatedSnapshot() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(7L, 10L));
        Page<ExchangeLogQueryRow> page = new Page<>(1, 20);
        ExchangeLogQueryRow row = new ExchangeLogQueryRow();
        row.setRequestId("REQ-1");
        row.setBatchId(2L);
        row.setSuccessQuantity(3L);
        row.setDisplayName("Historical name");
        page.setRecords(java.util.List.of(row));
        page.setTotal(1);
        when(mapper.pageForFrontend(any(), eq("REQ"), eq("COMPLETED"), eq("S1"), eq(null),
                eq(null), eq(null), eq(10L))).thenReturn(page);

        PageResponse<ExchangeLogView> result = service.page(new ExchangeLogPageQuery(1, 20, "REQ",
                ExchangeStatus.COMPLETED, "S1", null, null, null));

        assertEquals(1, result.total());
        assertEquals("Historical name", result.records().get(0).displayName());
        assertEquals(3, result.records().get(0).successQuantity());
        verify(mapper).pageForFrontend(any(), eq("REQ"), eq("COMPLETED"), eq("S1"), eq(null),
                eq(null), eq(null), eq(10L));
    }

    @Test
    void detailScopeReturnsNotFoundWithoutLoadingForeignItems() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 10L));
        when(mapper.detail("REQ-FOREIGN", 10L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.detail("REQ-FOREIGN"));

        assertEquals(ErrorCode.NOT_FOUND, exception.getVantixErrorCode());
        verify(mapper, never()).detailItems(any(), any());
    }
}

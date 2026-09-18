package com.sinognss.cloud.vantix.application.servicecode;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeServiceTest {
    private final ServiceCodeMapper codeMapper = mock(ServiceCodeMapper.class);
    private final ServiceCodeQueryMapper queryMapper = mock(ServiceCodeQueryMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final VantixProperties properties = new VantixProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneOffset.UTC);
    private ServiceCodeService service;

    @BeforeEach
    void setUp() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        properties.setUpcomingDays(7);
        service = new ServiceCodeService(codeMapper, queryMapper, userHolder, clock, properties);
    }

    @Test
    void displayStatusUsesTheFixedClockBoundaries() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        assertEquals(DisplayStatus.EXPIRED, service.displayStatus(ServiceCodeStatus.PENDING, now.minusNanos(1), now));
        assertEquals(DisplayStatus.EXPIRED, service.displayStatus(ServiceCodeStatus.PENDING, now, now));
        assertEquals(DisplayStatus.EXPIRING, service.displayStatus(ServiceCodeStatus.PENDING, now.plusNanos(1), now));
        assertEquals(DisplayStatus.EXPIRING, service.displayStatus(ServiceCodeStatus.PENDING, now.plusDays(7), now));
        assertEquals(DisplayStatus.WAITING, service.displayStatus(ServiceCodeStatus.PENDING,
                now.plusDays(7).plusNanos(1), now));
        assertEquals(DisplayStatus.PROCESSING, service.displayStatus(ServiceCodeStatus.PROCESSING,
                now.minusDays(100), now));
        assertEquals(DisplayStatus.CONSUMED, service.displayStatus(ServiceCodeStatus.CONSUMED,
                now.minusDays(100), now));
    }

    @Test
    void pageDelegatesAllFiltersToTheDatabaseProjection() {
        Page<ServiceCodeQueryRow> result = new Page<>(2, 3);
        result.setTotal(8);
        ServiceCodeQueryRow row = row(7L, ServiceCodeStatus.PENDING,
                LocalDateTime.of(2026, 9, 20, 10, 0));
        row.setDisplayName("90天活动版");
        row.setOwnerCompanyName("客户 A");
        result.setRecords(java.util.List.of(row));
        when(queryMapper.pageForFrontend(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(result);

        PageResponse<ServiceCodeView> response = service.page(new ServiceCodePageQuery(
                2, 3, "  活动版 ", ServiceCodeStatus.PENDING, DisplayStatus.EXPIRING,
                " SC001 ", 90, " ORDER-1 ", 10L));

        assertEquals(1, response.records().size());
        assertEquals("90天活动版", response.records().get(0).displayName());
        assertEquals("客户 A", response.records().get(0).ownerCompanyName());
        assertEquals(DisplayStatus.EXPIRING, response.records().get(0).displayStatus());
        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(queryMapper).pageForFrontend(any(), keyword.capture(), eq("PENDING"), eq("EXPIRING"),
                eq("SC001"), eq(90), eq("ORDER-1"), eq(10L), eq(null), any(), any());
        assertEquals("活动版", keyword.getValue());
    }

    @Test
    void companyScopeUsesCompanyPredicateWhenOwnerFilterIsAbsent() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 10L));
        when(queryMapper.pageForFrontend(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Page<>(1, 20));

        service.page(new ServiceCodePageQuery(1, 20, null, null, null,
                null, null, null, null));

        verify(queryMapper).pageForFrontend(any(), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq(10L), any(), any());
    }

    @Test
    void companyScopeRejectsDifferentOwnerFilterInsteadOfSilentlyChangingScope() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 10L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.page(new ServiceCodePageQuery(1, 20, null, null, null,
                        null, null, null, 11L)));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
    }

    @Test
    void unsupportedScopeIsRejectedForListAndStatistics() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(88L, null));

        BusinessException listException = assertThrows(BusinessException.class,
                () -> service.page(new ServiceCodePageQuery(1, 20, null, null, null,
                        null, null, null, null)));
        BusinessException statisticsException = assertThrows(BusinessException.class,
                () -> service.statistics(new ServiceCodeStatisticsQuery(null, null, null, null, null)));
        BusinessException specStatisticsException = assertThrows(BusinessException.class,
                () -> service.specStatistics(null));

        assertEquals(ErrorCode.UNSUPPORTED_USER_SCOPE, listException.getVantixErrorCode());
        assertEquals(ErrorCode.UNSUPPORTED_USER_SCOPE, statisticsException.getVantixErrorCode());
        assertEquals(ErrorCode.UNSUPPORTED_USER_SCOPE, specStatisticsException.getVantixErrorCode());
    }

    @Test
    void statisticsMapsConditionalAggregationWithoutLoadingCodes() {
        ServiceCodeStatisticsRow row = new ServiceCodeStatisticsRow();
        row.setTotal(20L);
        row.setWaiting(2L);
        row.setExpiring(3L);
        row.setExpired(4L);
        row.setProcessing(5L);
        row.setConsumed(6L);
        when(queryMapper.statistics(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(row);

        ServiceCodeStatistics statistics = service.statistics(new ServiceCodeStatisticsQuery(
                " code ", " SC001 ", 90, " ORDER-1 ", null));

        assertEquals(20, statistics.total());
        assertEquals(20, statistics.waiting() + statistics.expiring() + statistics.expired()
                + statistics.processing() + statistics.consumed());
        verify(queryMapper).statistics(eq("code"), eq("SC001"), eq(90), eq("ORDER-1"),
                eq(null), eq(null), any(), any());
    }

    @Test
    void specStatisticsMapsGroupedRowsAndUsesTheCurrentScope() {
        ServiceCodeSpecStatisticsRow row = new ServiceCodeSpecStatisticsRow();
        row.setSpecCode("SC001");
        row.setDisplayName("1个月");
        row.setDurationDays(30);
        row.setTotal(5L);
        row.setWaiting(1L);
        row.setExpiring(1L);
        row.setProcessing(1L);
        row.setConsumed(1L);
        row.setExpired(1L);
        when(queryMapper.specStatistics(any(), any(), any(), any())).thenReturn(List.of(row));

        ServiceCodeSpecStatistics statistics = service.specStatistics(null);

        assertEquals(5, statistics.total());
        assertEquals(1, statistics.items().size());
        assertEquals("SC001", statistics.items().get(0).specCode());
        assertEquals(30, statistics.items().get(0).durationDays());
        assertEquals(5, statistics.items().get(0).total());
        assertEquals(5, statistics.items().get(0).waiting() + statistics.items().get(0).expiring()
                + statistics.items().get(0).processing() + statistics.items().get(0).consumed()
                + statistics.items().get(0).expired());
        verify(queryMapper).specStatistics(eq(null), eq(null), any(), any());
    }

    @Test
    void specStatisticsRejectsCrossCompanyQueriesForCompanyScope() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 10L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.specStatistics(20L));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
    }

    @Test
    void invalidStatusCombinationAndInvalidValuesAreRejected() {
        assertThrows(BusinessException.class, () -> service.page(new ServiceCodePageQuery(
                1, 20, null, ServiceCodeStatus.PROCESSING, DisplayStatus.WAITING,
                null, null, null, null)));
        assertThrows(BusinessException.class, () -> service.page(new ServiceCodePageQuery(
                1, 20, "x".repeat(101), null, null, null, null, null, null)));
        assertThrows(BusinessException.class, () -> service.statistics(new ServiceCodeStatisticsQuery(
                null, null, 0, null, null)));
    }

    private ServiceCodeQueryRow row(Long id, ServiceCodeStatus status, LocalDateTime expireAt) {
        ServiceCodeQueryRow row = new ServiceCodeQueryRow();
        row.setId(id);
        row.setCode("CODE-" + id);
        row.setStatus(status);
        row.setExpireAt(expireAt);
        row.setSpecCode("SC001");
        row.setServiceType("NTRIP");
        row.setDurationDays(90);
        return row;
    }
}

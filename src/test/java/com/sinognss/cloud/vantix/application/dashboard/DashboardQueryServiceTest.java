package com.sinognss.cloud.vantix.application.dashboard;

import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.infrastructure.mapper.DashboardQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardQueryServiceTest {
    private final DashboardQueryMapper mapper = mock(DashboardQueryMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final VantixProperties properties = new VantixProperties();
    private DashboardQueryService service;

    @BeforeEach
    void setUp() {
        properties.setUpcomingDays(30);
        service = new DashboardQueryService(mapper, userHolder,
                Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC), properties);
    }

    @Test
    void globalCompanyAndPersonalUseTheExpectedMetricScopes() {
        when(mapper.statistics(eq(null), eq(null), eq(java.time.LocalDateTime.of(2026, 9, 16, 0, 0)),
                eq(java.time.LocalDateTime.of(2026, 10, 16, 0, 0))))
                .thenReturn(null);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        assertEquals(0, service.get().accountTotal());
        verify(mapper).statistics(eq(null), eq(null), eq(java.time.LocalDateTime.of(2026, 9, 16, 0, 0)),
                eq(java.time.LocalDateTime.of(2026, 10, 16, 0, 0)));

        when(userHolder.getUserScope()).thenReturn(new UserScope(7L, 10L));
        when(mapper.statistics(eq(10L), eq(7L), eq(java.time.LocalDateTime.of(2026, 9, 16, 0, 0)),
                eq(java.time.LocalDateTime.of(2026, 10, 16, 0, 0)))).thenReturn(null);
        assertEquals(0, service.get().serviceCodeTotal());
        verify(mapper).statistics(eq(10L), eq(7L), eq(java.time.LocalDateTime.of(2026, 9, 16, 0, 0)),
                eq(java.time.LocalDateTime.of(2026, 10, 16, 0, 0)));
    }
}

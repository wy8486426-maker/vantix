package com.sinognss.cloud.vantix.application.servicecode;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeServiceTest {
    private final ServiceCodeMapper codeMapper = mock(ServiceCodeMapper.class);
    private final ServiceCodeGenerateBatchMapper batchMapper = mock(ServiceCodeGenerateBatchMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final VantixProperties properties = new VantixProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private ServiceCodeService service;

    @BeforeEach
    void setUp() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        properties.setUpcomingDays(30);
        service = new ServiceCodeService(codeMapper, batchMapper, userHolder, clock, properties);
    }

    @Test
    void shouldCalculateExpiredDisplayStatusWithoutChangingPersistedStatus() {
        ServiceCode code = new ServiceCode();
        code.setStatus(ServiceCodeStatus.PENDING);
        code.setExpireAt(LocalDateTime.of(2025, 12, 31, 23, 59));

        assertEquals(DisplayStatus.EXPIRED, service.displayStatus(code, LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertEquals(ServiceCodeStatus.PENDING, code.getStatus());
    }

    @Test
    void shouldTranslateDynamicDisplayStatusIntoSqlPredicate() {
        when(codeMapper.selectPage(any(), any())).thenReturn(new Page<ServiceCode>(1, 20));

        service.page(1, 20, null, DisplayStatus.EXPIRING);

        ArgumentCaptor<Wrapper<ServiceCode>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(codeMapper).selectPage(any(), wrapper.capture());
        assertFalse(wrapper.getValue().getExpression().getNormal().isEmpty());
    }
}
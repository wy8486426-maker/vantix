package com.sinognss.cloud.vantix.application.servicecode;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeServiceTest {
    private final ServiceCodeMapper serviceCodeMapper = mock(ServiceCodeMapper.class);
    private final ServiceDurationConfigMapper durationMapper = mock(ServiceDurationConfigMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final VantixProperties properties = new VantixProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private ServiceCodeService service;

    @BeforeEach
    void setUp() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        properties.setUpcomingDays(30);
        service = new ServiceCodeService(serviceCodeMapper, durationMapper, userHolder, clock, properties);
    }

    @Test
    void shouldSnapshotSilenceConfigurationAtCreationTime() {
        ServiceDurationConfig config = duration(12);
        when(durationMapper.selectById(anyLong())).thenReturn(config);
        ServiceCode first = service.create(new CreateServiceCodeCommand("A", 1L, "O1", 10L, 1L));

        config.setCodeSilenceMonths(24);
        ServiceCode second = service.create(new CreateServiceCodeCommand("B", 1L, "O1", 10L, 1L));

        assertEquals(12, first.getCodeSilenceMonths());
        assertEquals(first.getCreatedAt().plusMonths(12), first.getExpireAt());
        assertEquals(24, second.getCodeSilenceMonths());
        assertNotEquals(first.getExpireAt(), second.getExpireAt());
    }

    @Test
    void shouldCalculateDisplayStatusWithoutPersistingExpiredState() {
        ServiceDurationConfig config = duration(12);

        when(durationMapper.selectById(anyLong())).thenReturn(config);
        var code = service.create(new CreateServiceCodeCommand("A", null, null, 10L, 1L));
        code.setExpireAt(clock.instant().atZone(ZoneOffset.UTC).toLocalDateTime().minusSeconds(1));

        assertEquals(DisplayStatus.EXPIRED, service.displayStatus(code,
                clock.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        assertEquals(com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus.PENDING, code.getStatus());
    }
    @Test
    void shouldTranslateDynamicDisplayStatusIntoSqlPredicate() {
        when(serviceCodeMapper.selectPage(any(), any())).thenReturn(new Page<ServiceCode>(1, 20));

        service.page(1, 20, null, DisplayStatus.EXPIRING);

        ArgumentCaptor<Wrapper<ServiceCode>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(serviceCodeMapper).selectPage(any(), wrapper.capture());
        assertFalse(wrapper.getValue().getExpression().getNormal().isEmpty());
    }


    private ServiceDurationConfig duration(int silenceMonths) {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setId(1L);
        config.setServiceType("CORS");
        config.setDurationValue(1);
        config.setDurationUnit(DurationUnit.MONTH);
        config.setCodeSilenceMonths(silenceMonths);
        config.setEnabled(true);
        return config;
    }
}

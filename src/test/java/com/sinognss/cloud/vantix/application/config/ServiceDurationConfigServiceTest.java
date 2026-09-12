package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDurationConfigServiceTest {
    private final ServiceDurationConfigMapper mapper = mock(ServiceDurationConfigMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private ServiceDurationConfigService service;

    @BeforeEach
    void setUp() {
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(7L, "operator"));
        when(mapper.selectCount(any())).thenReturn(0L);
        service = new ServiceDurationConfigService(mapper, userHolder);
    }

    @Test
    void createsStableSpecCodeFromDurationAndUnitWithoutUsingDatabaseId() {
        ServiceDurationConfigView created = service.create(new ServiceDurationConfigCommand(
                "CORS", 1, DurationUnit.MONTH, 12, true, null));

        ArgumentCaptor<ServiceDurationConfig> config = ArgumentCaptor.forClass(ServiceDurationConfig.class);
        verify(mapper).insert(config.capture());
        assertEquals("M1", config.getValue().getSpecCode());
        assertEquals("M1", created.specCode());
        assertEquals("1个月", created.displayName());
    }

    @Test
    void usesServiceTypeOnlyAsStableDisambiguatorWhenDurationCodeIsAlreadyUsed() {
        when(mapper.selectCount(any())).thenReturn(0L, 1L, 0L);

        ServiceDurationConfigView created = service.create(new ServiceDurationConfigCommand(
                "CORS", 1, DurationUnit.MONTH, 12, true, null));

        assertEquals("M1-S434F5253", created.specCode());
    }

    @Test
    void ordinaryUpdateCannotChangeThePersistedSpecCode() {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setId(1L);
        config.setSpecCode("M1");
        config.setServiceType("CORS");
        config.setDurationValue(1);
        config.setDurationUnit(DurationUnit.MONTH);
        config.setCodeSilenceMonths(12);
        config.setEnabled(true);
        when(mapper.selectById(1L)).thenReturn(config);

        ServiceDurationConfigView updated = service.update(1L,
                new ServiceDurationConfigCommand("CORS", 3, DurationUnit.MONTH, 6, false, "changed"));

        assertEquals("M1", updated.specCode());
        assertEquals(3, updated.durationValue());
        verify(mapper).updateById(config);
    }
}
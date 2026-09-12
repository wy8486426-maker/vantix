package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void createsCanonicalSpecCodesFromDurationAndUnitWithoutServiceTypeSuffix() {
        List<SpecCase> cases = List.of(
                new SpecCase(1, DurationUnit.DAY, "D1"),
                new SpecCase(7, DurationUnit.DAY, "D7"),
                new SpecCase(1, DurationUnit.WEEK, "W1"),
                new SpecCase(1, DurationUnit.MONTH, "M1"),
                new SpecCase(3, DurationUnit.MONTH, "M3"),
                new SpecCase(1, DurationUnit.YEAR, "Y1"),
                new SpecCase(12, DurationUnit.MONTH, "M12"));

        for (SpecCase specCase : cases) {
            ServiceDurationConfigView created = service.create(new ServiceDurationConfigCommand(
                    "CORS", specCase.value(), specCase.unit(), 12, true, null));
            assertEquals(specCase.code(), created.specCode());
        }

        ArgumentCaptor<ServiceDurationConfig> config = ArgumentCaptor.forClass(ServiceDurationConfig.class);
        verify(mapper, times(7)).insert(config.capture());
        assertEquals(List.of("D1", "D7", "W1", "M1", "M3", "Y1", "M12"),
                config.getAllValues().stream().map(ServiceDurationConfig::getSpecCode).toList());
    }

    @Test
    void rejectsSameDurationForAnotherServiceType() {
        when(mapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new ServiceDurationConfigCommand(
                        "SDK", 1, DurationUnit.MONTH, 12, true, null)));

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getVantixErrorCode());
        verify(mapper, never()).insert(any(ServiceDurationConfig.class));
    }

    @Test
    void mapsConcurrentDatabaseDurationOrSpecDuplicateToConfigInvalid() {
        when(mapper.insert(any(ServiceDurationConfig.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new ServiceDurationConfigCommand(
                        "CORS", 1, DurationUnit.MONTH, 12, true, null)));

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getVantixErrorCode());
    }

    @Test
    void rejectsChangingDurationValueAfterCreation() {
        assertImmutableUpdateRejected(new ServiceDurationConfigCommand(
                "CORS", 3, DurationUnit.MONTH, 6, false, "changed"));
    }

    @Test
    void rejectsChangingDurationUnitAfterCreation() {
        assertImmutableUpdateRejected(new ServiceDurationConfigCommand(
                "CORS", 1, DurationUnit.YEAR, 6, false, "changed"));
    }

    @Test
    void rejectsChangingLegacyServiceTypeAfterCreation() {
        assertImmutableUpdateRejected(new ServiceDurationConfigCommand(
                "SDK", 1, DurationUnit.MONTH, 6, false, "changed"));
    }

    @Test
    void allowsUpdatingSilenceEnabledAndRemarkWithoutChangingIdentity() {
        ServiceDurationConfig config = existingMonth();
        when(mapper.selectById(1L)).thenReturn(config);

        ServiceDurationConfigView updated = service.update(1L,
                new ServiceDurationConfigCommand("CORS", 1, DurationUnit.MONTH, 6, false, "changed"));

        assertEquals("M1", updated.specCode());
        assertEquals("CORS", updated.serviceType());
        assertEquals(1, updated.durationValue());
        assertEquals(DurationUnit.MONTH, updated.durationUnit());
        assertEquals(6, updated.codeSilenceMonths());
        assertEquals(false, updated.enabled());
        assertEquals("changed", updated.remark());
        assertEquals(7L, config.getUpdatedBy());
        verify(mapper).updateById(config);
    }

    private void assertImmutableUpdateRejected(ServiceDurationConfigCommand command) {
        ServiceDurationConfig config = existingMonth();
        when(mapper.selectById(1L)).thenReturn(config);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update(1L, command));

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getVantixErrorCode());
        assertEquals("服务时长创建后不可修改，请停用旧规格并新建规格", exception.getMessage());
        verify(mapper, never()).updateById(any(ServiceDurationConfig.class));
    }

    private ServiceDurationConfig existingMonth() {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setId(1L);
        config.setSpecCode("M1");
        config.setServiceType("CORS");
        config.setDurationValue(1);
        config.setDurationUnit(DurationUnit.MONTH);
        config.setCodeSilenceMonths(12);
        config.setEnabled(true);
        config.setRemark("old");
        return config;
    }

    private record SpecCase(int value, DurationUnit unit, String code) {
    }
}

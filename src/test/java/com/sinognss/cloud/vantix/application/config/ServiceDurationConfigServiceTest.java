package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void createsOpaqueSpecCodesAndAllowsSameDurationForDistinctDisplayNames() {
        List<SpecCase> cases = List.of(
                new SpecCase("1天", "CORS", 1),
                new SpecCase("1周", "CORS", 7),
                new SpecCase("45天", "SDK", 45),
                new SpecCase("45天-另一规格", "SDK", 45),
                new SpecCase("730天", "STANDARD", 730),
                new SpecCase("自定义周期", "STANDARD", 17),
                new SpecCase("按日规格", "CORS", 1));

        for (SpecCase specCase : cases) {
            ServiceDurationConfigView created = service.create(new ServiceDurationConfigCommand(
                    specCase.displayName(), specCase.serviceType(), specCase.durationDays(), 12, 30, true, null));
            assertEquals(specCase.displayName(), created.displayName());
            assertTrue(created.specCode().matches("SC[A-Z0-9]{12}"));
        }

        ArgumentCaptor<ServiceDurationConfig> config = ArgumentCaptor.forClass(ServiceDurationConfig.class);
        verify(mapper, times(7)).insert(config.capture());
        assertEquals(7, config.getAllValues().stream().map(ServiceDurationConfig::getSpecCode).distinct().count());
        assertEquals(List.of(1, 7, 45, 45, 730, 17, 1),
                config.getAllValues().stream().map(ServiceDurationConfig::getDurationDays).toList());
    }

    @Test
    void rejectsDuplicateDisplayName() {
        when(mapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new ServiceDurationConfigCommand(
                        "45天", "SDK", 45, 12, 30, true, null)));

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getVantixErrorCode());
        verify(mapper, never()).insert(any(ServiceDurationConfig.class));
    }

    @Test
    void mapsConcurrentDatabaseDurationOrSpecDuplicateToConfigInvalid() {
        when(mapper.insert(any(ServiceDurationConfig.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new ServiceDurationConfigCommand(
                        "月度规格", "CORS", 30, 12, 30, true, null)));

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getVantixErrorCode());
    }

    @Test
    void rejectsChangingDurationValueAfterCreation() {
        assertImmutableUpdateRejected(new ServiceDurationConfigCommand(
                "月度规格", "CORS", 90, 6, 30, false, "changed"));
    }

    @Test
    void rejectsChangingDurationUnitAfterCreation() {
        assertImmutableUpdateRejected(new ServiceDurationConfigCommand(
                "月度规格", "CORS", 365, 6, 30, false, "changed"));
    }

    @Test
    void rejectsChangingLegacyServiceTypeAfterCreation() {
        assertImmutableUpdateRejected(new ServiceDurationConfigCommand(
                "月度规格", "SDK", 30, 6, 30, false, "changed"));
    }

    @Test
    void allowsUpdatingSilenceEnabledAndRemarkWithoutChangingIdentity() {
        ServiceDurationConfig config = existingMonth();
        when(mapper.selectById(1L)).thenReturn(config);

        ServiceDurationConfigView updated = service.update(1L,
                new ServiceDurationConfigCommand("新月度规格", "CORS", 30, 6, 7, false, "changed"));

        assertEquals("M1", updated.specCode());
        assertEquals("新月度规格", updated.displayName());
        assertEquals("CORS", updated.serviceType());
        assertEquals(30, updated.durationDays());
        assertEquals(6, updated.codeSilenceDays());
        assertEquals(7, updated.accountSilenceDays());
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
        assertEquals("服务规格创建后不可修改，请停用旧规格并新建规格", exception.getMessage());
        verify(mapper, never()).updateById(any(ServiceDurationConfig.class));
    }

    private ServiceDurationConfig existingMonth() {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setId(1L);
        config.setSpecCode("M1");
        config.setDisplayName("月度规格");
        config.setServiceType("CORS");
        config.setDurationDays(30);
        config.setCodeSilenceDays(12);
        config.setAccountSilenceDays(30);
        config.setEnabled(true);
        config.setRemark("old");
        return config;
    }

    private record SpecCase(String displayName, String serviceType, int durationDays) {
    }
}

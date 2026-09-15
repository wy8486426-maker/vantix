package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.exchange.CompanyExchangeConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.CompanyExchangeConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeExchangeConfigServiceTest {
    private final CompanyExchangeConfigMapper mapper = mock(CompanyExchangeConfigMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private ServiceCodeExchangeConfigService service;

    @BeforeEach
    void setUp() {
        service = new ServiceCodeExchangeConfigService(mapper, userHolder, clock);
        when(userHolder.getUserScope()).thenReturn(new UserScope(7L, 100L));
        when(userHolder.getOperatorOrNull()).thenReturn(new OperatorIdentity(7L, "operator"));
    }

    @Test
    void unconfiguredReadReturnsStableCompanyScopedShape() {
        when(mapper.selectByCompanyId(100L)).thenReturn(null);

        ServiceCodeExchangeConfigView result = service.getCurrent();

        assertFalse(result.configured());
        assertEquals(100L, result.companyId());
        assertEquals(null, result.accountPrefix());
    }

    @Test
    void configIsImmutableAndSamePrefixRetryIsIdempotent() {
        CompanyExchangeConfig existing = config("AB12");
        when(mapper.insert(any(CompanyExchangeConfig.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectByCompanyIdForUpdate(100L)).thenReturn(existing, existing);

        ServiceCodeExchangeConfigView created = service.configure(" AB12 ");
        ServiceCodeExchangeConfigView retried = service.configure("AB12");

        assertEquals("AB12", created.accountPrefix());
        assertEquals(created, retried);
        assertThrows(BusinessException.class, () -> service.configure("CD34"));
    }

    @Test
    void firstConfigurationIsInsertedWithTrimmedPrefixAndOperatorSnapshot() {
        when(mapper.insert(any(CompanyExchangeConfig.class))).thenAnswer(invocation -> {
            CompanyExchangeConfig config = invocation.getArgument(0);
            config.setId(11L);
            return 1;
        });

        ServiceCodeExchangeConfigView result = service.configure(" AB12 ");

        assertTrue(result.configured());
        assertEquals(100L, result.companyId());
        assertEquals("AB12", result.accountPrefix());
        assertEquals(7L, result.configuredByUserId());
        verify(mapper, org.mockito.Mockito.never()).selectByCompanyId(100L);
        verify(mapper, org.mockito.Mockito.never()).selectByCompanyIdForUpdate(100L);
    }

    @Test
    void concurrentDifferentPrefixIsLockedAfterDuplicateInsert() {
        CompanyExchangeConfig existing = config("AB12");
        when(mapper.selectByCompanyIdForUpdate(100L)).thenReturn(existing);
        when(mapper.insert(any(CompanyExchangeConfig.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.configure("CD34"));

        assertEquals(ErrorCode.EXCHANGE_CONFIG_LOCKED, exception.getVantixErrorCode());
    }

    @Test
    void invalidPrefixAndGlobalScopeAreRejected() {
        BusinessException invalid = assertThrows(BusinessException.class, () -> service.configure("中文"));
        assertEquals(ErrorCode.EXCHANGE_PREFIX_INVALID, invalid.getVantixErrorCode());

        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        BusinessException global = assertThrows(BusinessException.class, service::getCurrent);
        assertEquals(ErrorCode.GLOBAL_SCOPE_REQUIRED, global.getVantixErrorCode());
    }

    private CompanyExchangeConfig config(String prefix) {
        CompanyExchangeConfig config = new CompanyExchangeConfig();
        config.setCompanyId(100L);
        config.setAccountPrefix(prefix);
        config.setOperatorUserId(7L);
        config.setOperatorUserName("operator");
        config.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return config;
    }
}

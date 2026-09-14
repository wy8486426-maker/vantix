package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeExchangeGroupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeGroupQueryServiceTest {
    private final ServiceCodeExchangeGroupMapper groupMapper = mock(ServiceCodeExchangeGroupMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ExchangeGroupQueryService service =
            new ExchangeGroupQueryService(groupMapper, companyMapper, userHolder, clock);

    @BeforeEach
    void setUp() {
        when(companyMapper.selectCount(any())).thenReturn(1L);
    }

    @Test
    void globalScopeMustSpecifyCompany() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.list(null));

        assertEquals(ErrorCode.INVALID_ARGUMENT, exception.getVantixErrorCode());
        verify(companyMapper, never()).selectCount(any());
        verify(groupMapper, never()).selectAvailableGroups(any(), any());
    }

    @Test
    void personalScopeCannotQueryAnotherCompany() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(19L, 7L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.list(8L));

        assertEquals(ErrorCode.SERVICE_CODE_NOT_OWNED, exception.getVantixErrorCode());
        verify(companyMapper, never()).selectCount(any());
        verify(groupMapper, never()).selectAvailableGroups(any(), any());
    }

    @Test
    void globalQueryMapsGroupAndUsesCurrentClock() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        ServiceCodeExchangeGroupMapper.ExchangeGroupRow row =
                new ServiceCodeExchangeGroupMapper.ExchangeGroupRow();
        row.setSpecCode("SPEC-1");
        row.setGenerationSource("B2B");
        row.setServiceType("STANDARD");
        row.setDurationValue(1);
        row.setDurationUnit("MONTH");
        row.setAvailableCount(4L);
        row.setEarliestExpireAt(LocalDateTime.of(2026, 3, 1, 12, 30));
        when(groupMapper.selectAvailableGroups(7L, LocalDateTime.of(2026, 1, 1, 0, 0)))
                .thenReturn(List.of(row));

        List<ServiceCodeExchangeGroupView> groups = service.list(7L);

        assertEquals(1, groups.size());
        assertEquals(new ServiceCodeExchangeGroupView("SPEC-1", "1个月", "STANDARD", "B2B",
                4L, LocalDateTime.of(2026, 3, 1, 12, 30)), groups.get(0));
        verify(groupMapper).selectAvailableGroups(7L, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void companyScopeDefaultsToItsCompany() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 7L));

        assertEquals(List.of(), service.list(null));

        verify(companyMapper).selectCount(any());
        verify(groupMapper).selectAvailableGroups(7L, LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}

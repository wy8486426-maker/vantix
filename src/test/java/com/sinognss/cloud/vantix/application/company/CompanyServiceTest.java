package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerRelationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyServiceTest {
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final DealerRelationLogMapper relationLogMapper = mock(DealerRelationLogMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private CompanyService service;

    @BeforeEach
    void setUp() {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(88L, "operator"));
        service = new CompanyService(companyMapper, relationLogMapper, userHolder, clock);
    }

    @Test
    void shouldSetFirstLevelCompanyAsChildAndWriteHistory() {
        DealerCompany root = company(1L, null);
        DealerCompany child = company(2L, null);
        when(companyMapper.selectForUpdate(any())).thenReturn(List.of(child, root));
        service.updateParent(new UpdateCompanyParentCommand(2L, 1L, "业务调整"));

        assertEquals(1L, child.getParentCompanyId());
        verify(companyMapper).updateById(child);
        verify(relationLogMapper).insert(any(com.sinognss.cloud.vantix.domain.company.DealerRelationLog.class));
    }

    @Test
    void shouldRejectSelfParent() {
        DealerCompany company = company(1L, null);
        when(companyMapper.selectOne(any())).thenReturn(company);

        assertThrows(BusinessException.class,
                () -> service.updateParent(new UpdateCompanyParentCommand(1L, 1L, null)));
    }

    @Test
    void shouldRejectSecondLevelParent() {
        DealerCompany target = company(3L, null);
        DealerCompany secondLevelParent = company(2L, 1L);
        when(companyMapper.selectForUpdate(any())).thenReturn(List.of(target, secondLevelParent));

        assertThrows(BusinessException.class,
                () -> service.updateParent(new UpdateCompanyParentCommand(3L, 2L, null)));
    }

    @Test
    void shouldRejectCycle() {
        DealerCompany target = company(1L, 2L);
        DealerCompany parent = company(2L, 1L);
        when(companyMapper.selectForUpdate(any())).thenReturn(List.of(target, parent));

        assertThrows(BusinessException.class,
                () -> service.updateParent(new UpdateCompanyParentCommand(1L, 2L, null)));
    }

    @Test
    void shouldRejectMovingCompanyThatAlreadyHasChildren() {
        DealerCompany target = company(1L, null);
        DealerCompany parent = company(3L, null);
        when(companyMapper.selectForUpdate(any())).thenReturn(List.of(target, parent));
        when(companyMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> service.updateParent(new UpdateCompanyParentCommand(1L, 3L, "迁移")));
        verify(companyMapper, org.mockito.Mockito.never()).updateById(any(DealerCompany.class));
        verify(relationLogMapper, org.mockito.Mockito.never()).insert(any(com.sinognss.cloud.vantix.domain.company.DealerRelationLog.class));
    }

    private DealerCompany company(Long id, Long parentId) {
        DealerCompany company = new DealerCompany();
        company.setCompanyId(id);
        company.setCompanyName("company-" + id);
        company.setParentCompanyId(parentId);
        return company;
    }
}

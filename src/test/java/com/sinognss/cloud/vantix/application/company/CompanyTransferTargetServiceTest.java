package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.application.config.SystemCompanyResolver;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyTransferTargetServiceTest {
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final SystemCompanyResolver systemCompanyResolver = mock(SystemCompanyResolver.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final CompanyTransferTargetService service = new CompanyTransferTargetService(
            companyMapper, systemCompanyResolver, userHolder);

    @Test
    void includesNullableManagerDisplayFieldsWithoutRejectingCompany() {
        DealerCompany system = company(1L, "system", null, null);
        DealerCompany current = company(100L, "current", null, null);
        DealerCompany parent = company(10L, "parent", 20L, "13800000000");
        DealerCompany child = company(101L, "child", null, null);
        current.setParentCompanyId(10L);
        child.setParentCompanyId(100L);
        when(userHolder.getCurrentCompanyId()).thenReturn(100L);
        when(systemCompanyResolver.requireId()).thenReturn(1L);
        when(companyMapper.selectList(any())).thenReturn(List.of(system, current, parent, child));

        List<TransferTargetCompanyView> targets = service.list();

        assertEquals(List.of(
                new TransferTargetCompanyView(1L, "system", null, null, "SYSTEM"),
                new TransferTargetCompanyView(10L, "parent", 20L, "13800000000", "PARENT"),
                new TransferTargetCompanyView(101L, "child", null, null, "CHILD")), targets);
    }

    @Test
    void systemCompanyGetsAllOtherCompaniesAsDealers() {
        DealerCompany system = company(1L, "system", null, null);
        DealerCompany dealer = company(100L, "dealer", null, null);
        when(userHolder.getCurrentCompanyId()).thenReturn(1L);
        when(systemCompanyResolver.requireId()).thenReturn(1L);
        when(companyMapper.selectList(any())).thenReturn(List.of(system, dealer));

        assertEquals(List.of(new TransferTargetCompanyView(100L, "dealer", null, null, "DEALER")),
                service.list());
    }

    private DealerCompany company(Long id, String name, Long managerId, String managerTel) {
        DealerCompany company = new DealerCompany();
        company.setCompanyId(id);
        company.setCompanyName(name);
        company.setManagerId(managerId);
        company.setManagerTel(managerTel);
        return company;
    }
}

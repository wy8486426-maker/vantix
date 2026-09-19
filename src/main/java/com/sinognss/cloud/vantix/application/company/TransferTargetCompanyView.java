package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.domain.company.DealerCompany;

public record TransferTargetCompanyView(Long companyId,
                                        String companyName,
                                        Long managerId,
                                        String managerTel,
                                        String relationshipType) {
    public static TransferTargetCompanyView from(DealerCompany company, String relationshipType) {
        return new TransferTargetCompanyView(company.getCompanyId(), company.getCompanyName(),
                company.getManagerId(), company.getManagerTel(), relationshipType);
    }
}

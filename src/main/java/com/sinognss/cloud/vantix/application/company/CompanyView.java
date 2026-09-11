package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.domain.company.CompanyStatus;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;

import java.time.LocalDateTime;

public record CompanyView(Long companyId, String companyName, Long parentCompanyId,
                          CompanyStatus companyStatus, LocalDateTime companySyncedAt,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static CompanyView from(DealerCompany company) {
        return new CompanyView(company.getCompanyId(), company.getCompanyName(), company.getParentCompanyId(),
                company.getCompanyStatus(), company.getCompanySyncedAt(), company.getCreatedAt(), company.getUpdatedAt());
    }
}

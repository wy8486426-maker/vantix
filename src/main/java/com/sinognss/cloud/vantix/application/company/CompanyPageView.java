package com.sinognss.cloud.vantix.application.company;

import java.time.LocalDateTime;

public record CompanyPageView(Long companyId, String companyName, Long managerId, String managerTel,
                              Long parentCompanyId,
                              String parentCompanyName, String level, String companyStatus,
                              LocalDateTime companySyncedAt, LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
    public CompanyPageView(Long companyId, String companyName, Long parentCompanyId,
                           String parentCompanyName, String level, String companyStatus,
                           LocalDateTime companySyncedAt, LocalDateTime createdAt,
                           LocalDateTime updatedAt) {
        this(companyId, companyName, null, null, parentCompanyId, parentCompanyName, level,
                companyStatus, companySyncedAt, createdAt, updatedAt);
    }

    public static CompanyPageView from(CompanyPageRow row) {
        return new CompanyPageView(row.getCompanyId(), row.getCompanyName(), row.getManagerId(), row.getManagerTel(),
                row.getParentCompanyId(),
                row.getParentCompanyName(), row.getLevel(), row.getCompanyStatus(), row.getCompanySyncedAt(),
                row.getCreatedAt(), row.getUpdatedAt());
    }
}

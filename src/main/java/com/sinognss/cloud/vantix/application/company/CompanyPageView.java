package com.sinognss.cloud.vantix.application.company;

import java.time.LocalDateTime;

public record CompanyPageView(Long companyId, String companyName, Long parentCompanyId,
                              String parentCompanyName, String level, String companyStatus,
                              LocalDateTime companySyncedAt, LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
    public static CompanyPageView from(CompanyPageRow row) {
        return new CompanyPageView(row.getCompanyId(), row.getCompanyName(), row.getParentCompanyId(),
                row.getParentCompanyName(), row.getLevel(), row.getCompanyStatus(), row.getCompanySyncedAt(),
                row.getCreatedAt(), row.getUpdatedAt());
    }
}

package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.domain.company.CompanyStatus;

public record CompanyPageQuery(long current, long size, String keyword, CompanyStatus status,
                               Long parentCompanyId, CompanyLevel level) {
}

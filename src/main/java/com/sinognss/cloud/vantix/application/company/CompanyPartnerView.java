package com.sinognss.cloud.vantix.application.company;

public record CompanyPartnerView(Long companyId, String companyName, String companyStatus,
                                 String relationshipType) {
    public static CompanyPartnerView from(CompanyPartnerRow row) {
        return new CompanyPartnerView(row.getCompanyId(), row.getCompanyName(), row.getCompanyStatus(),
                row.getRelationshipType());
    }
}

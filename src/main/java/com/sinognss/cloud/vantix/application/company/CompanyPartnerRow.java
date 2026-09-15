package com.sinognss.cloud.vantix.application.company;

public class CompanyPartnerRow {
    private Long companyId;
    private String companyName;
    private String companyStatus;
    private String relationshipType;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getCompanyStatus() { return companyStatus; }
    public void setCompanyStatus(String companyStatus) { this.companyStatus = companyStatus; }
    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
}

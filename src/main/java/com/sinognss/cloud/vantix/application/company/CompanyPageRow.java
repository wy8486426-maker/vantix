package com.sinognss.cloud.vantix.application.company;

import java.time.LocalDateTime;

public class CompanyPageRow {
    private Long companyId;
    private String companyName;
    private Long parentCompanyId;
    private String parentCompanyName;
    private String level;
    private String companyStatus;
    private LocalDateTime companySyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public Long getParentCompanyId() { return parentCompanyId; }
    public void setParentCompanyId(Long parentCompanyId) { this.parentCompanyId = parentCompanyId; }
    public String getParentCompanyName() { return parentCompanyName; }
    public void setParentCompanyName(String parentCompanyName) { this.parentCompanyName = parentCompanyName; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getCompanyStatus() { return companyStatus; }
    public void setCompanyStatus(String companyStatus) { this.companyStatus = companyStatus; }
    public LocalDateTime getCompanySyncedAt() { return companySyncedAt; }
    public void setCompanySyncedAt(LocalDateTime companySyncedAt) { this.companySyncedAt = companySyncedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

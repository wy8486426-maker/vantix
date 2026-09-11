package com.sinognss.cloud.vantix.domain.company;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("dealer_company")
public class DealerCompany {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private String companyName;
    private Long parentCompanyId;
    private CompanyStatus companyStatus;
    private LocalDateTime companySyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public Long getParentCompanyId() { return parentCompanyId; }
    public void setParentCompanyId(Long parentCompanyId) { this.parentCompanyId = parentCompanyId; }
    public CompanyStatus getCompanyStatus() { return companyStatus; }
    public void setCompanyStatus(CompanyStatus companyStatus) { this.companyStatus = companyStatus; }
    public LocalDateTime getCompanySyncedAt() { return companySyncedAt; }
    public void setCompanySyncedAt(LocalDateTime companySyncedAt) { this.companySyncedAt = companySyncedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

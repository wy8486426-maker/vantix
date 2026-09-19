package com.sinognss.cloud.vantix.application.company;

public record UserCenterCompany(Long companyId, String companyName, Long managerId, String managerTel) {
    public UserCenterCompany(Long companyId, String companyName) {
        this(companyId, companyName, null, null);
    }

    public boolean isValid() {
        return companyId != null
                && companyId > 0
                && companyName != null
                && !companyName.isBlank()
                && companyName.length() <= 128
                && (managerId == null || managerId > 0)
                && (managerTel == null || managerTel.length() <= 32);
    }
}

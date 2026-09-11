package com.sinognss.cloud.vantix.common.user;

public record UserScope(Long userId, Long companyId) {
    public boolean isGlobal() {
        return companyId == null;
    }

    public boolean canAccessCompany(Long targetCompanyId) {
        return isGlobal() || (targetCompanyId != null && targetCompanyId.equals(companyId));
    }
}

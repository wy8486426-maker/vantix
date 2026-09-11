package com.sinognss.cloud.vantix.common.user;

public record UserScope(Long userId, Long companyId) {
    public enum Type {
        GLOBAL,
        COMPANY,
        PERSONAL,
        UNSUPPORTED
    }

    public Type type() {
        if (userId == null && companyId == null) {
            return Type.GLOBAL;
        }
        if (userId == null) {
            return Type.COMPANY;
        }
        if (companyId == null) {
            return Type.UNSUPPORTED;
        }
        return Type.PERSONAL;
    }

    public boolean isGlobal() {
        return type() == Type.GLOBAL;
    }

    public boolean isCompanyScoped() {
        return type() == Type.COMPANY || type() == Type.PERSONAL;
    }

    public boolean isSupported() {
        return type() != Type.UNSUPPORTED;
    }

    public boolean canAccessCompany(Long targetCompanyId) {
        return isGlobal() ? targetCompanyId != null
                : isCompanyScoped() && targetCompanyId != null && targetCompanyId.equals(companyId);
    }
}

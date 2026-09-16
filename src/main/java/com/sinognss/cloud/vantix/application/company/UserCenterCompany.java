package com.sinognss.cloud.vantix.application.company;

public record UserCenterCompany(Long companyId, String companyName) {
    public boolean isValid() {
        return companyId != null
                && companyId > 0
                && companyName != null
                && !companyName.isBlank()
                && companyName.length() <= 128;
    }
}

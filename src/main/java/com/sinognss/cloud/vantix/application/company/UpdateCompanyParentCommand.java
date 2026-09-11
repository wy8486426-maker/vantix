package com.sinognss.cloud.vantix.application.company;

public record UpdateCompanyParentCommand(Long companyId, Long parentCompanyId, String reason) {
}

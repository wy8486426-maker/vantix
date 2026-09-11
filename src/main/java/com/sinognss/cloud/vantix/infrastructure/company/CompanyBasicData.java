package com.sinognss.cloud.vantix.infrastructure.company;

/** Minimal company payload required by Vantix when the company service integration is wired. */
public record CompanyBasicData(Long companyId, String companyName, String status) {
}

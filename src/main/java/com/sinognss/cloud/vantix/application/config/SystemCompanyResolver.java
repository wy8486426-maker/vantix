package com.sinognss.cloud.vantix.application.config;

import org.springframework.stereotype.Component;

@Component
public class SystemCompanyResolver {
    private final SystemConfigService systemConfigService;

    public SystemCompanyResolver(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public Long requireId() {
        return systemConfigService.getSystemCompanyId();
    }
}

package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.domain.exchange.CompanyExchangeConfig;

import java.time.LocalDateTime;

public record ServiceCodeExchangeConfigView(boolean configured, Long companyId, String accountPrefix,
                                            Long configuredByUserId, String configuredByUserName,
                                            LocalDateTime configuredAt) {
    public static ServiceCodeExchangeConfigView unconfigured(Long companyId) {
        return new ServiceCodeExchangeConfigView(false, companyId, null, null, null, null);
    }

    public static ServiceCodeExchangeConfigView from(CompanyExchangeConfig config) {
        return new ServiceCodeExchangeConfigView(true, config.getCompanyId(), config.getAccountPrefix(),
                config.getOperatorUserId(), config.getOperatorUserName(), config.getCreatedAt());
    }
}

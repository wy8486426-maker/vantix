package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.domain.config.AccountConfig;

import java.time.LocalDateTime;

public record AccountConfigView(Integer accountSilenceMonths, LocalDateTime updatedAt) {
    public static AccountConfigView from(AccountConfig config) {
        return new AccountConfigView(config.getAccountSilenceMonths(), config.getUpdatedAt());
    }
}

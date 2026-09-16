package com.sinognss.cloud.vantix.application.account;

public record ServiceAccountStatistics(long total, long waiting, long active, long expired, long disabled) {
}

package com.sinognss.cloud.vantix.application.account;

public record ServiceAccountStatisticsQuery(String keyword, String specCode, Integer durationDays,
                                            Long ownerCompanyId, Long assignedUserId) {
}

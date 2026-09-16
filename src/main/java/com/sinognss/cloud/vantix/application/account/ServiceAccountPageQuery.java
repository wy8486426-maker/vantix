package com.sinognss.cloud.vantix.application.account;

public record ServiceAccountPageQuery(long current, long size, String keyword,
                                      String status, String specCode, Integer durationDays,
                                      Long ownerCompanyId, Long assignedUserId) {
}

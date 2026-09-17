package com.sinognss.cloud.vantix.application.account;

import com.sinognss.cloud.vantix.domain.account.AccountSource;

public record ServiceAccountPageQuery(long current, long size, String keyword,
                                      String status, String specCode, Integer durationDays,
                                      Long ownerCompanyId, Long assignedUserId,
                                      AccountSource accountSource) {
    public ServiceAccountPageQuery(long current, long size, String keyword, String status,
                                   String specCode, Integer durationDays,
                                   Long ownerCompanyId, Long assignedUserId) {
        this(current, size, keyword, status, specCode, durationDays, ownerCompanyId, assignedUserId, null);
    }
}

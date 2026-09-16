package com.sinognss.cloud.vantix.application.renewal;

import java.time.LocalDateTime;

public record AccountRenewalLogPageQuery(long current, long size, String keyword, String status,
                                         Long ownerCompanyId, LocalDateTime createdFrom,
                                         LocalDateTime createdTo) {
}

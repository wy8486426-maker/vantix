package com.sinognss.cloud.vantix.application.servicecode;

public record ServiceCodeStatisticsQuery(String keyword,
                                         String specCode,
                                         Integer durationDays,
                                         String sourceOrderNo,
                                         Long ownerCompanyId) {
}

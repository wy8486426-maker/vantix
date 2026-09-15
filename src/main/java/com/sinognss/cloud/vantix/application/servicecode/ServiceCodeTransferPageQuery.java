package com.sinognss.cloud.vantix.application.servicecode;

import com.sinognss.cloud.vantix.domain.servicecode.TransferType;

import java.time.LocalDateTime;

public record ServiceCodeTransferPageQuery(long current, long size, String keyword,
                                           TransferType transferType, Long fromCompanyId,
                                           Long toCompanyId, LocalDateTime createdFrom,
                                           LocalDateTime createdTo, String specCode,
                                           Integer durationDays) {
}

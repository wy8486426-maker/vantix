package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;

import java.time.LocalDateTime;

public record ExchangeLogPageQuery(long current, long size, String keyword, ExchangeStatus status,
                                   String specCode, Long ownerCompanyId,
                                   LocalDateTime createdFrom, LocalDateTime createdTo) {
}

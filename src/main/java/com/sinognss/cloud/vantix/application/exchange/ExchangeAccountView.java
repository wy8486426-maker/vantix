package com.sinognss.cloud.vantix.application.exchange;

import java.time.LocalDateTime;

public record ExchangeAccountView(String accountId, String account, String accountStatus,
                                  String activationStatus, LocalDateTime activatedAt,
                                  LocalDateTime expireAt) {
}

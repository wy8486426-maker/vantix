package com.sinognss.cloud.vantix.application.exchange;

import java.time.LocalDateTime;
import java.util.List;

public record ServiceCodeExchangeView(String requestId, String exchangeBatchNo, Long companyId,
                                      String specCode, String generationSource, Integer quantity,
                                      String status, String accountPrefix, LocalDateTime createdAt,
                                      LocalDateTime completedAt, List<ExchangeAccountView> accounts) {
    public ServiceCodeExchangeView {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }
}

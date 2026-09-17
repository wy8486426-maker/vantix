package com.sinognss.cloud.vantix.application.history;

import com.sinognss.cloud.vantix.application.account.AccountIdentityView;

import java.time.LocalDateTime;
import java.util.List;

public record HistoryAccountImportView(String requestId, String importBatchNo, Long companyId,
                                       String specCode, Integer durationDays, Integer quantity,
                                       String status, LocalDateTime createdAt, LocalDateTime completedAt,
                                       List<AccountIdentityView> accounts) {
}

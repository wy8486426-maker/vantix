package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.application.account.AccountIdentityView;

import java.time.LocalDateTime;
import java.util.List;

public record TestAccountIssueView(String requestId, String issueBatchNo, Long companyId,
                                   String specCode, Integer durationDays, Integer quantity,
                                   String status, LocalDateTime createdAt, LocalDateTime completedAt,
                                   List<AccountIdentityView> accounts) {
}

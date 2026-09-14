package com.sinognss.cloud.vantix.application.renewal;

public record CreateAccountRenewalCommand(String requestId, Long serviceAccountId, Long serviceCodeId) {
}

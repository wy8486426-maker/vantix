package com.sinognss.cloud.vantix.application.testaccount;

public record TestAccountIssueCommand(String requestId, Long companyId, Integer quantity,
                                      String specCode, String accountPrefix) {
}

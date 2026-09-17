package com.sinognss.cloud.vantix.application.history;

import java.util.List;

public record HistoryAccountImportCommand(String requestId, Long companyId, String specCode,
                                          List<HistoryAccountIdentity> accounts) {
}

package com.sinognss.cloud.vantix.application.exchange;

import java.util.List;

public record ServiceCodeExchangeByCodesCommand(String requestId, Long companyId,
                                                List<Long> serviceCodeIds) {
}

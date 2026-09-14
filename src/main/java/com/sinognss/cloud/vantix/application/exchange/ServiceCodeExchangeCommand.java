package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;

public record ServiceCodeExchangeCommand(String requestId, Long companyId, String specCode,
                                         GenerationSource generationSource, Integer quantity,
                                         String accountPrefix) {
}

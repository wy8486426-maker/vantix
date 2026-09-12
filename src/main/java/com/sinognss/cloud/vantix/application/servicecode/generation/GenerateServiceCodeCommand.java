package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;

import java.time.LocalDateTime;

public record GenerateServiceCodeCommand(GenerationSource generationSource, String requestId,
                                         String orderNo, LocalDateTime orderTime,
                                         Long companyId, String specCode, Integer quantity,
                                         String remark) {
}
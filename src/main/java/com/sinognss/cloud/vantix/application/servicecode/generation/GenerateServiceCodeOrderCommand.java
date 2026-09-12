package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;

import java.time.LocalDateTime;
import java.util.List;

public record GenerateServiceCodeOrderCommand(GenerationSource generationSource, String requestId,
                                               String orderNo, LocalDateTime orderTime,
                                               Long companyId,
                                               List<GenerateServiceCodeItemCommand> items) {
}

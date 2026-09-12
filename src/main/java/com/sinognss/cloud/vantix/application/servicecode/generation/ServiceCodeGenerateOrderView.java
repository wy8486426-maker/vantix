package com.sinognss.cloud.vantix.application.servicecode.generation;

import java.time.LocalDateTime;
import java.util.List;

public record ServiceCodeGenerateOrderView(String requestId, String generationSource,
                                            String orderNo, Long companyId,
                                            LocalDateTime orderTime, String status,
                                            Integer itemCount, Integer totalQuantity,
                                            List<ServiceCodeGenerateOrderItemView> items,
                                            boolean idempotent, LocalDateTime createdAt) {
}

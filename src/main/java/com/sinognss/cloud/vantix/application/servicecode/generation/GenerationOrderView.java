package com.sinognss.cloud.vantix.application.servicecode.generation;

import java.time.LocalDateTime;

public record GenerationOrderView(Long id, String requestId, String generationSource,
                                  String sourceOrderNo, LocalDateTime sourceOrderTime,
                                  Long ownerCompanyId, String ownerCompanyName, Integer itemCount,
                                  Integer totalQuantity, String status, Long operatorUserId,
                                  String operatorUserName, LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {
    public static GenerationOrderView from(GenerationOrderQueryRow row) {
        return new GenerationOrderView(row.getId(), row.getRequestId(), row.getGenerationSource(),
                row.getSourceOrderNo(), row.getSourceOrderTime(), row.getOwnerCompanyId(),
                row.getOwnerCompanyName(), row.getItemCount(), row.getTotalQuantity(), row.getStatus(),
                row.getOperatorUserId(), row.getOperatorUserName(), row.getCreatedAt(), row.getUpdatedAt());
    }
}

package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.DurationDisplayFormatter;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;

import java.time.LocalDateTime;

public record ServiceCodeBatchView(String batchNo, String generationSource, String sourceOrderNo,
                                  Long companyId, String specCode, String displayName,
                                  Integer quantity, Integer generatedCount, String status,
                                  LocalDateTime createdAt) {
    public static ServiceCodeBatchView from(ServiceCodeGenerateBatch batch) {
        return new ServiceCodeBatchView(batch.getBatchNo(), batch.getGenerationSource().name(),
                batch.getSourceOrderNo(), batch.getOwnerCompanyId(), batch.getSpecCode(),
                DurationDisplayFormatter.format(batch.getDurationValue(),
                        DurationDisplayFormatter.parseUnit(batch.getDurationUnit())),
                batch.getQuantity(), batch.getGeneratedCount(), batch.getStatus(), batch.getCreatedAt());
    }
}
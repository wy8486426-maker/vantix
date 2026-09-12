package com.sinognss.cloud.vantix.application.servicecode.generation;

public record ServiceCodeGenerateOrderItemView(String specCode, String displayName,
                                                Integer quantity, Integer generatedCount,
                                                String batchNo, String status) {
    public static ServiceCodeGenerateOrderItemView from(ServiceCodeBatchView batch) {
        return new ServiceCodeGenerateOrderItemView(batch.specCode(), batch.displayName(),
                batch.quantity(), batch.generatedCount(), batch.batchNo(), batch.status());
    }
}

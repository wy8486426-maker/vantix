package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeBatchView;

import java.util.List;

public record OfflineImportResult(int batchCount, int generatedCount,
                                  List<ServiceCodeBatchView> batches) {
}
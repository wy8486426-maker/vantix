package com.sinognss.cloud.vantix.application.servicecode.generation;

import java.util.List;

public record GenerateServiceCodeResult(ServiceCodeBatchView batch, boolean idempotent,
                                        List<String> serviceCodes) {
}
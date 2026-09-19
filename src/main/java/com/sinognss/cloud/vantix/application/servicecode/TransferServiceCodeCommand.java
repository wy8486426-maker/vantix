package com.sinognss.cloud.vantix.application.servicecode;

import java.util.List;

public record TransferServiceCodeCommand(Long toCompanyId,
                                         List<Long> serviceCodeIds,
                                         String reason) {
    @Deprecated
    public TransferServiceCodeCommand(Long ignoredFromCompanyId, Long toCompanyId,
                                      List<Long> serviceCodeIds, String reason) {
        this(toCompanyId, serviceCodeIds, reason);
    }
}

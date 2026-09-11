package com.sinognss.cloud.vantix.application.servicecode;

import java.util.List;

public record TransferServiceCodeCommand(Long fromCompanyId, Long toCompanyId,
                                         List<Long> serviceCodeIds, String reason) {
}

package com.sinognss.cloud.vantix.application.servicecode;

public record CreateServiceCodeCommand(String code, Long sourceOrderId, String sourceOrderNo,
                                       Long ownerCompanyId, Long durationConfigId) {
}

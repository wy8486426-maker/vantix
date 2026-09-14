package com.sinognss.cloud.vantix.application.renewal;

public record AccountRenewalReservation(Long renewalId, Long operationId, String requestId, boolean created) {
}

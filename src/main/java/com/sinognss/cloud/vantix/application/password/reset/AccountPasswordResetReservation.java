package com.sinognss.cloud.vantix.application.password.reset;

public record AccountPasswordResetReservation(String requestId, Long serviceAccountId, String status) {
}

package com.sinognss.cloud.vantix.application.password.reset;

public record AccountPasswordResetCommand(String requestId, Long serviceAccountId) {
}

package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;

/** Result intentionally contains no password or other credential material. */
public record CorsPasswordResetResult(CorsOutcome outcome, String requestId, String accountId,
                                     String errorCode) {
    @Override
    public String toString() {
        return "CorsPasswordResetResult[outcome=" + outcome + ", metadata=REDACTED]";
    }
}

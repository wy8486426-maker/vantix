package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;

public final class CorsPasswordRevealResult {
    private final CorsOutcome outcome;
    private final String requestId;
    private final String accountId;
    private final PasswordRevealSecret secret;
    private final String errorCode;

    public CorsPasswordRevealResult(CorsOutcome outcome, String requestId, String accountId,
                                    PasswordRevealSecret secret, String errorCode) {
        this.outcome = outcome;
        this.requestId = requestId;
        this.accountId = accountId;
        this.secret = secret;
        this.errorCode = errorCode;
    }

    public CorsOutcome getOutcome() { return outcome; }
    public String getRequestId() { return requestId; }
    public String getAccountId() { return accountId; }
    public PasswordRevealSecret getSecret() { return secret; }
    public String getErrorCode() { return errorCode; }

    @Override
    public String toString() {
        return "CorsPasswordRevealResult[REDACTED]";
    }
}

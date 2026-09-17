package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;

import java.util.Objects;

/** Common CORS envelope outcome for resetPass and customPass. */
public final class CorsPasswordResult {
    private final CorsOutcome outcome;
    private final String code;
    private final String message;

    public CorsPasswordResult(CorsOutcome outcome, String code, String message) {
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.code = code;
        this.message = message;
    }

    public static CorsPasswordResult success() {
        return new CorsPasswordResult(CorsOutcome.SUCCESS, "0", null);
    }

    public static CorsPasswordResult businessFailure(String code, String message) {
        return new CorsPasswordResult(CorsOutcome.DEFINITIVE_REJECT, code, message);
    }

    public static CorsPasswordResult unknown(String code, String message) {
        return new CorsPasswordResult(CorsOutcome.UNKNOWN, code, message);
    }

    public CorsOutcome outcome() { return outcome; }
    public String code() { return code; }
    public String message() { return message; }
    public String errorCode() { return code; }
    public String errorMessage() { return message; }

    @Override
    public String toString() {
        return "CorsPasswordResult[outcome=" + outcome + ", code=" + code + "]";
    }
}

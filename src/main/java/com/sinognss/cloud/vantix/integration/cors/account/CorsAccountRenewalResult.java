package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;

import java.util.Objects;

/**
 * Result of the CORS batch renewal call. A SUCCESS includes the stable
 * correlation identity; its data and account snapshot may be unavailable while
 * the CORS Redis result window is still open. A DEFINITIVE_REJECT explicitly
 * means CORS guarantees the renewal had no side effect, so the caller may
 * safely release the reserved service code.
 */
public record CorsAccountRenewalResult(
        CorsOutcome outcome,
        String requestId,
        CorsAccountSnapshot account,
        CorsRenewalData data,
        String errorCode,
        String errorMessage) {

    public CorsAccountRenewalResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == CorsOutcome.SUCCESS) {
            requireText(requestId, "SUCCESS requires requestId");
        } else if (account != null || data != null) {
            throw new IllegalArgumentException("Only SUCCESS can contain renewal data");
        }
        if (requestId != null && (requestId.isBlank() || requestId.length() > 128
                || requestId.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("requestId must be non-blank, at most 128 characters, and contain no controls");
        }
    }

    public static CorsAccountRenewalResult success(String requestId, CorsAccountSnapshot account) {
        Objects.requireNonNull(account, "account must not be null");
        return new CorsAccountRenewalResult(CorsOutcome.SUCCESS, requestId, account,
                new CorsRenewalData("corsRenewal", java.util.List.of(account.account())), null, null);
    }

    public static CorsAccountRenewalResult successWithData(String requestId, CorsRenewalData data) {
        return new CorsAccountRenewalResult(CorsOutcome.SUCCESS, requestId, null, data, null, null);
    }

    public CorsAccountRenewalResult withAccount(CorsAccountSnapshot snapshot) {
        return new CorsAccountRenewalResult(outcome, requestId, snapshot, data, errorCode, errorMessage);
    }

    public static CorsAccountRenewalResult notFound(String requestId, String errorCode, String errorMessage) {
        return result(CorsOutcome.NOT_FOUND, requestId, errorCode, errorMessage);
    }

    /** The CORS contract guarantees sideEffect=false for this outcome. */
    public static CorsAccountRenewalResult definitiveReject(
            String requestId, String errorCode, String errorMessage) {
        return result(CorsOutcome.DEFINITIVE_REJECT, requestId, errorCode, errorMessage);
    }

    public static CorsAccountRenewalResult unknown(String requestId, String errorCode, String errorMessage) {
        return result(CorsOutcome.UNKNOWN, requestId, errorCode, errorMessage);
    }

    public static CorsAccountRenewalResult idempotencyConflict(
            String requestId, String errorCode, String errorMessage) {
        return result(CorsOutcome.IDEMPOTENCY_CONFLICT, requestId, errorCode, errorMessage);
    }

    private static CorsAccountRenewalResult result(
            CorsOutcome outcome, String requestId, String errorCode, String errorMessage) {
        return new CorsAccountRenewalResult(outcome, requestId, null, null, errorCode, errorMessage);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}

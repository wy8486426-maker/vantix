package com.sinognss.cloud.vantix.application.password.reset;

final class AccountPasswordResetFailure {
    static final String QUERY_UNKNOWN = "RESET_QUERY_UNKNOWN";
    static final String QUERY_MALFORMED = "RESET_QUERY_MALFORMED";
    static final String QUERY_IDENTITY_MISMATCH = "RESET_QUERY_IDENTITY_MISMATCH";
    static final String QUERY_IDEMPOTENCY_CONFLICT = "RESET_QUERY_IDEMPOTENCY_CONFLICT";
    static final String QUERY_DEFINITIVE_REJECT = "RESET_QUERY_REJECTED";
    static final String PREFLIGHT_UNKNOWN = "RESET_PREFLIGHT_UNKNOWN";
    static final String PREFLIGHT_MALFORMED = "RESET_PREFLIGHT_MALFORMED";
    static final String ACCOUNT_NOT_FOUND = "CORS_ACCOUNT_NOT_FOUND";
    static final String PREFLIGHT_IDENTITY_MISMATCH = "RESET_PREFLIGHT_IDENTITY_MISMATCH";
    static final String POST_UNKNOWN = "RESET_POST_UNKNOWN";
    static final String POST_MALFORMED = "RESET_POST_MALFORMED";
    static final String POST_IDENTITY_MISMATCH = "RESET_POST_IDENTITY_MISMATCH";
    static final String POST_IDEMPOTENCY_CONFLICT = "RESET_POST_IDEMPOTENCY_CONFLICT";
    static final String POST_DEFINITIVE_REJECT = "RESET_POST_REJECTED";
    static final String LOCAL_FINALIZE_FAILED = "LOCAL_FINALIZE_FAILED";
    static final String CLAIM_TIMEOUT = "RESET_CLAIM_TIMEOUT";
    static final String CLAIM_TIMEOUT_EXHAUSTED = "RESET_CLAIM_TIMEOUT_EXHAUSTED";
    static final String RETRY_EXHAUSTED = "RESET_RETRY_EXHAUSTED";

    private AccountPasswordResetFailure() {
    }

    static String message(String code) {
        return switch (code == null ? "" : code) {
            case QUERY_UNKNOWN -> "远程重置查询结果未知，等待后续安全查询";
            case QUERY_MALFORMED -> "远程重置查询结果无效，等待后续安全查询";
            case QUERY_IDENTITY_MISMATCH -> "远程重置查询身份不匹配，需要人工处理";
            case QUERY_IDEMPOTENCY_CONFLICT -> "远程重置请求标识冲突，需要人工处理";
            case QUERY_DEFINITIVE_REJECT -> "远程重置请求已明确拒绝且未产生副作用";
            case PREFLIGHT_UNKNOWN -> "远程账号预检结果未知，等待后续安全查询";
            case PREFLIGHT_MALFORMED -> "远程账号预检结果无效，等待后续安全查询";
            case ACCOUNT_NOT_FOUND -> "远程账号不存在，未执行密码重置";
            case PREFLIGHT_IDENTITY_MISMATCH -> "远程账号身份不匹配，需要人工处理";
            case POST_UNKNOWN -> "远程重置结果未知，等待后续安全查询";
            case POST_MALFORMED -> "远程重置结果无效，等待后续安全查询";
            case POST_IDENTITY_MISMATCH -> "远程重置响应身份不匹配，需要人工处理";
            case POST_IDEMPOTENCY_CONFLICT -> "远程重置请求标识冲突，需要人工处理";
            case POST_DEFINITIVE_REJECT -> "远程重置请求已明确拒绝且未产生副作用";
            case LOCAL_FINALIZE_FAILED -> "远程重置成功但本地记录失败，需要人工处理";
            case CLAIM_TIMEOUT -> "重置任务执行超时，下一轮将先查询原请求标识";
            case CLAIM_TIMEOUT_EXHAUSTED, RETRY_EXHAUSTED -> "重置任务超过自动重试上限，需要人工处理";
            default -> "密码重置结果未知，等待后续安全查询";
        };
    }

    static boolean isRetryable(String code) {
        return QUERY_UNKNOWN.equals(code) || QUERY_MALFORMED.equals(code)
                || PREFLIGHT_UNKNOWN.equals(code) || PREFLIGHT_MALFORMED.equals(code)
                || POST_UNKNOWN.equals(code) || POST_MALFORMED.equals(code);
    }

    static boolean isManualReview(String code) {
        return QUERY_IDENTITY_MISMATCH.equals(code) || QUERY_IDEMPOTENCY_CONFLICT.equals(code)
                || PREFLIGHT_IDENTITY_MISMATCH.equals(code) || POST_IDENTITY_MISMATCH.equals(code)
                || POST_IDEMPOTENCY_CONFLICT.equals(code) || LOCAL_FINALIZE_FAILED.equals(code);
    }

    static boolean isDefinitiveFailure(String code) {
        return ACCOUNT_NOT_FOUND.equals(code) || QUERY_DEFINITIVE_REJECT.equals(code)
                || POST_DEFINITIVE_REJECT.equals(code);
    }
}

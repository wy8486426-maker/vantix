package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.domain.cors.CorsOperation;

public final class AccountRenewalConstants {
    public static final String OPERATION_TYPE = "RENEW_ACCOUNT";
    public static final String BIZ_TYPE = "ACCOUNT_RENEWAL";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String PENDING = "PENDING";
    public static final String CLAIMED = "CLAIMED";
    public static final String RETRY_WAIT = "RETRY_WAIT";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String OPERATION_FAILED = "FAILED";

    private AccountRenewalConstants() { }

    public static boolean isOperation(CorsOperation operation, Long renewalId, Long accountId) {
        return operation != null && OPERATION_TYPE.equals(operation.getOperationType())
                && BIZ_TYPE.equals(operation.getBizType()) && operation.getBizId() != null
                && operation.getBizId().equals(renewalId) && operation.getServiceAccountId() != null
                && operation.getServiceAccountId().equals(accountId);
    }
}

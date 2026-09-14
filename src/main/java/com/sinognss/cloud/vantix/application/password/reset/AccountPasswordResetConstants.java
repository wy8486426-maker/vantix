package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;

public final class AccountPasswordResetConstants {
    public static final String OPERATION_TYPE = "RESET_ACCOUNT_PASSWORD";
    public static final String BIZ_TYPE = "ACCOUNT_PASSWORD_RESET";
    public static final String PENDING = "PENDING";
    public static final String CLAIMED = "CLAIMED";
    public static final String RETRY_WAIT = "RETRY_WAIT";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";

    private AccountPasswordResetConstants() {
    }

    public static boolean isOperation(CorsOperation operation, Long actionId, Long serviceAccountId) {
        return operation != null
                && OPERATION_TYPE.equals(operation.getOperationType())
                && BIZ_TYPE.equals(operation.getBizType())
                && operation.getBizId() != null && operation.getBizId().equals(actionId)
                && operation.getServiceAccountId() != null
                && operation.getServiceAccountId().equals(serviceAccountId);
    }

    public static boolean isAction(AccountPasswordAction action, Long serviceAccountId, String requestId) {
        return action != null
                && AccountPasswordActionConstants.RESET.equals(action.getActionType())
                && AccountPasswordActionConstants.PROCESSING.equals(action.getStatus())
                && action.getId() != null
                && action.getServiceAccountId() != null
                && action.getServiceAccountId().equals(serviceAccountId)
                && requestId != null && requestId.equals(action.getRequestId());
    }
}

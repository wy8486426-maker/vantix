package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;

import java.time.LocalDateTime;

final class AccountForceActivationConstants {
    static final String OPERATION_TYPE = "FORCE_ACTIVATE_ACCOUNT";
    static final String BIZ_TYPE = "ACCOUNT_FORCE_ACTIVATION";
    static final String PENDING = "PENDING";
    static final String RETRY_WAIT = "RETRY_WAIT";
    static final String CLAIMED = "CLAIMED";
    static final String SUCCEEDED = "SUCCEEDED";
    static final String MANUAL_REVIEW = "MANUAL_REVIEW";
    static final String WAITING_ACTIVATION = "WAITING_ACTIVATION";
    static final String ACTIVE = "ACTIVE";

    private AccountForceActivationConstants() {
    }

    static boolean isOperationForAccount(CorsOperation operation, Long serviceAccountId) {
        return operation != null
                && OPERATION_TYPE.equals(operation.getOperationType())
                && BIZ_TYPE.equals(operation.getBizType())
                && serviceAccountId != null
                && serviceAccountId.equals(operation.getBizId())
                && serviceAccountId.equals(operation.getServiceAccountId())
                && operation.getRequestId() != null
                && !operation.getRequestId().isBlank()
                && operation.getRequestId().length() <= 128;
    }

    static boolean isEligibleAccount(ServiceAccount account, LocalDateTime now) {
        return account != null
                && account.getId() != null
                && account.getVersion() != null
                && account.getCorsAccountId() != null
                && !account.getCorsAccountId().isBlank()
                && WAITING_ACTIVATION.equals(account.getCorsActivationStatus())
                && account.getForceActivateAt() != null
                && !account.getForceActivateAt().isAfter(now);
    }

    static boolean hasIdentity(ServiceAccount account, CorsOperation operation) {
        return account != null
                && operation != null
                && account.getId() != null
                && account.getId().equals(operation.getServiceAccountId())
                && account.getId().equals(operation.getBizId())
                && account.getCorsAccountId() != null
                && !account.getCorsAccountId().isBlank()
                && account.getAccount() != null
                && !account.getAccount().isBlank()
                && isOperationForAccount(operation, account.getId());
    }
}

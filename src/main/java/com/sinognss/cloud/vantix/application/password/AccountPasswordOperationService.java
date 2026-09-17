package com.sinognss.cloud.vantix.application.password;

import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetReservation;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountId;
import com.sinognss.cloud.vantix.integration.cors.account.CorsCustomPasswordRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsResetPasswordRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Executes resetPass/customPass synchronously without entering the CORS retry pipeline. */
public class AccountPasswordOperationService {
    private static final int MAX_PASSWORD_LENGTH = 4096;

    private final AccountPasswordAuditService auditService;
    private final CorsPasswordGateway gateway;

    public AccountPasswordOperationService(AccountPasswordAuditService auditService,
                                           CorsPasswordGateway gateway) {
        this.auditService = auditService;
        this.gateway = gateway;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reset(Long serviceAccountId) {
        execute(AccountPasswordActionConstants.RESET, serviceAccountId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AccountPasswordResetReservation reset(Long serviceAccountId, String requestId) {
        return execute(AccountPasswordActionConstants.RESET, serviceAccountId, null, requestId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void custom(Long serviceAccountId, String password) {
        if (password == null || password.isBlank() || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "password 非法");
        }
        execute(AccountPasswordActionConstants.CUSTOM, serviceAccountId, password, null);
    }

    private void execute(String actionType, Long serviceAccountId, String password) {
        execute(actionType, serviceAccountId, password, null);
    }

    private AccountPasswordResetReservation execute(String actionType, Long serviceAccountId,
                                                    String password, String requestId) {
        AccountPasswordAction action = requestId == null
                ? auditService.reserve(actionType, serviceAccountId)
                : auditService.reserve(actionType, serviceAccountId, requestId);
        if (!AccountPasswordActionConstants.PROCESSING.equals(action.getStatus())) {
            if (AccountPasswordActionConstants.SUCCEEDED.equals(action.getStatus())) {
                return resetReservation(action);
            }
            throw new BusinessException(ErrorCode.CORS_PASSWORD_RESULT_UNKNOWN,
                    "CORS 密码操作结果未知，请人工确认");
        }
        long corsAccountId = parseCorsId(action);
        CorsPasswordResult result;
        try {
            result = AccountPasswordActionConstants.RESET.equals(actionType)
                    ? gateway.resetPassword(new CorsResetPasswordRequest(corsAccountId))
                    : gateway.customPassword(new CorsCustomPasswordRequest(corsAccountId, password));
        } catch (RuntimeException exception) {
            markUnknown(action);
            throw unknown();
        }

        if (result == null || result.outcome() == null) {
            markUnknown(action);
            throw unknown();
        }
        if (result.outcome() == com.sinognss.cloud.vantix.integration.cors.CorsOutcome.SUCCESS) {
            try {
                auditService.complete(action);
            } catch (RuntimeException exception) {
                markUnknown(action);
                throw unknown();
            }
            return resetReservation(action);
        }
        if (result.outcome() == com.sinognss.cloud.vantix.integration.cors.CorsOutcome.DEFINITIVE_REJECT) {
            String code = safeCode(result.code(), "CORS_PASSWORD_REJECTED");
            try {
                auditService.fail(action, code, genericFailureMessage(actionType));
            } catch (RuntimeException exception) {
                markUnknown(action);
                throw unknown();
            }
            throw new BusinessException(failureCode(actionType), genericFailureMessage(actionType));
        }

        markUnknown(action);
        throw unknown();
    }

    private static AccountPasswordResetReservation resetReservation(AccountPasswordAction action) {
        return new AccountPasswordResetReservation(action.getRequestId(), action.getServiceAccountId(),
                AccountPasswordActionConstants.SUCCEEDED);
    }

    private void markUnknown(AccountPasswordAction action) {
        try {
            auditService.manualReview(action, "CORS_PASSWORD_RESULT_UNKNOWN",
                    "CORS 密码操作结果未知，请人工确认");
        } catch (RuntimeException ignored) {
            // Preserve the safe no-replay response even if the audit transition also fails.
        }
    }

    private static long parseCorsId(AccountPasswordAction action) {
        try {
            return CorsAccountId.parse(action.getCorsAccountId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_FAILED,
                    "服务账号缺少有效的 CORS 账号标识");
        }
    }

    private static ErrorCode failureCode(String actionType) {
        return AccountPasswordActionConstants.RESET.equals(actionType)
                ? ErrorCode.PASSWORD_RESET_FAILED : ErrorCode.PASSWORD_CUSTOM_FAILED;
    }

    private static String genericFailureMessage(String actionType) {
        return AccountPasswordActionConstants.RESET.equals(actionType)
                ? "CORS 密码重置失败" : "CORS 自定义密码操作失败";
    }

    private static String safeCode(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static BusinessException unknown() {
        return new BusinessException(ErrorCode.CORS_PASSWORD_RESULT_UNKNOWN,
                "CORS 密码操作结果未知，请人工确认");
    }
}

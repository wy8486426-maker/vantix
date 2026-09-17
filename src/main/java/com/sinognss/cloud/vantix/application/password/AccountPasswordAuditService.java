package com.sinognss.cloud.vantix.application.password;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Persists password-operation metadata only; it never receives or stores a password. */
public class AccountPasswordAuditService {
    private final AccountPasswordActionMapper actionMapper;
    private final ServiceAccountMapper accountMapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;

    public AccountPasswordAuditService(AccountPasswordActionMapper actionMapper,
                                       ServiceAccountMapper accountMapper,
                                       UserHolderBridge userHolder,
                                       Clock clock) {
        this.actionMapper = actionMapper;
        this.accountMapper = accountMapper;
        this.userHolder = userHolder;
        this.clock = clock;
    }

    @Transactional
    public AccountPasswordAction reserve(String actionType, Long serviceAccountId) {
        return reserve(actionType, serviceAccountId, null);
    }

    @Transactional
    public AccountPasswordAction reserve(String actionType, Long serviceAccountId, String requestedRequestId) {
        if (!AccountPasswordActionConstants.RESET.equals(actionType)
                && !AccountPasswordActionConstants.CUSTOM.equals(actionType)) {
            throw new IllegalArgumentException("unsupported password action type");
        }
        if (serviceAccountId == null || serviceAccountId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务账号标识非法");
        }

        ServiceAccount account = accountMapper.selectByIdForUpdate(serviceAccountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务账号不存在");
        }
        UserScope scope = userHolder.getUserScope();
        AccountPasswordAccessPolicy.assertAccess(scope, account.getOwnerCompanyId(), account.getAssignedUserId());
        long corsAccountId = validCorsAccountId(account);
        String requestId = requestedRequestId == null ? null : AccountPasswordRequestIds.normalize(requestedRequestId);
        if (requestId != null) {
            AccountPasswordAction existing = actionMapper.selectByRequestId(requestId);
            if (existing != null) {
                if (!AccountPasswordActionConstants.RESET.equals(existing.getActionType())
                        || !serviceAccountId.equals(existing.getServiceAccountId())) {
                    throw new BusinessException(ErrorCode.PASSWORD_RESET_IDEMPOTENCY_CONFLICT,
                            "requestId 已用于不同的密码操作");
                }
                if (existing.getOwnerCompanyId() == null
                        || !existing.getOwnerCompanyId().equals(account.getOwnerCompanyId())
                        || existing.getAssignedUserId() == null
                        || !existing.getAssignedUserId().equals(account.getAssignedUserId())) {
                    throw new BusinessException(ErrorCode.PASSWORD_RESET_IDEMPOTENCY_CONFLICT,
                            "requestId 已用于不同的账号");
                }
                if (AccountPasswordActionConstants.SUCCEEDED.equals(existing.getStatus())) {
                    return existing;
                }
                throw new BusinessException(ErrorCode.CORS_PASSWORD_RESULT_UNKNOWN,
                        "CORS 密码操作结果未知，请人工确认");
            }
        }
        if (AccountPasswordActionConstants.RESET.equals(actionType)
                && actionMapper.selectActiveResetByServiceAccountId(account.getId()) != null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS, "该账号已有未完成的密码重置");
        }

        OperatorIdentity operator = userHolder.getOperator();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        AccountPasswordAction action = new AccountPasswordAction();
        // This ID is local audit metadata only and is never sent to CORS.
        action.setRequestId(requestId == null ? "PWD_AUDIT_" + UUID.randomUUID() : requestId);
        action.setActionType(actionType);
        action.setServiceAccountId(account.getId());
        action.setOwnerCompanyId(account.getOwnerCompanyId());
        action.setAssignedUserId(account.getAssignedUserId());
        action.setCorsAccountId(String.valueOf(corsAccountId));
        action.setAccount(account.getAccount());
        action.setStatus(AccountPasswordActionConstants.PROCESSING);
        action.setActiveResetAccountId(AccountPasswordActionConstants.RESET.equals(actionType)
                ? account.getId() : null);
        action.setOperatorUserId(operator.userId());
        action.setOperatorUserName(operator.userName());
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        action.setVersion(0L);
        try {
            if (actionMapper.insert(action) != 1 || action.getId() == null) {
                throw new IllegalStateException("Password operation audit could not be reserved");
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS, "该账号已有未完成的密码重置");
        }
        return action;
    }

    @Transactional
    public void complete(AccountPasswordAction action) {
        transition(action, AccountPasswordActionConstants.SUCCEEDED, null,
                null, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS), null);
    }

    @Transactional
    public void fail(AccountPasswordAction action, String errorCode, String message) {
        transition(action, AccountPasswordActionConstants.FAILED, errorCode, message,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS), null);
    }

    @Transactional
    public void manualReview(AccountPasswordAction action, String errorCode, String message) {
        transition(action, AccountPasswordActionConstants.MANUAL_REVIEW, errorCode, message,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS),
                AccountPasswordActionConstants.RESET.equals(action.getActionType())
                        ? action.getServiceAccountId() : null);
    }

    private void transition(AccountPasswordAction action, String status, String errorCode,
                            String message, LocalDateTime now, Long activeResetAccountId) {
        if (action == null || action.getId() == null || action.getVersion() == null) {
            throw new IllegalStateException("Password operation audit identity is unavailable");
        }
        int changed = actionMapper.transitionFromProcessing(action.getId(), action.getVersion(), status,
                safeCode(errorCode), safeMessage(message), now, now, activeResetAccountId);
        if (changed != 1) {
            throw new IllegalStateException("Password operation audit state changed before completion");
        }
    }

    private static long validCorsAccountId(ServiceAccount account) {
        try {
            return CorsAccountId.parse(account.getCorsAccountId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_FAILED,
                    "服务账号缺少有效的 CORS 账号标识");
        }
    }

    private static String safeCode(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }
}

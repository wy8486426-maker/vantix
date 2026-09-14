package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.application.password.AccountPasswordRequestIds;
import com.sinognss.cloud.vantix.application.password.AccountPasswordAccessPolicy;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.springframework.dao.DuplicateKeyException;

public class AccountPasswordResetReserveService {
    private final AccountPasswordResetReserveTransaction transaction;
    private final AccountPasswordActionMapper actionMapper;
    private final CorsOperationMapper operationMapper;
    private final UserHolderBridge userHolder;

    public AccountPasswordResetReserveService(AccountPasswordResetReserveTransaction transaction,
                                              AccountPasswordActionMapper actionMapper,
                                              CorsOperationMapper operationMapper,
                                              UserHolderBridge userHolder) {
        this.transaction = transaction;
        this.actionMapper = actionMapper;
        this.operationMapper = operationMapper;
        this.userHolder = userHolder;
    }

    public AccountPasswordResetReservation reserve(String inputRequestId, Long serviceAccountId) {
        if (serviceAccountId == null || serviceAccountId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务账号标识非法");
        }
        AccountPasswordResetCommand command = new AccountPasswordResetCommand(
                AccountPasswordRequestIds.normalize(inputRequestId), serviceAccountId);
        UserScope scope = userHolder.getUserScope();
        var operator = userHolder.getOperator();
        try {
            return transaction.reserve(command, scope, operator);
        } catch (DuplicateKeyException duplicate) {
            return resolveDuplicate(command, scope);
        }
    }

    private AccountPasswordResetReservation resolveDuplicate(AccountPasswordResetCommand command, UserScope scope) {
        AccountPasswordAction existing = actionMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            return resolveExisting(command, scope, existing);
        }
        AccountPasswordAction active = actionMapper.selectActiveResetByServiceAccountId(command.serviceAccountId());
        if (active != null) {
            AccountPasswordAccessPolicy.assertAccess(scope, active.getOwnerCompanyId(), active.getAssignedUserId());
            throw new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS, "该账号已有未完成的密码重置");
        }
        throw new BusinessException(ErrorCode.PASSWORD_RESET_IDEMPOTENCY_CONFLICT,
                "requestId 已用于不同的操作");
    }

    private AccountPasswordResetReservation resolveExisting(AccountPasswordResetCommand command,
                                                            UserScope scope,
                                                            AccountPasswordAction existing) {
        if (!AccountPasswordActionConstants.RESET.equals(existing.getActionType())
                || !command.serviceAccountId().equals(existing.getServiceAccountId())) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_IDEMPOTENCY_CONFLICT,
                    "requestId 已用于不同的密码操作");
        }
        AccountPasswordAccessPolicy.assertAccess(scope, existing.getOwnerCompanyId(), existing.getAssignedUserId());
        var operation = operationMapper.selectByBusiness(AccountPasswordResetConstants.BIZ_TYPE, existing.getId());
        if (!AccountPasswordResetConstants.isOperation(operation, existing.getId(), existing.getServiceAccountId())
                || !existing.getRequestId().equals(operation.getRequestId())) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_MANUAL_REVIEW, "密码重置记录需要人工处理");
        }
        return new AccountPasswordResetReservation(existing.getRequestId(), existing.getServiceAccountId(),
                existing.getStatus());
    }
}

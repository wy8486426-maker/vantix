package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.application.password.AccountPasswordAccessPolicy;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class AccountPasswordResetReserveTransaction {
    private final AccountPasswordActionMapper actionMapper;
    private final CorsOperationMapper operationMapper;
    private final ServiceAccountMapper serviceAccountMapper;
    private final Clock clock;

    public AccountPasswordResetReserveTransaction(AccountPasswordActionMapper actionMapper,
                                                 CorsOperationMapper operationMapper,
                                                 ServiceAccountMapper serviceAccountMapper,
                                                 Clock clock) {
        this.actionMapper = actionMapper;
        this.operationMapper = operationMapper;
        this.serviceAccountMapper = serviceAccountMapper;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountPasswordResetReservation reserve(AccountPasswordResetCommand command,
                                                   UserScope scope,
                                                   OperatorIdentity operator) {
        AccountPasswordAction existing = actionMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            return resolveExisting(command, scope, existing);
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        ServiceAccount account = serviceAccountMapper.selectByIdForUpdate(command.serviceAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务账号不存在");
        }
        AccountPasswordAccessPolicy.assertAccess(scope, account.getOwnerCompanyId(), account.getAssignedUserId());
        if (!hasIdentity(account)) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_FAILED, "服务账号缺少有效的 CORS 账号标识");
        }

        // The account row serializes simultaneous reservations for different requestIds.
        // This locking current read also catches a requestId inserted while we waited.
        existing = actionMapper.selectByRequestIdForUpdate(command.requestId());
        if (existing != null) {
            return resolveExisting(command, scope, existing);
        }
        if (actionMapper.selectActiveResetByServiceAccountId(account.getId()) != null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS, "该账号已有未完成的密码重置");
        }

        AccountPasswordAction action = new AccountPasswordAction();
        action.setRequestId(command.requestId());
        action.setActionType(AccountPasswordActionConstants.RESET);
        action.setServiceAccountId(account.getId());
        action.setOwnerCompanyId(account.getOwnerCompanyId());
        action.setAssignedUserId(account.getAssignedUserId());
        action.setCorsAccountId(account.getCorsAccountId());
        action.setAccount(account.getAccount());
        action.setStatus(AccountPasswordActionConstants.PROCESSING);
        action.setActiveResetAccountId(account.getId());
        action.setOperatorUserId(operator == null ? null : operator.userId());
        action.setOperatorUserName(operator == null ? null : operator.userName());
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        action.setVersion(0L);
        if (actionMapper.insert(action) != 1 || action.getId() == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_FAILED, "密码重置审计记录创建失败");
        }

        CorsOperation operation = new CorsOperation();
        operation.setRequestId(command.requestId());
        operation.setOperationType(AccountPasswordResetConstants.OPERATION_TYPE);
        operation.setBizType(AccountPasswordResetConstants.BIZ_TYPE);
        operation.setBizId(action.getId());
        operation.setServiceAccountId(account.getId());
        operation.setStatus(AccountPasswordResetConstants.PENDING);
        operation.setRetryCount(0);
        operation.setVersion(0L);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        if (operationMapper.insert(operation) != 1 || operation.getId() == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_FAILED, "密码重置操作创建失败");
        }
        return new AccountPasswordResetReservation(action.getRequestId(), action.getServiceAccountId(),
                action.getStatus());
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
        CorsOperation operation = operationMapper.selectByBusiness(AccountPasswordResetConstants.BIZ_TYPE,
                existing.getId());
        if (!validOperation(operation, existing)) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_MANUAL_REVIEW, "密码重置记录需要人工处理");
        }
        return new AccountPasswordResetReservation(existing.getRequestId(), existing.getServiceAccountId(),
                existing.getStatus());
    }

    private static boolean validOperation(CorsOperation operation, AccountPasswordAction action) {
        return AccountPasswordResetConstants.isOperation(operation, action.getId(), action.getServiceAccountId())
                && action.getRequestId().equals(operation.getRequestId());
    }

    private static boolean hasIdentity(ServiceAccount account) {
        return account.getId() != null && account.getOwnerCompanyId() != null
                && nonblank(account.getCorsAccountId()) && nonblank(account.getAccount());
    }

    private static boolean nonblank(String value) {
        return value != null && !value.isBlank();
    }
}
